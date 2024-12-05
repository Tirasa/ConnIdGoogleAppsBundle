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

import static net.tirasa.connid.bundles.googleapps.GoogleApiExecutor.execute;

import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Alias;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Groups;
import com.google.common.base.CharMatcher;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaUtil;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.AndFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsAllValuesFilter;
import org.identityconnectors.framework.common.objects.filter.ContainsFilter;
import org.identityconnectors.framework.common.objects.filter.EndsWithFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.EqualsIgnoreCaseFilter;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterVisitor;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.NotFilter;
import org.identityconnectors.framework.common.objects.filter.OrFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;

/**
 * A GroupHandler is a util class to cover all Group related operations.
 *
 * @author Laszlo Hordos
 */
public class GroupHandler implements FilterVisitor<StringBuilder, Directory.Groups.List> {

    /**
     * Setup logging for the {@link GroupHandler}.
     */
    private static final Log LOG = Log.getLog(GroupHandler.class);

    private static final Escaper STRING_ESCAPER = Escapers.builder().addEscape('\'', "\\'").build();

    @Override
    public StringBuilder visitAndFilter(final Directory.Groups.List list, final AndFilter andFilter) {
        throw getException();
    }

    @Override
    public StringBuilder visitContainsFilter(final Directory.Groups.List list, final ContainsFilter containsFilter) {
        if (containsFilter.getAttribute().is(GoogleAppsUtil.MEMBERS_ATTR)) {
            list.setUserKey(containsFilter.getValue());
        } else {
            throw getException();
        }
        return null;
    }

    protected StringBuilder getStringBuilder(
            final Attribute attribute, final char operator, final Character postfix, final String filedName) {

        StringBuilder builder = new StringBuilder();
        builder.append(filedName).append(operator);
        String stringValue = AttributeUtil.getAsStringValue(attribute);
        if (StringUtil.isNotBlank(stringValue)) {
            stringValue = STRING_ESCAPER.escape(stringValue);
            if (CharMatcher.whitespace().matchesAnyOf(stringValue)) {
                builder.append('\'').append(stringValue);
                if (null != postfix) {
                    builder.append(postfix);
                }
                builder.append('\'');
            } else {
                builder.append(stringValue);
                if (null != postfix) {
                    builder.append(postfix);
                }
            }
        }
        return builder;
    }

    @Override
    public StringBuilder visitContainsAllValuesFilter(
            final Directory.Groups.List list, final ContainsAllValuesFilter containsAllValuesFilter) {

        throw getException();
    }

