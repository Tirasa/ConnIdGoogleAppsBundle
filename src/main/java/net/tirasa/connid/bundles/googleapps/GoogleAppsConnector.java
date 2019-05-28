/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 ForgeRock AS. All Rights Reserved
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
 * Portions Copyrighted 2016 ConnId.
 */
package net.tirasa.connid.bundles.googleapps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.RetryableException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.OperationOptionInfo;
import org.identityconnectors.framework.common.objects.OperationOptionInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.SearchResult;
import org.identityconnectors.framework.common.objects.SortKey;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AttributeFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.AndFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.Connector;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.SearchResultsHandler;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.TestOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.client.http.HttpStatusCodes;
import com.google.api.services.admin.directory.Directory;
import com.google.api.services.admin.directory.model.Alias;
import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.admin.directory.model.Groups;
import com.google.api.services.admin.directory.model.Member;
import com.google.api.services.admin.directory.model.Members;
import com.google.api.services.admin.directory.model.OrgUnit;
import com.google.api.services.admin.directory.model.OrgUnits;
import com.google.api.services.admin.directory.model.User;
import com.google.api.services.admin.directory.model.UserMakeAdmin;
import com.google.api.services.admin.directory.model.UserPhoto;
import com.google.api.services.admin.directory.model.Users;
import com.google.api.services.licensing.Licensing;
import com.google.api.services.licensing.LicensingRequest;
import com.google.api.services.licensing.model.LicenseAssignment;
import com.google.api.services.licensing.model.LicenseAssignmentList;

/**
 * Main implementation of the GoogleApps Connector.
 */
@ConnectorClass(displayNameKey = "GoogleApps.connector.display",
        configurationClass = GoogleAppsConfiguration.class)
