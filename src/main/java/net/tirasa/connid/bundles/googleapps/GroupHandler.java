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

import com.google.api.services.admin.directory.Directory;
import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.admin.directory.model.Member;
import com.google.common.base.CharMatcher;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import java.io.IOException;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.PredefinedAttributeInfos;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
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
    public static ObjectClassInfo getGroupClassInfo() {
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
        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsUtil.NAME_ATTR));
        builder.addAttributeInfo(PredefinedAttributeInfos.DESCRIPTION);

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

    public static ObjectClassInfo getMemberClassInfo() {
        // @formatter:off
        /*
         * {
         * }
         */
        // @formatter:on
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(GoogleAppsUtil.MEMBER.getObjectClassValue());
        builder.addAttributeInfo(AttributeInfoBuilder.define(Name.NAME).setUpdateable(false).
                setCreateable(false)/* .setRequired(true) */.build());

        // optional
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.GROUP_KEY_ATTR).setUpdateable(false).
                setRequired(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.EMAIL_ATTR).setUpdateable(false).
                setRequired(true).build());

        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsUtil.ROLE_ATTR));
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.TYPE_ATTR).setUpdateable(false).
                setCreateable(false).build());

        return builder.build();
    }

    // https://support.google.com/a/answer/33386
    public static Directory.Groups.Insert createGroup(
            final Directory.Groups groups, final AttributesAccessor attributes) {

        Group group = new Group();
        group.setEmail(GoogleAppsUtil.getName(attributes.getName()));
        // Optional
        group.setDescription(attributes.findString(PredefinedAttributes.DESCRIPTION));
        group.setName(attributes.findString(GoogleAppsUtil.NAME_ATTR));

        try {
            return groups.insert(group).setFields(GoogleAppsUtil.ID_EMAIL_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Groups#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Groups.Patch updateGroup(
            final Directory.Groups groups, final String groupKey, final AttributesAccessor attributes) {

        Group group = null;

        Name email = attributes.getName();
        if (email != null) {
            String stringValue = GoogleAppsUtil.getStringValueWithDefault(email, null);
            if (null != stringValue) {
                if (StringUtil.isBlank(stringValue)) {
                    throw new InvalidAttributeValueException(
                            "Invalid attribute '__NAME__'. The group's email address. "
                            + "Can not be blank when updating a group.");
                }
                group = new Group();
                group.setEmail(stringValue);
            }
        }

        Attribute description = attributes.find(PredefinedAttributes.DESCRIPTION);
        if (null != description) {
            String stringValue = GoogleAppsUtil.getStringValueWithDefault(description, null);
            if (null != stringValue) {
                if (null == group) {
                    group = new Group();
                }
                group.setDescription(stringValue);
            }
        }
        Attribute name = attributes.find(GoogleAppsUtil.NAME_ATTR);
        if (null != name) {
            String stringValue = GoogleAppsUtil.getStringValueWithDefault(name, null);
            if (null != stringValue) {
                if (null == group) {
                    group = new Group();
                }
                group.setName(stringValue);
            }
        }

        if (null == group) {
            return null;
        }
        try {
            return groups.patch(groupKey, group).setFields(GoogleAppsUtil.ID_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Groups#Patch");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Members.Insert createMember(
            final Directory.Members service, final AttributesAccessor attributes) {

        String groupKey = attributes.findString(GoogleAppsUtil.GROUP_KEY_ATTR);
        if (StringUtil.isBlank(groupKey)) {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'groupKey'. "
                    + "Identifies the group in the API request. Required when creating a Member.");
        }

        String memberKey = attributes.findString(GoogleAppsUtil.EMAIL_ATTR);
        if (StringUtil.isBlank(memberKey)) {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'memberKey'. "
                    + "Identifies the group member in the API request. Required when creating a Member.");
        }
        String role = attributes.findString(GoogleAppsUtil.ROLE_ATTR);

        return createMember(service, groupKey, memberKey, role);
    }

    public static Directory.Members.Insert createMember(
            final Directory.Members service, final String groupKey, final String memberKey, final String role) {

        Member content = new Member();
        content.setEmail(memberKey);
        if (StringUtil.isBlank(role)) {
            content.setRole("MEMBER");
        } else {
            // OWNER. MANAGER. MEMBER.
            content.setRole(role);
        }
        try {
            return service.insert(groupKey, content).setFields(GoogleAppsUtil.EMAIL_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Members#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Members.Patch updateMembers(
            final Directory.Members service, final String groupKey, final String memberKey, final String role) {

        Member content = new Member();
        content.setEmail(memberKey);

        if (StringUtil.isBlank(role)) {
            content.setRole("MEMBER");
        } else {
            // OWNER. MANAGER. MEMBER.
            content.setRole(role);
        }
        try {
            return service.patch(groupKey, memberKey, content).setFields(GoogleAppsUtil.EMAIL_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Members#Update");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Members.Delete deleteMembers(
            final Directory.Members service, final String groupKey, final String memberKey) {

        try {
            return service.delete(groupKey, memberKey);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Members#Delete");
            throw ConnectorException.wrap(e);
        }
    }

    public static ConnectorObject fromMember(final String groupKey, final Member content) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(GoogleAppsUtil.MEMBER);

        Uid uid = generateMemberId(groupKey, content);
        builder.setUid(uid);
        builder.setName(uid.getUidValue());

        builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.GROUP_KEY_ATTR, content.getId()));
        builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.EMAIL_ATTR, content.getEmail()));
        builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.ROLE_ATTR, content.getRole()));
        builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.TYPE_ATTR, content.getType()));

        return builder.build();
    }

    public static Uid generateMemberId(final String groupKey, final Member content) {
        String memberName = groupKey + '/' + content.getEmail();

        Uid uid;
        if (null != content.getEtag()) {
            uid = new Uid(memberName, content.getEtag());
        } else {
            uid = new Uid(memberName);
        }
        return uid;
    }
}
