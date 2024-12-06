/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2024 ConnId. All Rights Reserved
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package net.tirasa.connid.bundles.googleapps;

import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Groups;
import com.google.api.services.directory.model.Member;
import com.google.api.services.directory.model.Members;
import com.google.api.services.directory.model.OrgUnit;
import com.google.api.services.directory.model.OrgUnits;
import com.google.api.services.directory.model.User;
import com.google.api.services.directory.model.Users;
import com.google.api.services.licensing.Licensing;
import com.google.api.services.licensing.LicensingRequest;
import com.google.api.services.licensing.model.LicenseAssignment;
import com.google.api.services.licensing.model.LicenseAssignmentList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.SortKey;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AndFilter;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;
import org.identityconnectors.framework.spi.SearchResultsHandler;

public class GoogleAppsSearch {

    private static final Log LOG = Log.getLog(GoogleAppsSearch.class);

    private static Set<String> getAttributesToGet(final ObjectClass objectClass, final OperationOptions options) {
        Set<String> attributesToGet = null;
        if (null != options.getAttributesToGet()) {
            attributesToGet = CollectionUtil.newCaseInsensitiveSet();
            if (GoogleAppsUtil.ORG_UNIT.equals(objectClass)) {
                attributesToGet.add(GoogleAppsUtil.ORG_UNIT_PATH_ATTR);
            } else {
                attributesToGet.add(GoogleAppsUtil.ID_ATTR);
            }
            attributesToGet.add(GoogleAppsUtil.ETAG_ATTR);
            for (String attribute : options.getAttributesToGet()) {
                int i = attribute.indexOf('/');
                if (i == 0) {
                    // Strip off the leading '/'
                    attribute = attribute.substring(1);
                    i = attribute.indexOf('/');
                }
                int j = attribute.indexOf('(');
                if (i < 0 && j < 0) {
                    attributesToGet.add(attribute);
                } else if (i == 0 || j == 0) {
                    throw new IllegalArgumentException("Invalid attribute name to get:/" + attribute);
                } else {
                    int l = attribute.length();
                    if (i > 0) {
                        l = Math.min(l, i);
                    }
                    if (j > 0) {
                        l = Math.min(l, j);
                    }
                    attributesToGet.add(attribute.substring(0, l));
                }
            }
        }
        return attributesToGet;
    }

    private static List<String> customSchemaNames(final String customSchemasJSON) {
        List<GoogleAppsCustomSchema> customSchemas = GoogleAppsUtil.extractCustomSchemas(customSchemasJSON);
        List<String> customSchemaNames = new ArrayList<>();
        for (GoogleAppsCustomSchema customSchema : customSchemas) {
            if (customSchema.getType().equals("object")) {
                // parse inner schemas
                String basicName = customSchema.getName();
                // manage only first level inner schemas
                for (GoogleAppsCustomSchema innerSchema : customSchema.getInnerSchemas()) {
                    customSchemaNames.add(basicName + "." + innerSchema.getName());
                }
            } else {
                LOG.warn("CustomSchema type {0} not allowed at this level", customSchema.getType());
            }
        }
        return customSchemaNames;
    }