public class GoogleAppsConnector implements Connector, CreateOp, DeleteOp, SchemaOp,
        SearchOp<Filter>, TestOp, UpdateOp {

    /**
     * Setup logging for the {@link GoogleAppsConnector}.
     */
    private static final Log LOG = Log.getLog(GoogleAppsConnector.class);

    public static final ObjectClass ORG_UNIT = new ObjectClass("OrgUnit");

    public static final ObjectClass MEMBER = new ObjectClass("Member");

    public static final ObjectClass ALIAS = new ObjectClass("Alias");

    public static final ObjectClass LICENSE_ASSIGNMENT = new ObjectClass("LicenseAssignment");

    public static final String ID_ETAG = "id,etag";

    public static final String EMAIL_ETAG = "email,etag";

    public static final String ID_ATTR = "id";

    public static final String ETAG_ATTR = "etag";

    public static final String NAME_ATTR = "name";

    public static final String ADMIN_CREATED_ATTR = "adminCreated";

    public static final String NON_EDITABLE_ALIASES_ATTR = "nonEditableAliases";

    public static final String DIRECT_MEMBERS_COUNT_ATTR = "directMembersCount";

    public static final String MY_CUSTOMER_ID = "my_customer";

    public static final String CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR = "changePasswordAtNextLogin";

    public static final String IP_WHITELISTED_ATTR = "ipWhitelisted";

    public static final String ORG_UNIT_PATH_ATTR = "orgUnitPath";

    public static final String INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR = "includeInGlobalAddressList";

    public static final String IMS_ATTR = "ims";

    public static final String EMAILS_ATTR = "emails";

    public static final String EXTERNAL_IDS_ATTR = "externalIds";

    public static final String RELATIONS_ATTR = "relations";

    public static final String ADDRESSES_ATTR = "addresses";

    public static final String ORGANIZATIONS_ATTR = "organizations";

    public static final String PHONES_ATTR = "phones";

    public static final String GIVEN_NAME_ATTR = "givenName";

    public static final String FAMILY_NAME_ATTR = "familyName";

    public static final String FULL_NAME_ATTR = "fullName";

    public static final String IS_ADMIN_ATTR = "isAdmin";

    public static final String IS_DELEGATED_ADMIN_ATTR = "isDelegatedAdmin";

    public static final String LAST_LOGIN_TIME_ATTR = "lastLoginTime";

    public static final String CREATION_TIME_ATTR = "creationTime";

    public static final String AGREED_TO_TERMS_ATTR = "agreedToTerms";

    public static final String SUSPENSION_REASON_ATTR = "suspensionReason";

    public static final String ALIASES_ATTR = "aliases";

    public static final String CUSTOMER_ID_ATTR = "customerId";

    public static final String IS_MAILBOX_SETUP_ATTR = "isMailboxSetup";

    public static final String THUMBNAIL_PHOTO_URL_ATTR = "thumbnailPhotoUrl";

    public static final String DELETION_TIME_ATTR = "deletionTime";

    public static final String DESCRIPTION_ATTR = "description";

    public static final String PRIMARY_EMAIL_ATTR = "primaryEmail";

    public static final char COMMA = ',';

    public static final String SHOW_DELETED_PARAM = "showDeleted";

    public static final String ASCENDING_ORDER = "ASCENDING";

    public static final String DESCENDING_ORDER = "DESCENDING";

    public static final String EMAIL_ATTR = "email";

    public static final String ALIAS_ATTR = "alias";

    public static final String PARENT_ORG_UNIT_PATH_ATTR = "parentOrgUnitPath";

    public static final String BLOCK_INHERITANCE_ATTR = "blockInheritance";

    public static final String EMPTY_STRING = "";

    public static final String ORG_UNIT_PATH_ETAG = "orgUnitPath,etag";

    public static final String PRODUCT_ID_ATTR = "productId";

    public static final String SKU_ID_ATTR = "skuId";

    public static final String USER_ID_ATTR = "userId";

    public static final String SELF_LINK_ATTR = "selfLink";

    public static final String ROLE_ATTR = "role";

    public static final String MEMBERS_ATTR = "__MEMBERS__";

    public static final String GROUP_KEY_ATTR = "groupKey";

    public static final String TYPE_ATTR = "type";

    public static final String CUSTOM_SCHEMAS = "customSchemas";

    public static final String PRODUCT_ID_SKU_ID_USER_ID = "productId,skuId,userId";

    public static final String PHOTO_ATTR = "__PHOTO__";

    /**
     * Place holder for the {@link Configuration} passed into the init() method
     * {@link GoogleAppsConnector#init(org.identityconnectors.framework.spi.Configuration)}
     * .
     */
    private GoogleAppsConfiguration configuration;

    private Schema schema = null;

    /**
     * Gets the Configuration context for this connector.
     *
     * @return The current {@link Configuration}
     */
    @Override
    public Configuration getConfiguration() {
        return this.configuration;
    }

    /**
     * Callback method to receive the {@link Configuration}.
     *
     * @param configuration
     * the new {@link Configuration}
     * @see org.identityconnectors.framework.spi.Connector#init(org.identityconnectors.framework.spi.Configuration)
     */
    @Override
    public void init(final Configuration configuration) {
        this.configuration = (GoogleAppsConfiguration) configuration;
    }

    /**
     * Disposes of the {@link GoogleAppsConnector}'s resources.
     *
     * @see org.identityconnectors.framework.spi.Connector#dispose()
     */
    @Override
    public void dispose() {
        configuration = null;
    }

    /** ****************
     * SPI Operations
     *
     * Implement the following operations using the contract and description
     * found in the Javadoc for these methods.
     ***************** */
    /**
     * {@inheritDoc}
     */
    @Override
    public Uid create(
            final ObjectClass objectClass,
            final Set<Attribute> createAttributes,
            final OperationOptions options) {

        final AttributesAccessor accessor = new AttributesAccessor(createAttributes);

        if (ObjectClass.ACCOUNT.equals(objectClass)) {
            Uid uid = execute(UserHandler.createUser(configuration.getDirectory().users(), accessor, configuration.
                    getCustomSchemasJSON()),
                    new RequestResultHandler<Directory.Users.Insert, User, Uid>() {

                @Override
                public Uid handleResult(final Directory.Users.Insert request,
                        final User value) {
                    LOG.ok("New User is created: {0}", value.getId());
                    return new Uid(value.getId(), value.getEtag());
                }
            });

            List<Object> aliases = accessor.findList(ALIASES_ATTR);
            if (null != aliases) {
                final Directory.Users.Aliases aliasesService = configuration.getDirectory().users().aliases();
                for (Object member : aliases) {
                    if (member instanceof String) {
                        String id = execute(UserHandler.createUserAlias(aliasesService, uid.getUidValue(),
                                (String) member),
                                new RequestResultHandler<Directory.Users.Aliases.Insert, Alias, String>() {

                            @Override
                            public String handleResult(
                                    final Directory.Users.Aliases.Insert request, final Alias value) {

                                return value == null ? null : value.getId();
                            }
                        });

                        if (null == id) {
                            // TODO make warn about failed update
                        }
                    } else if (null != member) {
                        // Delete user and Error or
                        RetryableException e =
                                RetryableException.wrap("Invalid attribute value: " + String.valueOf(member), uid);
                        e.initCause(new InvalidAttributeValueException("Attribute 'aliases' must be a String list"));
                        throw e;
                    }
                }
            }

            Attribute photo = accessor.find(PHOTO_ATTR);
            if (null != photo) {
                Object photoObject = AttributeUtil.getSingleValue(photo);
                if (photoObject instanceof byte[]) {
                    String id = execute(UserHandler.createUpdateUserPhoto(
                            configuration.getDirectory().users().photos(), uid.getUidValue(), (byte[]) photoObject),
                            new RequestResultHandler<Directory.Users.Photos.Update, UserPhoto, String>() {

                        @Override
                        public String handleResult(final Directory.Users.Photos.Update request, final UserPhoto value) {
                            return value == null ? null : value.getId();
                        }
                    });

                    if (null == id) {
                        // TODO make warn about failed update
                    }

                } else if (null != photoObject) {
                    // Delete group and Error or
                    RetryableException e = RetryableException.wrap("Invalid attribute value: "
                            + String.valueOf(photoObject), uid);
                    e.initCause(new InvalidAttributeValueException(
                            "Attribute 'photo' must be a single Map value"));
                    throw e;
                }
            }

            Attribute isAdmin = accessor.find(IS_ADMIN_ATTR);
            if (null != isAdmin) {
                try {
                    Boolean isAdminValue = AttributeUtil.getBooleanValue(isAdmin);
                    if (null != isAdminValue && isAdminValue) {
                        UserMakeAdmin content = new UserMakeAdmin();
                        content.setStatus(isAdminValue);

                        execute(configuration.getDirectory().users().makeAdmin(uid.getUidValue(), content),
                                new RequestResultHandler<Directory.Users.MakeAdmin, Void, Void>() {

                            @Override
                            public Void handleResult(final Directory.Users.MakeAdmin request, final Void value) {
                                return null;
                            }
                        });
                    }
                } catch (final Exception e) {
                    // TODO Delete user and throw Exception
                    throw ConnectorException.wrap(e);
                }
            }

            return uid;
        } else if (ObjectClass.GROUP.equals(objectClass)) {
            // @formatter:off
            /* AlreadyExistsException
             * {
             * "code" : 409,
             * "errors" : [ {
             * "domain" : "global",
             * "message" : "Entity already exists.",
             * "reason" : "duplicate"
             * } ],
             * "message" : "Entity already exists."
             * }
             */
            // @formatter:on
            Uid uid = execute(GroupHandler.createGroup(configuration.getDirectory().groups(), accessor),
                    new RequestResultHandler<Directory.Groups.Insert, Group, Uid>() {

                @Override
                public Uid handleResult(final Directory.Groups.Insert request,
                        final Group value) {
                    LOG.ok("New Group is created:{0}", value.getId());
                    return new Uid(value.getId(), value.getEtag());
                }
            });
            List<Object> members = accessor.findList(MEMBERS_ATTR);
            if (null != members) {
                final Directory.Members membersService = configuration.getDirectory().members();
                for (Object member : members) {
                    if (member instanceof Map) {
                        String email = (String) ((Map) member).get(EMAIL_ATTR);
                        String role = (String) ((Map) member).get(ROLE_ATTR);

                        String id = execute(GroupHandler.createMember(membersService, uid.getUidValue(), email, role),
                                new RequestResultHandler<Directory.Members.Insert, Member, String>() {

                            @Override
                            public String handleResult(final Directory.Members.Insert request, final Member value) {

                                return value == null ? null : value.getId();
                            }
                        });

                        if (null == id) {
                            // TODO make warn about failed update
                        }
                    } else if (null != member) {
                        // Delete group and Error or
                        RetryableException e =
                                RetryableException.wrap("Invalid attribute value: " + String.valueOf(member), uid);
                        e.initCause(new InvalidAttributeValueException("Attribute 'members' must be a Map list"));
                        throw e;
                    }
                }
            }

            return uid;
        } else if (MEMBER.equals(objectClass)) {
            return execute(GroupHandler.createMember(configuration.getDirectory().members(), accessor),
                    new RequestResultHandler<Directory.Members.Insert, Member, Uid>() {

                @Override
                public Uid handleResult(final Directory.Members.Insert request,
                        final Member value) {
                    LOG.ok("New Member is created:{0}/{1}", request.getGroupKey(), value.getEmail());
                    return GroupHandler.generateMemberId(request.getGroupKey(), value);
                }
            });
        } else if (ORG_UNIT.equals(objectClass)) {
            return execute(OrgunitsHandler.createOrgunit(configuration.getDirectory().orgunits(), accessor),
                    new RequestResultHandler<Directory.Orgunits.Insert, OrgUnit, Uid>() {

                @Override
                public Uid handleResult(final Directory.Orgunits.Insert request, final OrgUnit value) {
                    LOG.ok("New OrgUnit is created:{0}", value.getName());
                    return OrgunitsHandler.generateOrgUnitId(value);
                }
            });
        } else if (LICENSE_ASSIGNMENT.equals(objectClass)) {
            // @formatter:off
            /* AlreadyExistsException
             * {
             * "code" : 400,
             * "errors" : [ {
             * "domain" : "global",
             * "message" : "Invalid Ou Id",
             * "reason" : "invalid"
             * } ],
             * "message" : "Invalid Ou Id"
             * }
             */
            // @formatter:on

            return execute(
                    LicenseAssignmentsHandler.createLicenseAssignment(
                            configuration.getLicensing().licenseAssignments(), accessor),
                    new RequestResultHandler<Licensing.LicenseAssignments.Insert, LicenseAssignment, Uid>() {

                @Override
                public Uid handleResult(
                        final Licensing.LicenseAssignments.Insert request,
                        final LicenseAssignment value) {

                    LOG.ok("LicenseAssignment is Created:{0}/{1}/{2}",
                            value.getProductId(), value.getSkuId(), value.getUserId());
                    return LicenseAssignmentsHandler.generateLicenseAssignmentId(value);
                }
            });
        } else {
            LOG.warn("Create of type {0} is not supported", configuration.getConnectorMessages()
                    .format(objectClass.getDisplayNameKey(), objectClass.getObjectClassValue()));
            throw new UnsupportedOperationException("Create of type"
                    + objectClass.getObjectClassValue() + " is not supported");
        }

    }

    @Override
    public void delete(final ObjectClass objectClass, final Uid uid, final OperationOptions options) {
        AbstractGoogleJsonClientRequest<Void> request = null;

        try {
            if (ObjectClass.ACCOUNT.equals(objectClass)) {
                request = configuration.getDirectory().users().delete(uid.getUidValue());
            } else if (ObjectClass.GROUP.equals(objectClass)) {
                request = configuration.getDirectory().groups().delete(uid.getUidValue());
            } else if (MEMBER.equals(objectClass)) {
                // @formatter:off
                /* Already deleted
                 * {
                 * "code" : 400,
                 * "errors" : [ {
                 * "domain" : "global",
                 * "message" : "Missing required field: memberKey",
                 * "reason" : "required"
                 * } ],
                 * "message" : "Missing required field: memberKey"
                 * }
                 */
                // @formatter:on
                String[] ids = uid.getUidValue().split("/");
                if (ids.length == 2) {
                    request = configuration.getDirectory().members().delete(ids[0], ids[1]);
                } else {
                    throw new UnknownUidException("Invalid ID format");
                }
            } else if (ORG_UNIT.equals(objectClass)) {
                request = configuration.getDirectory().orgunits().
                        delete(MY_CUSTOMER_ID, CollectionUtil.newList(uid.getUidValue()));
            } else if (LICENSE_ASSIGNMENT.equals(objectClass)) {
                request = LicenseAssignmentsHandler.deleteLicenseAssignment(
                        configuration.getLicensing().licenseAssignments(), uid.getUidValue());
            }
        } catch (IOException e) {
            throw ConnectorException.wrap(e);
        }

        if (null == request) {
            LOG.warn("Delete of type {0} is not supported", configuration.getConnectorMessages()
                    .format(objectClass.getDisplayNameKey(), objectClass.getObjectClassValue()));
            throw new UnsupportedOperationException("Delete of type"
                    + objectClass.getObjectClassValue() + " is not supported");
        }

        execute(request, new RequestResultHandler<AbstractGoogleJsonClientRequest<Void>, Void, Void>() {

            @Override
            public Void handleResult(final AbstractGoogleJsonClientRequest<Void> request, final Void value) {
                return null;
            }

            @Override
            public Void handleNotFound(final IOException e) {
                throw new UnknownUidException(uid, objectClass);
            }
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public Schema schema() {
        if (null == schema) {
            final SchemaBuilder builder = new SchemaBuilder(GoogleAppsConnector.class);

            ObjectClassInfo user = UserHandler.getUserClassInfo(configuration.getCustomSchemasJSON());
            builder.defineObjectClass(user);

            ObjectClassInfo group = GroupHandler.getGroupClassInfo();
            builder.defineObjectClass(group);

            ObjectClassInfo member = GroupHandler.getMemberClassInfo();
            builder.defineObjectClass(member);

            ObjectClassInfo orgUnit = OrgunitsHandler.getOrgunitClassInfo();
            builder.defineObjectClass(orgUnit);

            ObjectClassInfo licenseAssignment = LicenseAssignmentsHandler.getLicenseAssignmentClassInfo();
            builder.defineObjectClass(licenseAssignment);

            builder.defineOperationOption(OperationOptionInfoBuilder.buildAttributesToGet(),
                    SearchOp.class);
            builder.defineOperationOption(OperationOptionInfoBuilder.buildPageSize(),
                    SearchOp.class);
            builder.defineOperationOption(OperationOptionInfoBuilder.buildPagedResultsCookie(),
                    SearchOp.class);
            builder.defineOperationOption(OperationOptionInfoBuilder.buildSortKeys(),
                    SearchOp.class);
            builder.defineOperationOption(
                    new OperationOptionInfo(SHOW_DELETED_PARAM, Boolean.class), SearchOp.class);

            schema = builder.build();
        }
        return schema;
    }

    @Override
    public FilterTranslator<Filter> createFilterTranslator(ObjectClass objectClass, OperationOptions options) {
        return new FilterTranslator<Filter>() {

            @Override
            public List<Filter> translate(Filter filter) {
                return CollectionUtil.newList(filter);
            }
        };
    }

    @Override
    public void executeQuery(
            final ObjectClass objectClass,
            final Filter query,
            final ResultsHandler handler,
            final OperationOptions options) {

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
                            request.setCustomer(MY_CUSTOMER_ID);
                        }
                    } else {
                        request.setCustomer(MY_CUSTOMER_ID);
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
                    String fields = getFields(options, ID_ATTR, ETAG_ATTR, PRIMARY_EMAIL_ATTR);
                    if (null != fields) {
                        request.setFields("nextPageToken,users(" + fields + ")");
                    }

                    if (options.getOptions().get(SHOW_DELETED_PARAM) instanceof Boolean) {
                        request.setShowDeleted(options.getOptions().get(SHOW_DELETED_PARAM).toString());
                    }

                    // Implementation to support the 'OP_SORT_KEYS'
                    if (null != options.getSortKeys()) {
                        for (SortKey sortKey : options.getSortKeys()) {
                            String orderBy;
                            if (sortKey.getField().equalsIgnoreCase(EMAIL_ATTR)
                                    || sortKey.getField().equalsIgnoreCase(PRIMARY_EMAIL_ATTR)
                                    || sortKey.getField().equalsIgnoreCase(ALIASES_ATTR)
                                    || sortKey.getField().equalsIgnoreCase(ALIAS_ATTR)) {
                                orderBy = EMAIL_ATTR;
                            } else if (sortKey.getField().equalsIgnoreCase(GIVEN_NAME_ATTR)) {
                                orderBy = GIVEN_NAME_ATTR;
                            } else if (sortKey.getField().equalsIgnoreCase(FAMILY_NAME_ATTR)) {
                                orderBy = FAMILY_NAME_ATTR;
                            } else {
                                LOG.ok("Unsupported SortKey:{0}", sortKey);
                                continue;
                            }

                            request.setOrderBy(orderBy);
                            if (sortKey.isAscendingOrder()) {
                                request.setSortOrder(ASCENDING_ORDER);
                            } else {
                                request.setSortOrder(DESCENDING_ORDER);
                            }
                            break;
                        }
                    }

                    String nextPageToken = null;
                    do {
                        nextPageToken = execute(request,
                                new RequestResultHandler<Directory.Users.List, Users, String>() {

                            @Override
                            public String handleResult(final Directory.Users.List request, final Users value) {
                                if (null != value.getUsers()) {
                                    for (User user : value.getUsers()) {
                                        handler.handle(fromUser(
                                                user, attributesToGet, configuration.getDirectory().groups()));
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
                    request.setFields(getFields(options, ID_ATTR, ETAG_ATTR, PRIMARY_EMAIL_ATTR));
                    request.setProjection(configuration.getProjection());

                    execute(request,
                            new RequestResultHandler<Directory.Users.Get, User, Boolean>() {

                        @Override
                        public Boolean handleResult(final Directory.Users.Get request, final User user) {
                            return handler.handle(
                                    fromUser(user, attributesToGet, configuration.getDirectory().groups()));
                        }

                        @Override
                        public Boolean handleNotFound(IOException e) {
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
                        query.accept(new GroupHandler(), request);
                    } else {
                        request.setCustomer(MY_CUSTOMER_ID);
                    }

                    boolean paged = false;
                    // Groups
                    if (options.getPageSize() != null && 0 < options.getPageSize()) {
                        request.setMaxResults(options.getPageSize());
                        paged = true;
                    }
                    request.setPageToken(options.getPagedResultsCookie());

                    // Implementation to support the 'OP_ATTRIBUTES_TO_GET'
                    String fields = getFields(options, ID_ATTR, ETAG_ATTR, EMAIL_ATTR);
                    if (null != fields) {
                        request.setFields("nextPageToken,groups(" + fields + ")");
                    }

                    String nextPageToken = null;
                    do {
                        nextPageToken = execute(request,
                                new RequestResultHandler<Directory.Groups.List, Groups, String>() {

                            @Override
                            public String handleResult(final Directory.Groups.List request, final Groups value) {
                                if (null != value.getGroups()) {
                                    for (Group group : value.getGroups()) {
                                        handler.handle(fromGroup(
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
                    request.setFields(getFields(options, ID_ATTR, ETAG_ATTR, EMAIL_ATTR));

                    execute(request, new RequestResultHandler<Directory.Groups.Get, Group, Boolean>() {

                        @Override
                        public Boolean handleResult(final Directory.Groups.Get request, final Group value) {
                            return handler.handle(fromGroup(value, attributesToGet,
                                    configuration.getDirectory().members()));
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
        } else if (MEMBER.equals(objectClass)) {
            if (null == key) {
                // Search request
                // TODO support AND role
                try {
                    String groupKey = null;

                    if (query instanceof EqualsFilter
                            && ((EqualsFilter) query).getAttribute().is(GROUP_KEY_ATTR)) {

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
                        nextPageToken = execute(request,
                                new RequestResultHandler<Directory.Members.List, Members, String>() {

                            @Override
                            public String handleResult(
                                    final Directory.Members.List request,
                                    final Members value) {
                                if (null != value.getMembers()) {
                                    for (Member group : value.getMembers()) {
                                        handler.handle(GroupHandler.fromMember(request.getGroupKey(), group));
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

                    execute(request,
                            new RequestResultHandler<Directory.Members.Get, Member, Boolean>() {

                        @Override
                        public Boolean handleResult(Directory.Members.Get request, Member value) {
                            return handler.handle(GroupHandler.fromMember(request.getGroupKey(), value));
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
        } else if (ORG_UNIT.equals(objectClass)) {
            if (null == key) {
                // Search request
                try {
                    Directory.Orgunits.List request = configuration.getDirectory().orgunits().list(MY_CUSTOMER_ID);
                    if (null != query) {
                        if (query instanceof StartsWithFilter
                                && AttributeUtil.namesEqual(ORG_UNIT_PATH_ATTR, ((StartsWithFilter) query).getName())) {

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
                    String fields = getFields(options, ORG_UNIT_PATH_ATTR, ETAG_ATTR, NAME_ATTR);
                    if (null != fields) {
                        request.setFields("organizationUnits(" + fields + ")");
                    }

                    execute(request,
                            new RequestResultHandler<Directory.Orgunits.List, OrgUnits, Void>() {

                        @Override
                        public Void handleResult(final Directory.Orgunits.List request,
                                final OrgUnits value) {
                            if (null != value.getOrganizationUnits()) {
                                for (OrgUnit group : value.getOrganizationUnits()) {
                                    handler.handle(OrgunitsHandler.fromOrgunit(group, attributesToGet));
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
                            get(MY_CUSTOMER_ID, Arrays.asList((String) key.getValue().get(0)));
                    request.setFields(getFields(options, ORG_UNIT_PATH_ATTR, ETAG_ATTR, NAME_ATTR));

                    execute(request,
                            new RequestResultHandler<Directory.Orgunits.Get, OrgUnit, Boolean>() {

                        @Override
                        public Boolean handleResult(Directory.Orgunits.Get request, OrgUnit value) {
                            return handler.handle(OrgunitsHandler.fromOrgunit(value, attributesToGet));
                        }

                        @Override
                        public Boolean handleNotFound(IOException e) {
                            // Do nothing if not found
                            return true;
                        }
                    });
                } catch (IOException e) {
                    LOG.warn(e, "Failed to initialize OrgUnits#Get");
                    throw ConnectorException.wrap(e);
                }
            }
        } else if (LICENSE_ASSIGNMENT.equals(objectClass)) {
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
                                        listForProduct(productId, MY_CUSTOMER_ID);

                        if (options.getPageSize() != null && 0 < options.getPageSize()) {
                            r.setMaxResults(Long.valueOf(options.getPageSize()));
                            paged = true;
                        }
                        r.setPageToken(options.getPagedResultsCookie());
                        request = r;
                    } else {
                        Licensing.LicenseAssignments.ListForProductAndSku r =
                                configuration.getLicensing().licenseAssignments().
                                        listForProductAndSku(productId, skuId, MY_CUSTOMER_ID);

                        if (options.getPageSize() != null && 0 < options.getPageSize()) {
                            r.setMaxResults(Long.valueOf(options.getPageSize()));
                            paged = true;
                        }
                        r.setPageToken(options.getPagedResultsCookie());
                        request = r;
                    }

                    String nextPageToken = null;
                    do {
                        nextPageToken = execute(request,
                                new RequestResultHandler<
                                                LicensingRequest<
                                                        LicenseAssignmentList>, LicenseAssignmentList, String>() {

                            @Override
                            public String handleResult(
                                    LicensingRequest<LicenseAssignmentList> request,
                                    LicenseAssignmentList value) {

                                if (null != value.getItems()) {
                                    for (LicenseAssignment resource : value.getItems()) {
                                        handler.handle(LicenseAssignmentsHandler.fromLicenseAssignment(resource));
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

                    execute(request,
                            new RequestResultHandler<Licensing.LicenseAssignments.Get, LicenseAssignment, Boolean>() {

                        @Override
                        public Boolean handleResult(
                                Licensing.LicenseAssignments.Get request,
                                LicenseAssignment value) {

                            return handler.handle(LicenseAssignmentsHandler.fromLicenseAssignment(value));
                        }

                        @Override
                        public Boolean handleNotFound(IOException e) {
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

    protected Attribute getKeyFromFilter(ObjectClass objectClass, Filter filter) {
        Attribute key = null;
        if (filter instanceof EqualsFilter) {
            // Account, Group, OrgUnit object classes
            Attribute filterAttr = ((EqualsFilter) filter).getAttribute();
            if (filterAttr instanceof Uid) {
                key = filterAttr;
            } else if (ObjectClass.ACCOUNT.equals(objectClass) || ObjectClass.GROUP.equals(objectClass)
                    && (filterAttr instanceof Name || filterAttr.getName().equalsIgnoreCase(ALIASES_ATTR))) {
                key = filterAttr;
            } else if (ORG_UNIT.equals(objectClass) && filterAttr.getName().equalsIgnoreCase(ORG_UNIT_PATH_ATTR)) {
                key = filterAttr;
            }
        } else if (filter instanceof AndFilter) {
            // Member object class
            if (MEMBER.equals(objectClass)) {
                Attribute groupKey = null;
                Attribute memberKey = null;
                StringBuilder memberId = new StringBuilder();

                Collection<Filter> filters = ((AndFilter) filter).getFilters();
                for (Filter f : filters) {
                    if (f instanceof EqualsFilter) {
                        Attribute filterAttr = ((EqualsFilter) f).getAttribute();
                        if (filterAttr.getName().equalsIgnoreCase(GROUP_KEY_ATTR)) {
                            groupKey = filterAttr;
                        } else if (filterAttr.getName().equalsIgnoreCase(EMAIL_ATTR) || filterAttr.getName().
                                equalsIgnoreCase(ALIAS_ATTR) || filterAttr instanceof Uid) {
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

    protected Set<String> getAttributesToGet(ObjectClass objectClass, OperationOptions options) {
        Set<String> attributesToGet = null;
        if (null != options.getAttributesToGet()) {
            attributesToGet = CollectionUtil.newCaseInsensitiveSet();
            if (ORG_UNIT.equals(objectClass)) {
                attributesToGet.add(ORG_UNIT_PATH_ATTR);
            } else {
                attributesToGet.add(ID_ATTR);
            }
            attributesToGet.add(ETAG_ATTR);
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

    protected String googleName(ObjectClass objectClass, String attributeName) {
        if (AttributeUtil.namesEqual(Name.NAME, attributeName)) {
            if (ObjectClass.ACCOUNT.equals(objectClass)) {
                return PRIMARY_EMAIL_ATTR;
            } else if (ObjectClass.GROUP.equals(objectClass)) {
                return EMAIL_ATTR;
            } else {
                return NAME_ATTR;
            }
        }

        if (AttributeUtil.namesEqual(PredefinedAttributes.DESCRIPTION, attributeName)) {
            return DESCRIPTION_ATTR;
        }

        if (AttributeUtil.namesEqual(FAMILY_NAME_ATTR, attributeName)) {
            return "name/familyName";
        }
        if (AttributeUtil.namesEqual(GIVEN_NAME_ATTR, attributeName)) {
            return "name/givenName";
        }
        if (AttributeUtil.namesEqual(FULL_NAME_ATTR, attributeName)) {
            return "name/fullName";
        }
        return attributeName; //__GROUPS__ //__PASSWORD__
    }

    protected Set<String> _getAttributesToGet(OperationOptions options) {
        Set<String> attributesToGet = null;
        if (null != options.getAttributesToGet()) {
            attributesToGet = CollectionUtil.newCaseInsensitiveSet();
            for (String attribute : options.getAttributesToGet()) {
                StringBuilder builder = new StringBuilder();
                loop:
                for (int i = 0; i < attribute.length(); i++) {
                    char c = attribute.charAt(i);
                    switch (c) {
                        case '/': {
                            if (i == 0) {
                                // Strip off the leading '/'
                                break;
                            } else if (i == 1) {
                                throw new IllegalArgumentException("Invalid attribute name to get:"
                                        + attribute);
                            }
                            break loop;
                        }
                        case '(': {
                            if (i == 0) {
                                throw new IllegalArgumentException("Invalid attribute name to get:"
                                        + attribute);
                            }
                            break loop;
                        }
                        default:
                            builder.append(c);
                    }
                }
                attributesToGet.add(builder.toString());
            }
        }
        return attributesToGet;
    }

    protected String getFields(OperationOptions options, String... nameAttribute) {
        if (null != options.getAttributesToGet()) {
            Set<String> attributes = CollectionUtil.newCaseInsensitiveSet();
            attributes.addAll(Arrays.asList(nameAttribute));
            final boolean notBlankCustomSchemas = StringUtil.isNotBlank(configuration.getCustomSchemasJSON());
            final List<String> customSchemaNames = notBlankCustomSchemas
                    ? customSchemaNames(configuration.getCustomSchemasJSON())
                    : new ArrayList<String>();
            for (String attribute : options.getAttributesToGet()) {
                if (AttributeUtil.namesEqual(PredefinedAttributes.DESCRIPTION, attribute)) {
                    attributes.add(DESCRIPTION_ATTR);
                } else if (AttributeUtil.isSpecialName(attribute)) {
                } else if (AttributeUtil.namesEqual(FAMILY_NAME_ATTR, attribute)) {
                    attributes.add("name/familyName");
                } else if (AttributeUtil.namesEqual(GIVEN_NAME_ATTR, attribute)) {
                    attributes.add("name/givenName");
                } else if (AttributeUtil.namesEqual(FULL_NAME_ATTR, attribute)) {
                    attributes.add("name/fullName");
                } else if (!customSchemaNames.contains(attribute)) {
                    attributes.add(attribute);
                }
                // return also customSchemas according to configuration
                if ("full".equals(configuration.getProjection()) && notBlankCustomSchemas) {
                    attributes.add(CUSTOM_SCHEMAS);
                }
            }
            return StringUtil.join(attributes, COMMA);
        }
        return null;
    }

    @Override
    public void test() {
        LOG.ok("Test works well");
    }

    @Override
    public Uid update(
            final ObjectClass objectClass,
            final Uid uid,
            final Set<Attribute> replaceAttributes,
            final OperationOptions options) {

        final AttributesAccessor attributesAccessor = new AttributesAccessor(replaceAttributes);

        Uid uidAfterUpdate = uid;
        if (ObjectClass.ACCOUNT.equals(objectClass)) {
            final Directory.Users.Patch patch =
                    UserHandler.updateUser(configuration.getDirectory().users(), uid.getUidValue(), attributesAccessor,
                            configuration.getCustomSchemasJSON());
            if (null != patch) {
                uidAfterUpdate = execute(patch, new RequestResultHandler<Directory.Users.Patch, User, Uid>() {

                    @Override
                    public Uid handleResult(Directory.Users.Patch request, User value) {
                        LOG.ok("User is Updated:{0}", value.getId());
                        return new Uid(value.getId(), value.getEtag());
                    }
                });
            }
            Attribute groups = attributesAccessor.find(PredefinedAttributes.GROUPS_NAME);
            if (null != groups && null != groups.getValue()) {
                final Directory.Members service = configuration.getDirectory().members();
                if (groups.getValue().isEmpty()) {
                    // Remove all membership
                    for (String groupKey : listGroups(configuration.getDirectory().groups(),
                            uidAfterUpdate.getUidValue())) {

                        execute(GroupHandler.deleteMembers(service, groupKey, uidAfterUpdate.getUidValue()),
                                new RequestResultHandler<Directory.Members.Delete, Void, Object>() {

                            @Override
                            public Object handleResult(Directory.Members.Delete request, Void value) {
                                return null;
                            }

                            @Override
                            public Object handleNotFound(IOException e) {
                                // It may be an indirect membership,
                                // not able to delete
                                return null;
                            }
                        });
                    }
                } else {
                    final Set<String> activeGroups =
                            listGroups(configuration.getDirectory().groups(), uidAfterUpdate.getUidValue());

                    final List<Directory.Members.Insert> addGroups = new ArrayList<Directory.Members.Insert>();
                    final Set<String> keepGroups = CollectionUtil.newCaseInsensitiveSet();

                    for (Object member : groups.getValue()) {
                        if (member instanceof String) {
                            if (activeGroups.contains((String) member)) {
                                keepGroups.add((String) member);
                            } else {
                                String email = attributesAccessor.getName().getNameValue();
                                addGroups.add(GroupHandler.createMember(service, (String) member, email, null));
                            }
                        } else if (null != member) {
                            // throw error/revert?
                            throw new InvalidAttributeValueException(
                                    "Attribute '__GROUPS__' must be a String list");
                        }
                    }

                    // Add new Member object
                    for (Directory.Members.Insert insert : addGroups) {
                        execute(insert, new RequestResultHandler<Directory.Members.Insert, Member, Object>() {

                            @Override
                            public Object handleResult(Directory.Members.Insert request, Member value) {
                                return null;
                            }

                            @Override
                            public Object handleDuplicate(IOException e) {
                                // Do nothing
                                return null;
                            }
                        });
                    }

                    // Delete existing Member object
                    if (activeGroups.removeAll(keepGroups)) {
                        for (String groupKey : activeGroups) {
                            execute(GroupHandler.deleteMembers(service, groupKey, uidAfterUpdate.getUidValue()),
                                    new RequestResultHandler<Directory.Members.Delete, Void, Object>() {

                                @Override
                                public Object handleResult(
                                        Directory.Members.Delete request, Void value) {
                                    return null;
                                }

                                @Override
                                public Object handleNotFound(IOException e) {
                                    // It may be an indirect membership,
                                    // not able to delete
                                    return null;
                                }
                            });
                        }
                    }
                }
            }
            // GOOGLEAPPS-9
            // license management: if remove license param is true and __ENABLE__ is false perform delete license
            // license read must be performed with the user primaryEmail, userId is not allowed
            if (configuration.getRemoveLicenseOnDisable()
                    && !attributesAccessor.findBoolean(OperationalAttributes.ENABLE_NAME)
                    && StringUtil.isNotBlank(attributesAccessor.findString(PRIMARY_EMAIL_ATTR))) {
                for (String skuId : configuration.getSkuIds()) {
                    // 1. retrieve user license
                    try {
                        // use email as key
                        Licensing.LicenseAssignments.Get request =
                                configuration.getLicensing().licenseAssignments().get(
                                        configuration.getProductId(),
                                        skuId,
                                        attributesAccessor.findString(PRIMARY_EMAIL_ATTR));
                        execute(request,
                                new RequestResultHandler<Licensing.LicenseAssignments.Get, LicenseAssignment, Boolean>() {

                            @Override
                            public Boolean handleResult(
                                    Licensing.LicenseAssignments.Get request,
                                    LicenseAssignment value) {
                                try {
                                    // 2. remove license
                                    delete(LICENSE_ASSIGNMENT,
                                            new Uid(GoogleAppsUtil.generateLicenseId(
                                                    value.getProductId(),
                                                    value.getSkuId(),
                                                    value.getUserId())), null);
                                } catch (Exception e) {
                                    LOG.error(e, "Failed to delete license for user {0}", value.getUserId());
                                    throw ConnectorException.wrap(e);
                                }
                                return true;
                            }

                            @Override
                            public Boolean handleNotFound(IOException e) {
                                // Do nothing if not found
                                return true;
                            }
                        });
                    } catch (IOException e) {
                        LOG.error(e, "Unable to find license for {0}-{1}-{2}", configuration.getProductId(), skuId,
                                attributesAccessor.findString(PRIMARY_EMAIL_ATTR));
                    }
                }
            }
        } else if (ObjectClass.GROUP.equals(objectClass)) {
            final Directory.Groups.Patch patch = GroupHandler.updateGroup(
                    configuration.getDirectory().groups(), uid.getUidValue(), attributesAccessor);
            if (null != patch) {
                uidAfterUpdate = execute(patch, new RequestResultHandler<Directory.Groups.Patch, Group, Uid>() {

                    @Override
                    public Uid handleResult(Directory.Groups.Patch request, Group value) {
                        LOG.ok("Group is Updated:{0}", value.getId());
                        return new Uid(value.getId(), value.getEtag());
                    }
                });
            }
            Attribute members = attributesAccessor.find(MEMBERS_ATTR);
            if (null != members && null != members.getValue()) {
                final Directory.Members service = configuration.getDirectory().members();
                if (members.getValue().isEmpty()) {
                    // Remove all membership
                    for (Map<String, String> member : listMembers(service, uidAfterUpdate.getUidValue(), null)) {
                        execute(GroupHandler.deleteMembers(
                                service, uidAfterUpdate.getUidValue(), member.get(EMAIL_ATTR)),
                                new RequestResultHandler<Directory.Members.Delete, Void, Object>() {

                            @Override
                            public Object handleResult(Directory.Members.Delete request,
                                    Void value) {
                                return null;
                            }

                            @Override
                            public Object handleNotFound(IOException e) {
                                // Do nothing
                                return null;
                            }
                        });
                    }
                } else {
                    final List<Map<String, String>> activeMembership =
                            listMembers(service, uidAfterUpdate.getUidValue(), null);

                    final List<Directory.Members.Insert> addMembership =
                            new ArrayList<Directory.Members.Insert>();
                    final List<Directory.Members.Patch> patchMembership =
                            new ArrayList<Directory.Members.Patch>();

                    for (Object member : members.getValue()) {
                        if (member instanceof Map) {

                            String email = (String) ((Map) member).get(EMAIL_ATTR);
                            if (null == email) {
                                continue;
                            }
                            String role = (String) ((Map) member).get(ROLE_ATTR);
                            if (null == role) {
                                role = "MEMBER";
                            }

                            boolean notMember = true;
                            for (Map<String, String> a : activeMembership) {
                                // How to handle ROLE update?
                                // OWNER -> MANAGER -> MEMBER
                                if (email.equalsIgnoreCase(a.get(EMAIL_ATTR))) {
                                    a.put("keep", null);
                                    if (!role.equalsIgnoreCase(a.get(ROLE_ATTR))) {
                                        patchMembership.add(GroupHandler.updateMembers(
                                                service, uidAfterUpdate.getUidValue(), email, role));
                                    }
                                    notMember = false;
                                    break;
                                }
                            }
                            if (notMember) {
                                addMembership.add(GroupHandler.createMember(
                                        service, uidAfterUpdate.getUidValue(), email, role));
                            }
                        } else if (null != member) {
                            // throw error/revert?
                            throw new InvalidAttributeValueException(
                                    "Attribute 'members' must be a Map list");
                        }
                    }

                    // Add new Member object
                    for (Directory.Members.Insert insert : addMembership) {
                        execute(insert, new RequestResultHandler<Directory.Members.Insert, Member, Object>() {

                            @Override
                            public Object handleResult(Directory.Members.Insert request, Member value) {
                                return null;
                            }

                            @Override
                            public Object handleDuplicate(IOException e) {
                                // Do nothing
                                return null;
                            }
                        });
                    }

                    // Update existing Member object
                    for (Directory.Members.Patch request : patchMembership) {
                        execute(request, new RequestResultHandler<Directory.Members.Patch, Member, Object>() {

                            @Override
                            public Object handleResult(Directory.Members.Patch request, Member value) {
                                return null;
                            }
                        });
                    }

                    // Delete existing Member object
                    for (Map<String, String> am : activeMembership) {
                        if (!am.containsKey("keep")) {
                            execute(GroupHandler.deleteMembers(
                                    service, uidAfterUpdate.getUidValue(), am.get(EMAIL_ATTR)),
                                    new RequestResultHandler<Directory.Members.Delete, Void, Object>() {

                                @Override
                                public Object handleResult(
                                        Directory.Members.Delete request, Void value) {
                                    return null;
                                }

                                @Override
                                public Object handleNotFound(IOException e) {
                                    // Do nothing
                                    return null;
                                }
                            });
                        }
                    }
                }
            }
        } else if (MEMBER.equals(objectClass)) {
            String role = attributesAccessor.findString(ROLE_ATTR);
            if (StringUtil.isNotBlank(role)) {
                String[] ids = uid.getUidValue().split("/");
                if (ids.length == 2) {
                    final Directory.Members.Patch patch = GroupHandler.updateMembers(
                            configuration.getDirectory().members(), ids[0], ids[1], role).setFields(EMAIL_ETAG);
                    uidAfterUpdate = execute(patch, new RequestResultHandler<Directory.Members.Patch, Member, Uid>() {

                        @Override
                        public Uid handleResult(Directory.Members.Patch request, Member value) {
                            LOG.ok("Member is updated:{0}/{1}", request.getGroupKey(), value.getEmail());
                            return GroupHandler.generateMemberId(request.getGroupKey(), value);
                        }
                    });
                } else {
                    throw new UnknownUidException("Invalid ID format");
                }
            }
        } else if (ORG_UNIT.equals(objectClass)) {
            final Directory.Orgunits.Patch patch = OrgunitsHandler.updateOrgunit(
                    configuration.getDirectory().orgunits(), uid.getUidValue(), attributesAccessor);
            if (null != patch) {
                uidAfterUpdate = execute(patch, new RequestResultHandler<Directory.Orgunits.Patch, OrgUnit, Uid>() {

                    @Override
                    public Uid handleResult(Directory.Orgunits.Patch request, OrgUnit value) {
                        LOG.ok("OrgUnit is updated:{0}", value.getName());
                        return OrgunitsHandler.generateOrgUnitId(value);
                    }
                });
            }
        } else if (LICENSE_ASSIGNMENT.equals(objectClass)) {
            final Licensing.LicenseAssignments.Patch patch =
                    LicenseAssignmentsHandler.updateLicenseAssignment(
                            configuration.getLicensing().licenseAssignments(), uid.getUidValue(), attributesAccessor);
            if (null != patch) {
                uidAfterUpdate = execute(patch,
                        new RequestResultHandler<Licensing.LicenseAssignments.Patch, LicenseAssignment, Uid>() {

                    @Override
                    public Uid handleResult(
                            Licensing.LicenseAssignments.Patch request,
                            LicenseAssignment value) {

                        LOG.ok("LicenseAssignment is Updated:{0}/{1}/{2}",
                                value.getProductId(), value.getSkuId(), value.getUserId());
                        return LicenseAssignmentsHandler.generateLicenseAssignmentId(value);
                    }
                });
            }
        } else {
            LOG.warn("Update of type {0} is not supported", configuration.getConnectorMessages()
                    .format(objectClass.getDisplayNameKey(), objectClass.getObjectClassValue()));
            throw new UnsupportedOperationException("Update of type"
                    + objectClass.getObjectClassValue() + " is not supported");
        }
        return uidAfterUpdate;
    }

    protected ConnectorObject fromUser(final User user, Set<String> attributesToGet, final Directory.Groups service) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        if (null != user.getEtag()) {
            builder.setUid(new Uid(user.getId(), user.getEtag()));
        } else {
            builder.setUid(user.getId());
        }
        builder.setName(user.getPrimaryEmail());
        if (user.getSuspended() != null) {
            builder.addAttribute(AttributeBuilder.build(OperationalAttributes.ENABLE_NAME, !user.getSuspended()));
        }

        if ((null == attributesToGet || attributesToGet.contains(ID_ATTR))) {
            builder.addAttribute(AttributeBuilder.build(ID_ATTR, user.getId()));
        }
        if ((null == attributesToGet || attributesToGet.contains(PRIMARY_EMAIL_ATTR))) {
            builder.addAttribute(AttributeBuilder.build(PRIMARY_EMAIL_ATTR, user.getPrimaryEmail()));
        }
        // Optional
        // If both givenName and familyName are empty then Google didn't return with 'name'
        if (null == attributesToGet || attributesToGet.contains(GIVEN_NAME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GIVEN_NAME_ATTR,
                    null != user.getName() ? user.getName().getGivenName() : null));
        }
        if (null == attributesToGet || attributesToGet.contains(FAMILY_NAME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(FAMILY_NAME_ATTR,
                    null != user.getName() ? user.getName().getFamilyName() : null));
        }
        if (null == attributesToGet || attributesToGet.contains(FULL_NAME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(FULL_NAME_ATTR,
                    null != user.getName() ? user.getName().getFullName() : null));
        }

        if (null == attributesToGet || attributesToGet.contains(IS_ADMIN_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(IS_ADMIN_ATTR, user.getIsAdmin()));
        }
        if (null == attributesToGet || attributesToGet.contains(IS_DELEGATED_ADMIN_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(IS_DELEGATED_ADMIN_ATTR, user.getIsDelegatedAdmin()));
        }
        if ((null == attributesToGet || attributesToGet.contains(LAST_LOGIN_TIME_ATTR))
                && user.getLastLoginTime() != null) {

            builder.addAttribute(AttributeBuilder.build(LAST_LOGIN_TIME_ATTR, user.getLastLoginTime().toString()));
        }
        if ((null == attributesToGet || attributesToGet.contains(CREATION_TIME_ATTR))
                && user.getCreationTime() != null) {

            builder.addAttribute(AttributeBuilder.build(CREATION_TIME_ATTR, user.getCreationTime().toString()));
        }
        if (null == attributesToGet || attributesToGet.contains(AGREED_TO_TERMS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(AGREED_TO_TERMS_ATTR, user.getAgreedToTerms()));
        }
        if (null == attributesToGet || attributesToGet.contains(SUSPENSION_REASON_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(SUSPENSION_REASON_ATTR, user.getSuspensionReason()));
        }
        if (null == attributesToGet || attributesToGet.contains(CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR)) {
            builder.addAttribute(
                    AttributeBuilder.build(CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR, user.getChangePasswordAtNextLogin()));
        }
        if (null == attributesToGet || attributesToGet.contains(IP_WHITELISTED_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(IP_WHITELISTED_ATTR, user.getIpWhitelisted()));
        }
        if (null == attributesToGet || attributesToGet.contains(IMS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(IMS_ATTR, (Collection) user.getIms()));
        }
        if (null == attributesToGet || attributesToGet.contains(EMAILS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(EMAILS_ATTR, (Collection) user.getEmails()));
        }
        if (null == attributesToGet || attributesToGet.contains(EXTERNAL_IDS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(EXTERNAL_IDS_ATTR, (Collection) user.getExternalIds()));
        }
        if (null == attributesToGet || attributesToGet.contains(RELATIONS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(RELATIONS_ATTR, (Collection) user.getRelations()));
        }
        if (null == attributesToGet || attributesToGet.contains(ADDRESSES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(ADDRESSES_ATTR, (Collection) user.getAddresses()));
        }
        if (null == attributesToGet || attributesToGet.contains(ORGANIZATIONS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(ORGANIZATIONS_ATTR, (Collection) user.getOrganizations()));
        }
        if (null == attributesToGet || attributesToGet.contains(PHONES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(PHONES_ATTR, (Collection) user.getPhones()));
        }
        if (null == attributesToGet || attributesToGet.contains(ALIASES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(ALIASES_ATTR, user.getAliases()));
        }

        if (null == attributesToGet || attributesToGet.contains(NON_EDITABLE_ALIASES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(NON_EDITABLE_ALIASES_ATTR, user.getNonEditableAliases()));
        }

        if (null == attributesToGet || attributesToGet.contains(CUSTOMER_ID_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(CUSTOMER_ID_ATTR, user.getCustomerId()));
        }
        if (null == attributesToGet || attributesToGet.contains(ORG_UNIT_PATH_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(ORG_UNIT_PATH_ATTR, user.getOrgUnitPath()));
        }
        if (null == attributesToGet || attributesToGet.contains(IS_MAILBOX_SETUP_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(IS_MAILBOX_SETUP_ATTR, user.getIsMailboxSetup()));
        }
        if (null == attributesToGet
                || attributesToGet.contains(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR)) {
            builder.addAttribute(
                    AttributeBuilder.build(INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, user.getIncludeInGlobalAddressList()));
        }
        if (null == attributesToGet || attributesToGet.contains(THUMBNAIL_PHOTO_URL_ATTR)) {
            builder.addAttribute(
                    AttributeBuilder.build(THUMBNAIL_PHOTO_URL_ATTR, user.getThumbnailPhotoUrl()));
        }
        if (null == attributesToGet || attributesToGet.contains(DELETION_TIME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(DELETION_TIME_ATTR, null != user
                    .getDeletionTime() ? user.getDeletionTime().toString() : null));
        }
        if (null == attributesToGet || ("full".equals(configuration.getProjection())
                && StringUtil.isNotBlank(configuration.getCustomSchemasJSON()))) {
            List<GoogleAppsCustomSchema> customSchemas = GoogleAppsUtil.extractCustomSchemas(configuration.
                    getCustomSchemasJSON());
            for (GoogleAppsCustomSchema customSchema : customSchemas) {
                if (customSchema.getType().equals("object")) {
                    // parse inner schemas
                    String basicName = customSchema.getName();
                    // manage only first level inner schemas
                    for (GoogleAppsCustomSchema innerSchema : customSchema.getInnerSchemas()) {
                        final String innerSchemaName = basicName + "." + innerSchema.getName();
                        builder.addAttribute(AttributeBuilder.build(
                                innerSchemaName,
                                null != user.getCustomSchemas()
                                ? getValueFromKey(innerSchemaName, user.getCustomSchemas())
                                : null));
                    }
                } else {
                    LOG.warn("CustomSchema type {0} not allowed at this level", customSchema.getType());
                }
            }
        }
        // Expensive to get
        if (null != attributesToGet && attributesToGet.contains(PredefinedAttributes.GROUPS_NAME)) {
            builder.addAttribute(
                    AttributeBuilder.build(PredefinedAttributes.GROUPS_NAME, listGroups(service, user.getId())));
        }

        return builder.build();
    }

    protected ConnectorObject fromGroup(
            final Group group,
            final Set<String> attributesToGet,
            final Directory.Members service) {

        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(ObjectClass.GROUP);

        if (null != group.getEtag()) {
            builder.setUid(new Uid(group.getId(), group.getEtag()));
        } else {
            builder.setUid(group.getId());
        }
        builder.setName(group.getEmail());

        // Optional
        if (null == attributesToGet || attributesToGet.contains(NAME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(NAME_ATTR, group.getName()));
        }
        if (null == attributesToGet || attributesToGet.contains(PredefinedAttributes.DESCRIPTION)) {
            builder.addAttribute(AttributeBuilder.build(PredefinedAttributes.DESCRIPTION, group.getDescription()));
        }

        if (null == attributesToGet || attributesToGet.contains(ADMIN_CREATED_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(ADMIN_CREATED_ATTR, group.getAdminCreated()));
        }
        if (null == attributesToGet || attributesToGet.contains(ALIASES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(ALIASES_ATTR, group.getAliases()));
        }
        if (null == attributesToGet || attributesToGet.contains(NON_EDITABLE_ALIASES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(NON_EDITABLE_ALIASES_ATTR, group.getNonEditableAliases()));
        }
        if (null == attributesToGet || attributesToGet.contains(DIRECT_MEMBERS_COUNT_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(DIRECT_MEMBERS_COUNT_ATTR, group.getDirectMembersCount()));
        }

        // Expensive to get
        if (null != attributesToGet && attributesToGet.contains(MEMBERS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(MEMBERS_ATTR, listMembers(service, group.getId(), null)));
        }

        return builder.build();
    }

    protected List<Map<String, String>> listMembers(Directory.Members service, String groupKey, String roles) {
        final List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        try {
            Directory.Members.List request = service.list(groupKey);
            request.setRoles(StringUtil.isBlank(roles) ? "OWNER,MANAGER,MEMBER" : roles);

            String nextPageToken;
            do {
                nextPageToken = execute(request,
                        new RequestResultHandler<Directory.Members.List, Members, String>() {

                    @Override
                    public String handleResult(Directory.Members.List request,
                            Members value) {
                        if (null != value.getMembers()) {
                            for (Member member : value.getMembers()) {
                                Map<String, String> m =
                                        new LinkedHashMap<String, String>(2);
                                m.put(EMAIL_ATTR, member.getEmail());
                                m.put(ROLE_ATTR, member.getRole());
                                result.add(m);
                            }
                        }
                        return value.getNextPageToken();
                    }
                });
            } while (StringUtil.isNotBlank(nextPageToken));
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Members#Delete");
            throw ConnectorException.wrap(e);
        }
        return result;
    }

    protected Set<String> listGroups(Directory.Groups service, String userKey) {
        final Set<String> result = CollectionUtil.newCaseInsensitiveSet();
        try {
            Directory.Groups.List request = service.list();
            request.setUserKey(userKey);
            request.setFields("groups/email");
            // 400 Bad Request if the Customer(my_customer or exact value) is set, only domain-userKey combination 
            // allowed. request.setCustomer(MY_CUSTOMER_ID);
            request.setDomain(configuration.getDomain());

            String nextPageToken;
            do {
                nextPageToken = execute(request,
                        new RequestResultHandler<Directory.Groups.List, Groups, String>() {

                    @Override
                    public String handleResult(Directory.Groups.List request, Groups value) {
                        if (null != value.getGroups()) {
                            for (Group group : value.getGroups()) {
                                result.add(group.getEmail());
                            }
                        }
                        return value.getNextPageToken();
                    }
                });
            } while (StringUtil.isNotBlank(nextPageToken));
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Members#Delete");
            throw ConnectorException.wrap(e);
        }
        return result;
    }

    protected <G extends AbstractGoogleJsonClientRequest<T>, T, R> R execute(
            G request, RequestResultHandler<G, T, R> handler) {

        return execute(
                Assertions.nullChecked(request, "Google Json ClientRequest"),
                Assertions.nullChecked(handler, "handler"), -1);
    }

    protected <G extends AbstractGoogleJsonClientRequest<T>, T, R> R execute(
            G request, RequestResultHandler<G, T, R> handler, int retry) {

        try {
            if (retry >= 0) {
                long sleep = (long) ((1000 * Math.pow(2, retry)) + nextLong(1000));
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    throw ConnectorException.wrap(e);
                }
            }
            return handler.handleResult(request, request.execute());
        } catch (GoogleJsonResponseException e) {
            GoogleJsonError details = e.getDetails();
            if (null != details && null != details.getErrors()) {
                GoogleJsonError.ErrorInfo errorInfo = details.getErrors().get(0);
                // error: 403
                switch (e.getStatusCode()) {
                    case HttpStatusCodes.STATUS_CODE_FORBIDDEN:
                        if ("userRateLimitExceeded".equalsIgnoreCase(errorInfo.getReason())
                                || "rateLimitExceeded".equalsIgnoreCase(errorInfo.getReason())) {
                            LOG.info("System should retry");
                        }
                        break;
                    case HttpStatusCodes.STATUS_CODE_NOT_FOUND:
                        if ("notFound".equalsIgnoreCase(errorInfo.getReason())) {
                            return handler.handleNotFound(e);
                        }
                        break;
                    case 409:
                        if ("duplicate".equalsIgnoreCase(errorInfo.getReason())) {
                            // Already Exists
                            handler.handleDuplicate(e);
                        }
                        break;
                    case 400:
                        if ("invalid".equalsIgnoreCase(errorInfo.getReason())) {
                            // Already Exists "Invalid Ou Id"
                        }
                        break;
                    case HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE:
                        if ("backendError".equalsIgnoreCase(errorInfo.getReason())) {
                            throw RetryableException.wrap(e.getMessage(), e);
                        }
                        break;
                    default:
                        break;
                }
            }

            if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
                if (retry < 5) {
                    return execute(request, handler, ++retry);
                } else {
                    handler.handleError(e);
                }
            } else if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
                return handler.handleNotFound(e);
            }
            throw ConnectorException.wrap(e);
        } catch (IOException e) {
            // https://developers.google.com/admin-sdk/directory/v1/limits
            // rateLimitExceeded or userRateLimitExceeded
            if (retry < 5) {
                return execute(request, handler, ++retry);
            } else {
                return handler.handleError(e);
            }
        }
    }

    protected RuntimeException get(GoogleJsonError.ErrorInfo errorInfo) {
        return null;
    }

    private static final Random RANDOM = new Random();

    private static long nextLong(long n) {
        long bits, val;
        do {
            bits = (RANDOM.nextLong() << 1) >>> 1;
            val = bits % n;
        } while (bits - val + (n - 1) < 0L);
        return val;
    }

    private Object getValueFromKey(final String customSchema, final Map<String, Map<String, Object>> customSchemas) {
        String[] names = customSchema.split("\\.");
        return names.length > 1
                ? customSchemas.get(names[0]) != null ? customSchemas.get(names[0]).get(names[1]) : null
                : null;
    }

    private List<String> customSchemaNames(final String customSchemasJSON) {
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

}