    protected RuntimeException getException() {
        return new UnsupportedOperationException(
                "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
    }

    @Override
    public StringBuilder visitEqualsFilter(final Directory.Groups.List list, final EqualsFilter equalsFilter) {
        if (equalsFilter.getAttribute().is("customer")) {
            if (null != list.getDomain() || null != list.getUserKey()) {
                throw new InvalidAttributeValueException(
                        "The 'customer', 'domain' and 'userKey' can not be in the same query");
            } else {
                list.setCustomer(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            }
        } else if (equalsFilter.getAttribute().is("domain")) {
            if (null != list.getCustomer() || null != list.getUserKey()) {
                throw new InvalidAttributeValueException(
                        "The 'customer', 'domain' and 'userKey' can not be in the same query");
            } else {
                list.setDomain(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            }
        } else if (equalsFilter.getAttribute().is("userKey")) {
            if (null != list.getDomain() || null != list.getCustomer()) {
                throw new InvalidAttributeValueException(
                        "The 'customer', 'domain' and 'userKey' can not be in the same query");
            } else {
                list.setUserKey(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            }
        } else {
            String filedName = equalsFilter.getName();
            if (null != filedName) {
                return getStringBuilder(equalsFilter.getAttribute(), '=', null, filedName);
            } else {
                // Warning: not supported field name
                throw new InvalidAttributeValueException("");
            }
        }

        return null;
    }

    @Override
    public StringBuilder visitEqualsIgnoreCaseFilter(
            final Directory.Groups.List list, final EqualsIgnoreCaseFilter equalsFilter) {

        if (equalsFilter.getAttribute().is("customer")) {
            if (null != list.getDomain() || null != list.getUserKey()) {
                throw new InvalidAttributeValueException(
                        "The 'customer', 'domain' and 'userKey' can not be in the same query");
            } else {
                list.setCustomer(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            }
        } else if (equalsFilter.getAttribute().is("domain")) {
            if (null != list.getCustomer() || null != list.getUserKey()) {
                throw new InvalidAttributeValueException(
                        "The 'customer', 'domain' and 'userKey' can not be in the same query");
            } else {
                list.setDomain(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            }
        } else if (equalsFilter.getAttribute().is("userKey")) {
            if (null != list.getDomain() || null != list.getCustomer()) {
                throw new InvalidAttributeValueException(
                        "The 'customer', 'domain' and 'userKey' can not be in the same query");
            } else {
                list.setUserKey(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            }
        } else {
            String filedName = equalsFilter.getName();
            if (null != filedName) {
                return getStringBuilder(equalsFilter.getAttribute(), '=', null, filedName);
            } else {
                // Warning: not supported field name
                throw new InvalidAttributeValueException("");
            }
        }

        return null;
    }

    @Override
    public StringBuilder visitExtendedFilter(final Directory.Groups.List list, final Filter filter) {
        throw getException();
    }

    @Override
    public StringBuilder visitGreaterThanFilter(
            final Directory.Groups.List list, final GreaterThanFilter greaterThanFilter) {

        throw getException();
    }

    @Override
    public StringBuilder visitGreaterThanOrEqualFilter(
            final Directory.Groups.List list, final GreaterThanOrEqualFilter greaterThanOrEqualFilter) {

        throw getException();
    }

    @Override
    public StringBuilder visitLessThanFilter(
            final Directory.Groups.List list, final LessThanFilter lessThanFilter) {

        throw getException();
    }

    @Override
    public StringBuilder visitLessThanOrEqualFilter(
            final Directory.Groups.List list, final LessThanOrEqualFilter lessThanOrEqualFilter) {

        throw getException();
    }

    @Override
    public StringBuilder visitNotFilter(final Directory.Groups.List list, final NotFilter notFilter) {
        throw getException();
    }

    @Override
    public StringBuilder visitOrFilter(final Directory.Groups.List list, final OrFilter orFilter) {
        throw getException();
    }

    @Override
    public StringBuilder visitStartsWithFilter(
            final Directory.Groups.List list, final StartsWithFilter startsWithFilter) {

        throw getException();
    }

    @Override
    public StringBuilder visitEndsWithFilter(final Directory.Groups.List list, final EndsWithFilter endsWithFilter) {
        throw getException();
    }

    // /////////////
    //
    // GROUP
    //
    // /////////////
    public static ObjectClassInfo getObjectClassInfo() {
        // @formatter:off
        /* GROUP from https://devsite.googleplex.com/admin-sdk/directory/v1/reference/groups#resource
         * {
         * "kind": "admin#directory#group",
         * "id": string,
         * "etag": etag,
         * "email": string,
         * "name": string,
         * "directMembersCount": long,
         * "description": string,
         * "adminCreated": boolean,
         * "aliases": [
         * string
         * ],
         * "nonEditableAliases": [
         * string
         * ]
         * }
         */
        // @formatter:on
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(ObjectClass.GROUP_NAME);
        // email
        builder.addAttributeInfo(Name.INFO);
        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsUtil.ID_ATTR));
        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsUtil.NAME_ATTR));
        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsUtil.DESCRIPTION_ATTR));

        // Read-only
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.ADMIN_CREATED_ATTR, Boolean.TYPE).
                setUpdateable(false).setCreateable(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.ALIASES_ATTR).setUpdateable(false).
                setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.NON_EDITABLE_ALIASES_ATTR).
                setUpdateable(false).setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.DIRECT_MEMBERS_COUNT_ATTR, Long.TYPE).
                setUpdateable(false).setCreateable(false).build());

        // Virtual Attribute
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.MEMBERS_ATTR).setMultiValued(true).
                setReturnedByDefault(false).build());

        return builder.build();
    }

    // https://support.google.com/a/answer/33386
    public static Directory.Groups.Insert create(
            final Directory.Groups service, final AttributesAccessor attributes) {

        Group group = new Group();
        group.setEmail(GoogleAppsUtil.getName(attributes.getName()));
        // Optional
        group.setDescription(attributes.findString(GoogleAppsUtil.DESCRIPTION_ATTR));
        group.setName(attributes.findString(GoogleAppsUtil.NAME_ATTR));

        try {
            return service.insert(group).setFields(GoogleAppsUtil.ID_EMAIL_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Groups#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Groups.Aliases.Insert createGroupAlias(
            final Directory.Groups.Aliases service, final String groupKey, final String alias) {

        Alias content = new Alias();
        content.setAlias(alias);
        try {
            return service.insert(groupKey, content).setFields(GoogleAppsUtil.ID_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Aliases#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Groups.Aliases.Delete deleteGroupAlias(
            final Directory.Groups.Aliases service, final String groupKey, final String alias) {

        try {
            return service.delete(groupKey, alias);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Aliases#Delete");
            throw ConnectorException.wrap(e);
        }
    }

    private static void set(final AtomicReference<Group> content, final Consumer<Group> consumer) {
        if (content.get() == null) {
            content.set(new Group());
        }
        consumer.accept(content.get());
    }

    public static Directory.Groups.Patch update(
            final Directory.Groups service,
            final String groupKey,
            final AttributesAccessor attributes) {

        AtomicReference<Group> content = new AtomicReference<>();

        Optional.ofNullable(attributes.getName())
                .filter(email -> !StringUtil.isBlank(email.getNameValue()))
                .ifPresent(email -> set(content, g -> g.setEmail(email.getNameValue())));

        Optional.ofNullable(attributes.find(GoogleAppsUtil.NAME_ATTR))
                .flatMap(GoogleAppsUtil::getStringValue)
                .ifPresent(stringValue -> set(content, g -> g.setName(stringValue)));

        Optional.ofNullable(attributes.find(GoogleAppsUtil.DESCRIPTION_ATTR))
                .flatMap(GoogleAppsUtil::getStringValue)
                .ifPresent(stringValue -> set(content, g -> g.setDescription(stringValue)));

        if (null == content.get()) {
            return null;
        }
        try {
            return service.patch(groupKey, content.get()).setFields(GoogleAppsUtil.ID_EMAIL_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Groups#Patch");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Groups.Update update(
            final Directory.Groups service,
            final String groupKey,
            final Set<AttributeDelta> modifications) {

        if (AttributeDeltaUtil.getUidAttributeDelta(modifications) != null
                || AttributeDeltaUtil.getAttributeDeltaForName(modifications) != null) {

            throw new IllegalArgumentException("Do not perform rename via updateDelta, use standard update");
        }

        AtomicReference<Group> content = new AtomicReference<>();

        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.NAME_ATTR, modifications))
                .flatMap(GoogleAppsUtil::getStringValue)
                .ifPresent(stringValue -> set(content, g -> g.setName(stringValue)));

        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.DESCRIPTION_ATTR, modifications))
                .flatMap(GoogleAppsUtil::getStringValue)
                .ifPresent(stringValue -> set(content, g -> g.setDescription(stringValue)));

        if (null == content.get()) {
            return null;
        }
        try {
            return service.update(groupKey, content.get()).setFields(GoogleAppsUtil.ID_EMAIL_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Groups#update");
            throw ConnectorException.wrap(e);
        }
    }

    public static Set<String> listGroups(final Directory.Groups service, final String userKey, final String domain) {
        final Set<String> result = CollectionUtil.newCaseInsensitiveSet();
        try {
            Directory.Groups.List request = service.list();
            request.setUserKey(userKey);
            request.setFields("groups/email");
            // 400 Bad Request if the Customer(my_customer or exact value) is set, only domain-userKey combination 
            // allowed. request.setCustomer(MY_CUSTOMER_ID);
            request.setDomain(domain);

            String nextPageToken;
            do {
                nextPageToken = execute(request, new RequestResultHandler<Directory.Groups.List, Groups, String>() {

                    @Override
                    public String handleResult(final Directory.Groups.List request, final Groups value) {
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

    public static ConnectorObject fromGroup(
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
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.NAME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.NAME_ATTR, group.getName()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.EMAIL_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.EMAIL_ATTR, group.getEmail()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.DESCRIPTION_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.DESCRIPTION_ATTR, group.getDescription()));
        }

        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.ADMIN_CREATED_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.ADMIN_CREATED_ATTR, group.getAdminCreated()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.ALIASES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.ALIASES_ATTR, group.getAliases()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.NON_EDITABLE_ALIASES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.NON_EDITABLE_ALIASES_ATTR, group.getNonEditableAliases()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.DIRECT_MEMBERS_COUNT_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.DIRECT_MEMBERS_COUNT_ATTR, group.getDirectMembersCount()));
        }

        // Expensive to get
        if (null != attributesToGet && attributesToGet.contains(GoogleAppsUtil.MEMBERS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.MEMBERS_ATTR, MembersHandler.listMembers(service, group.getId(), null)));
        }

        return builder.build();
    }
}