    private static Attribute getKeyFromFilter(final ObjectClass objectClass, final Filter filter) {
        Attribute key = null;
        if (filter instanceof EqualsFilter) {
            // Account, Group, OrgUnit object classes
            Attribute filterAttr = ((EqualsFilter) filter).getAttribute();
            if (filterAttr instanceof Uid) {
                key = filterAttr;
            } else if (ObjectClass.ACCOUNT.equals(objectClass) || ObjectClass.GROUP.equals(objectClass)
                    && (filterAttr instanceof Name
                    || filterAttr.getName().equalsIgnoreCase(GoogleAppsUtil.ALIASES_ATTR))) {
                key = filterAttr;
            } else if (GoogleAppsUtil.ORG_UNIT.equals(objectClass) && filterAttr.getName().equalsIgnoreCase(
                    GoogleAppsUtil.ORG_UNIT_PATH_ATTR)) {
                key = filterAttr;
            } else if (ObjectClass.GROUP.equals(objectClass) && filterAttr.is(GoogleAppsUtil.EMAIL_ATTR)) {
                key = filterAttr;
            }
        } else if (filter instanceof AndFilter) {
            // Member object class
            if (GoogleAppsUtil.MEMBER.equals(objectClass)) {
                Attribute groupKey = null;
                Attribute memberKey = null;
                StringBuilder memberId = new StringBuilder();

                Collection<Filter> filters = ((AndFilter) filter).getFilters();
                for (Filter f : filters) {
                    if (f instanceof EqualsFilter) {
                        Attribute filterAttr = ((EqualsFilter) f).getAttribute();
                        if (filterAttr.getName().equalsIgnoreCase(GoogleAppsUtil.GROUP_KEY_ATTR)) {
                            groupKey = filterAttr;
                        } else if (filterAttr.getName().equalsIgnoreCase(GoogleAppsUtil.EMAIL_ATTR)
                                || filterAttr.getName().equalsIgnoreCase(GoogleAppsUtil.ALIAS_ATTR)
                                || filterAttr instanceof Uid) {
                            memberKey = filterAttr;
                        } else {
                            throw new UnsupportedOperationException(
                                    "Only AndFilter('groupKey','memberKey') is supported");
                        }
                    } else {
                        throw new UnsupportedOperationException(
                                "Only AndFilter('groupKey','memberKey') is supported");
                    }
                }
                if (memberKey != null && groupKey != null) {
                    memberId.append(groupKey.getValue().get(0));
                    memberId.append("/");
                    memberId.append(memberKey.getValue().get(0));
                    key = new Uid(memberId.toString());
                }
            }
        }
        return key;
    }

    private final GoogleAppsConfiguration configuration;

    private final ObjectClass objectClass;

    private final Filter query;

    private final ResultsHandler handler;

    private final OperationOptions options;

    public GoogleAppsSearch(
            final GoogleAppsConfiguration configuration,
            final ObjectClass objectClass,
            final Filter query,
            final ResultsHandler handler,
            final OperationOptions options) {

        this.configuration = configuration;
        this.objectClass = objectClass;
        this.query = query;
        this.handler = handler;
        this.options = options;
    }

    protected String getFields(final OperationOptions options, final String... nameAttribute) {
        if (null != options.getAttributesToGet()) {
            Set<String> attributes = CollectionUtil.newCaseInsensitiveSet();
            attributes.addAll(Arrays.asList(nameAttribute));
            final boolean notBlankCustomSchemas = StringUtil.isNotBlank(configuration.getCustomSchemasJSON());
            final List<String> customSchemaNames = notBlankCustomSchemas
                    ? customSchemaNames(configuration.getCustomSchemasJSON())
                    : new ArrayList<>();
            for (String attribute : options.getAttributesToGet()) {
                if (AttributeUtil.namesEqual(GoogleAppsUtil.DESCRIPTION_ATTR, attribute)) {
                    attributes.add(GoogleAppsUtil.DESCRIPTION_ATTR);
                } else if (AttributeUtil.isSpecialName(attribute)) {
                    // nothing to do
                } else if (AttributeUtil.namesEqual(GoogleAppsUtil.FAMILY_NAME_ATTR, attribute)) {
                    attributes.add("name/familyName");
                } else if (AttributeUtil.namesEqual(GoogleAppsUtil.GIVEN_NAME_ATTR, attribute)) {
                    attributes.add("name/givenName");
                } else if (AttributeUtil.namesEqual(GoogleAppsUtil.FULL_NAME_ATTR, attribute)) {
                    attributes.add("name/fullName");
                } else if (!customSchemaNames.contains(attribute)) {
                    attributes.add(attribute);
                }
                // return also customSchemas according to configuration
                if ("full".equals(configuration.getProjection()) && notBlankCustomSchemas) {
                    attributes.add(GoogleAppsUtil.CUSTOM_SCHEMAS);
                }
            }
            return StringUtil.join(attributes, GoogleAppsUtil.COMMA);
        }
        return null;
    }

    public void execute() {
        final Set<String> attributesToGet = getAttributesToGet(objectClass, options);
        Attribute key = getKeyFromFilter(objectClass, query);

        if (ObjectClass.ACCOUNT.equals(objectClass)) {
            if (null == key || null == key.getValue() || key.getValue().isEmpty() || null == key.getValue().get(0)) {
                // Search request
                try {
                    Directory.Users.List request = configuration.getDirectory().users().list();
                    if (null != query) {
                        StringBuilder queryBuilder = query.accept(new UserHandler(), request);
                        if (null != queryBuilder) {
                            String queryString = queryBuilder.toString();
                            LOG.ok("Executing Query: {0}", queryString);
                            request.setQuery(queryString);
                        }
                        if (null == request.getDomain() && null == request.getCustomer()) {
                            request.setCustomer(GoogleAppsUtil.MY_CUSTOMER_ID);
                        }
                    } else {
                        request.setCustomer(GoogleAppsUtil.MY_CUSTOMER_ID);
                    }

                    // Implementation to support the 'OP_PAGE_SIZE'
                    boolean paged = false;
                    if (options.getPageSize() != null && 0 < options.getPageSize()) {
                        if (options.getPageSize() >= 1 && options.getPageSize() <= 500) {
                            request.setMaxResults(options.getPageSize());
                            paged = true;
                        } else {
                            throw new IllegalArgumentException(
                                    "Invalid pageSize value. Default is 100. Max allowed is 500 (integer, 1-500)");
                        }
                    }
                    // Implementation to support the 'OP_PAGED_RESULTS_COOKIE'
                    request.setPageToken(options.getPagedResultsCookie());
                    request.setProjection(configuration.getProjection());

                    // Implementation to support the 'OP_ATTRIBUTES_TO_GET'
                    String fields = getFields(options, GoogleAppsUtil.ID_ATTR,
                            GoogleAppsUtil.ETAG_ATTR, GoogleAppsUtil.PRIMARY_EMAIL_ATTR);
                    if (null != fields) {
                        request.setFields("nextPageToken,users(" + fields + ")");
                    }

                    if (options.getOptions().get(GoogleAppsUtil.SHOW_DELETED_PARAM) instanceof Boolean) {
                        request.setShowDeleted(options.getOptions().get(GoogleAppsUtil.SHOW_DELETED_PARAM).toString());
                    }

                    // Implementation to support the 'OP_SORT_KEYS'
                    if (null != options.getSortKeys()) {
                        for (SortKey sortKey : options.getSortKeys()) {
                            String orderBy;
                            if (sortKey.getField().equalsIgnoreCase(GoogleAppsUtil.EMAIL_ATTR)
                                    || sortKey.getField().equalsIgnoreCase(GoogleAppsUtil.PRIMARY_EMAIL_ATTR)
                                    || sortKey.getField().equalsIgnoreCase(GoogleAppsUtil.ALIASES_ATTR)
                                    || sortKey.getField().equalsIgnoreCase(GoogleAppsUtil.ALIAS_ATTR)) {
                                orderBy = GoogleAppsUtil.EMAIL_ATTR;
                            } else if (sortKey.getField().equalsIgnoreCase(GoogleAppsUtil.GIVEN_NAME_ATTR)) {
                                orderBy = GoogleAppsUtil.GIVEN_NAME_ATTR;
                            } else if (sortKey.getField().equalsIgnoreCase(GoogleAppsUtil.FAMILY_NAME_ATTR)) {
                                orderBy = GoogleAppsUtil.FAMILY_NAME_ATTR;
                            } else {
                                LOG.ok("Unsupported SortKey:{0}", sortKey);
                                continue;
                            }

                            request.setOrderBy(orderBy);
                            if (sortKey.isAscendingOrder()) {
                                request.setSortOrder(GoogleAppsUtil.ASCENDING_ORDER);
                            } else {
                                request.setSortOrder(GoogleAppsUtil.DESCENDING_ORDER);
                            }
                            break;
                        }
                    }

                    String nextPageToken = null;
                    do {
                        nextPageToken = GoogleApiExecutor.execute(request,
                                new RequestResultHandler<Directory.Users.List, Users, String>() {

                            @Override
                            public String handleResult(final Directory.Users.List request, final Users value) {
                                if (null != value.getUsers()) {
                                    for (User user : value.getUsers()) {
                                        handler.handle(UserHandler.fromUser(
                                                configuration,
                                                user,
                                                attributesToGet,
                                                configuration.getDirectory().groups()));
                                    }
                                }
                                return value.getNextPageToken();
                            }
                        });
                        request.setPageToken(nextPageToken);
                    } while (!paged && StringUtil.isNotBlank(nextPageToken));

                    if (paged && StringUtil.isNotBlank(nextPageToken)) {
                        LOG.info("Paged Search was requested and next token is:{0}", nextPageToken);
                        ((SearchResultsHandler) handler).handleResult(new SearchResult(nextPageToken, 0));
                    }
                } catch (IOException e) {
                    LOG.warn(e, "Failed to initialize Groups#List");
                    throw ConnectorException.wrap(e);
                }

            } else {
                // Read request
                try {
                    Directory.Users.Get request =
                            configuration.getDirectory().users().get((String) key.getValue().get(0));
                    request.setFields(getFields(options, GoogleAppsUtil.ID_ATTR,
                            GoogleAppsUtil.ETAG_ATTR, GoogleAppsUtil.PRIMARY_EMAIL_ATTR));
                    request.setProjection(configuration.getProjection());

                    GoogleApiExecutor.execute(request,
                            new RequestResultHandler<Directory.Users.Get, User, Boolean>() {

                        @Override
                        public Boolean handleResult(final Directory.Users.Get request, final User user) {
                            return handler.handle(UserHandler.fromUser(
                                    configuration, user, attributesToGet, configuration.getDirectory().groups()));
                        }

                        @Override
                        public Boolean handleNotFound(final IOException e) {
                            // Do nothing if not found
                            return true;
                        }
                    });
                } catch (IOException e) {
                    LOG.warn(e, "Failed to initialize Users#Get");
                    throw ConnectorException.wrap(e);
                }
            }
        } else if (ObjectClass.GROUP.equals(objectClass)) {
            if (null == key) {
                // Search request
                try {
                    // userKey excludes the customer and domain!!
                    Directory.Groups.List request = configuration.getDirectory().groups().list();
                    if (null != query) {
                        StringBuilder queryBuilder = query.accept(new GroupHandler(), request);
                        if (null != queryBuilder) {
                            String queryString = queryBuilder.toString();
                            LOG.ok("Executing Query: {0}", queryString);
                            request.setQuery(queryString);
                        }
                        if (null == request.getDomain() && null == request.getCustomer()) {
                            request.setCustomer(GoogleAppsUtil.MY_CUSTOMER_ID);
                        }
                    } else {
                        request.setCustomer(GoogleAppsUtil.MY_CUSTOMER_ID);
                    }

                    boolean paged = false;
                    // Groups
                    if (options.getPageSize() != null && 0 < options.getPageSize()) {
                        request.setMaxResults(options.getPageSize());
                        paged = true;
                    }
                    request.setPageToken(options.getPagedResultsCookie());

                    // Implementation to support the 'OP_ATTRIBUTES_TO_GET'
                    String fields = getFields(options, GoogleAppsUtil.ID_ATTR,
                            GoogleAppsUtil.ETAG_ATTR, GoogleAppsUtil.EMAIL_ATTR);
                    if (null != fields) {
                        request.setFields("nextPageToken,groups(" + fields + ")");
                    }

                    String nextPageToken = null;
                    do {
                        nextPageToken = GoogleApiExecutor.execute(request,
                                new RequestResultHandler<Directory.Groups.List, Groups, String>() {

                            @Override
                            public String handleResult(final Directory.Groups.List request, final Groups value) {
                                if (null != value.getGroups()) {
                                    for (Group group : value.getGroups()) {
                                        handler.handle(GroupHandler.fromGroup(
                                                group, attributesToGet, configuration.getDirectory().members()));
                                    }
                                }
                                return value.getNextPageToken();
                            }
                        });
                        request.setPageToken(nextPageToken);
                    } while (!paged && StringUtil.isNotBlank(nextPageToken));

                    if (paged && StringUtil.isNotBlank(nextPageToken)) {
                        LOG.info("Paged Search was requested");
                        ((SearchResultsHandler) handler).handleResult(new SearchResult(nextPageToken, 0));
                    }
                } catch (IOException e) {
                    LOG.warn(e, "Failed to initialize Groups#List");
                    throw ConnectorException.wrap(e);
                }
            } else {
                // Read request
                try {
                    Directory.Groups.Get request =
                            configuration.getDirectory().groups().get((String) key.getValue().get(0));
                    request.setFields(getFields(options, GoogleAppsUtil.ID_ATTR,
                            GoogleAppsUtil.ETAG_ATTR, GoogleAppsUtil.EMAIL_ATTR));

                    GoogleApiExecutor.execute(request,
                            new RequestResultHandler<Directory.Groups.Get, Group, Boolean>() {

                        @Override
                        public Boolean handleResult(final Directory.Groups.Get request, final Group value) {
                            return handler.handle(GroupHandler.fromGroup(
                                    value, attributesToGet, configuration.getDirectory().members()));
                        }

                        @Override
                        public Boolean handleNotFound(final IOException e) {
                            // Do nothing if not found
                            return true;
                        }
                    });
                } catch (IOException e) {
                    LOG.warn(e, "Failed to initialize Groups#Get");
                    throw ConnectorException.wrap(e);
                }
            }
        } else if (GoogleAppsUtil.MEMBER.equals(objectClass)) {
            if (null == key) {
                // Search request
                // TODO support AND role
                try {
                    String groupKey = null;

                    if (query instanceof EqualsFilter
                            && ((EqualsFilter) query).getAttribute().is(GoogleAppsUtil.GROUP_KEY_ATTR)) {

                        groupKey = AttributeUtil.getStringValue(((AttributeFilter) query).getAttribute());
                    } else {
                        throw new UnsupportedOperationException("Only EqualsFilter('groupKey') is supported");
                    }

                    if (StringUtil.isBlank(groupKey)) {
                        throw new InvalidAttributeValueException("The 'groupKey' can not be blank.");
                    }
                    Directory.Members.List request = configuration.getDirectory().members().list(groupKey);

                    boolean paged = false;
                    // Groups
                    if (options.getPageSize() != null && 0 < options.getPageSize()) {
                        request.setMaxResults(options.getPageSize());
                        paged = true;
                    }
                    request.setPageToken(options.getPagedResultsCookie());

                    String nextPageToken = null;
                    do {
                        nextPageToken = GoogleApiExecutor.execute(request,
                                new RequestResultHandler<Directory.Members.List, Members, String>() {

                            @Override
                            public String handleResult(
                                    final Directory.Members.List request,
                                    final Members value) {
                                if (null != value.getMembers()) {
                                    for (Member group : value.getMembers()) {
                                        handler.handle(MembersHandler.from(request.getGroupKey(), group));
                                    }
                                }
                                return value.getNextPageToken();
                            }
                        });
                        request.setPageToken(nextPageToken);
                    } while (!paged && StringUtil.isNotBlank(nextPageToken));

                    if (paged && StringUtil.isNotBlank(nextPageToken)) {
                        LOG.info("Paged Search was requested");
                        ((SearchResultsHandler) handler).handleResult(new SearchResult(nextPageToken, 0));
                    }
                } catch (IOException e) {
                    LOG.warn(e, "Failed to initialize Groups#List");
                    throw ConnectorException.wrap(e);
                }
            } else {
                // Read request
                try {
                    String[] ids = ((Uid) key).getUidValue().split("/");
                    if (ids.length != 2) {
                        // TODO fix the exception
                        throw new InvalidAttributeValueException("Unrecognised UID format");
                    }

                    Directory.Members.Get request = configuration.getDirectory().members().get(ids[0], ids[1]);
                    GoogleApiExecutor.execute(request,
                            new RequestResultHandler<Directory.Members.Get, Member, Boolean>() {

                        @Override
                        public Boolean handleResult(final Directory.Members.Get request, final Member value) {
                            return handler.handle(MembersHandler.from(request.getGroupKey(), value));
                        }

                        @Override
                        public Boolean handleNotFound(final IOException e) {
                            // Do nothing if not found
                            return true;
                        }
                    });
                } catch (IOException e) {
                    LOG.warn(e, "Failed to initialize Groups#Get");
                    throw ConnectorException.wrap(e);
                }
            }
        } else if (GoogleAppsUtil.ORG_UNIT.equals(objectClass)) {
            if (null == key) {
                // Search request
                try {
                    Directory.Orgunits.List request = configuration.getDirectory().orgunits().
                            list(GoogleAppsUtil.MY_CUSTOMER_ID);
                    if (null != query) {
                        if (query instanceof StartsWithFilter
                                && AttributeUtil.namesEqual(GoogleAppsUtil.ORG_UNIT_PATH_ATTR,
                                        ((StartsWithFilter) query).getName())) {

                            request.setOrgUnitPath(((StartsWithFilter) query).getValue());
                        } else {
                            throw new UnsupportedOperationException(
                                    "Only StartsWithFilter('orgUnitPath') is supported");
                        }
                    } else {
                        request.setOrgUnitPath("/");
                    }

                    String scope = options.getScope();
                    if (OperationOptions.SCOPE_OBJECT.equalsIgnoreCase(scope)
                            || OperationOptions.SCOPE_ONE_LEVEL.equalsIgnoreCase(scope)) {

                        request.setType("children");
                    } else {
                        request.setType("all");
                    }

                    // Implementation to support the 'OP_ATTRIBUTES_TO_GET'
                    String fields = getFields(
                            options,
                            GoogleAppsUtil.ORG_UNIT_PATH_ATTR,
                            GoogleAppsUtil.ETAG_ATTR,
                            GoogleAppsUtil.NAME_ATTR);
                    if (null != fields) {
                        request.setFields("organizationUnits(" + fields + ")");
                    }

                    GoogleApiExecutor.execute(request,
                            new RequestResultHandler<Directory.Orgunits.List, OrgUnits, Void>() {

                        @Override
                        public Void handleResult(final Directory.Orgunits.List request,
                                final OrgUnits value) {
                            if (null != value.getOrganizationUnits()) {
                                for (OrgUnit group : value.getOrganizationUnits()) {
                                    handler.handle(OrgunitsHandler.from(group, attributesToGet));
                                }
                            }
                            return null;
                        }
                    });
                } catch (IOException e) {
                    LOG.warn(e, "Failed to initialize OrgUnits#List");
                    throw ConnectorException.wrap(e);
                }
            } else {
                // Read request
                try {
                    Directory.Orgunits.Get request = configuration.getDirectory().orgunits().
                            get(GoogleAppsUtil.MY_CUSTOMER_ID, (String) key.getValue().get(0));
                    request.setFields(getFields(options, GoogleAppsUtil.ORG_UNIT_PATH_ATTR,
                            GoogleAppsUtil.ETAG_ATTR, GoogleAppsUtil.NAME_ATTR));

                    GoogleApiExecutor.execute(request,
                            new RequestResultHandler<Directory.Orgunits.Get, OrgUnit, Boolean>() {

                        @Override
                        public Boolean handleResult(final Directory.Orgunits.Get request, final OrgUnit value) {
                            return handler.handle(OrgunitsHandler.from(value, attributesToGet));
                        }

                        @Override
                        public Boolean handleNotFound(final IOException e) {
                            // Do nothing if not found
                            return true;
                        }
                    });
                } catch (IOException e) {
                    LOG.warn(e, "Failed to initialize OrgUnits#Get");
                    throw ConnectorException.wrap(e);
                }
            }
        } else if (GoogleAppsUtil.LICENSE_ASSIGNMENT.equals(objectClass)) {
            if (null == key) {
                // Search request
                try {
                    String productId = "";
                    String skuId = "";

                    boolean paged = false;

                    LicensingRequest<LicenseAssignmentList> request = null;

                    if (StringUtil.isBlank(productId)) {
                        // TODO iterate over the three productids
                        throw new ConnectorException("productId is required");
                    } else if (StringUtil.isBlank(skuId)) {
                        Licensing.LicenseAssignments.ListForProduct r =
                                configuration.getLicensing().licenseAssignments().
                                        listForProduct(productId, GoogleAppsUtil.MY_CUSTOMER_ID);

                        if (options.getPageSize() != null && 0 < options.getPageSize()) {
                            r.setMaxResults(Long.valueOf(options.getPageSize()));
                            paged = true;
                        }
                        r.setPageToken(options.getPagedResultsCookie());
                        request = r;
                    } else {
                        Licensing.LicenseAssignments.ListForProductAndSku r =
                                configuration.getLicensing().licenseAssignments().
                                        listForProductAndSku(productId, skuId, GoogleAppsUtil.MY_CUSTOMER_ID);

                        if (options.getPageSize() != null && 0 < options.getPageSize()) {
                            r.setMaxResults(Long.valueOf(options.getPageSize()));
                            paged = true;
                        }
                        r.setPageToken(options.getPagedResultsCookie());
                        request = r;
                    }

                    String nextPageToken = null;
                    do {
                        nextPageToken = GoogleApiExecutor.execute(request,
                                new RequestResultHandler<
                                        LicensingRequest<LicenseAssignmentList>, LicenseAssignmentList, String>() {

                            @Override
                            public String handleResult(
                                    final LicensingRequest<LicenseAssignmentList> request,
                                    final LicenseAssignmentList value) {

                                if (null != value.getItems()) {
                                    for (LicenseAssignment resource : value.getItems()) {
                                        handler.handle(LicenseAssignmentsHandler.from(resource));
                                    }
                                }
                                return value.getNextPageToken();
                            }
                        });
                        if (request instanceof Licensing.LicenseAssignments.ListForProduct) {
                            ((Licensing.LicenseAssignments.ListForProduct) request).setPageToken(nextPageToken);
                        } else {
                            ((Licensing.LicenseAssignments.ListForProductAndSku) request).setPageToken(nextPageToken);
                        }
                    } while (!paged && StringUtil.isNotBlank(nextPageToken));

                    if (paged && StringUtil.isNotBlank(nextPageToken)) {
                        LOG.info("Paged Search was requested");
                        ((SearchResultsHandler) handler).handleResult(new SearchResult(nextPageToken, 0));
                    }

                } catch (IOException e) {
                    LOG.warn(e, "Failed to initialize Groups#List");
                    throw ConnectorException.wrap(e);
                }
            } else {
                // Read request
                try {
                    Matcher name = LicenseAssignmentsHandler.LICENSE_NAME_PATTERN.matcher(((Uid) key).getUidValue());
                    if (!name.matches()) {
                        return;
                    }

                    String productId = name.group(0);
                    String skuId = name.group(1);
                    String userId = name.group(2);

                    Licensing.LicenseAssignments.Get request =
                            configuration.getLicensing().licenseAssignments().get(productId, skuId, userId);

                    GoogleApiExecutor.execute(request,
                            new RequestResultHandler<Licensing.LicenseAssignments.Get, LicenseAssignment, Boolean>() {

                        @Override
                        public Boolean handleResult(
                                final Licensing.LicenseAssignments.Get request,
                                final LicenseAssignment value) {

                            return handler.handle(LicenseAssignmentsHandler.from(value));
                        }

                        @Override
                        public Boolean handleNotFound(final IOException e) {
                            // Do nothing if not found
                            return true;
                        }
                    });
                } catch (IOException e) {
                    LOG.warn(e, "Failed to initialize Groups#Get");
                    throw ConnectorException.wrap(e);
                }
            }
        } else {
            LOG.warn("Search of type {0} is not supported", configuration.getConnectorMessages()
                    .format(objectClass.getDisplayNameKey(), objectClass.getObjectClassValue()));
            throw new UnsupportedOperationException("Search of type"
                    + objectClass.getObjectClassValue() + " is not supported");
        }
    }
}
