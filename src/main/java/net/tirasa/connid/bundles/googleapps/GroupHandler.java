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
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterVisitor;
import org.identityconnectors.framework.common.objects.filter.GreaterThanFilter;
import org.identityconnectors.framework.common.objects.filter.GreaterThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanFilter;
import org.identityconnectors.framework.common.objects.filter.LessThanOrEqualFilter;
import org.identityconnectors.framework.common.objects.filter.NotFilter;
import org.identityconnectors.framework.common.objects.filter.OrFilter;
import org.identityconnectors.framework.common.objects.filter.StartsWithFilter;

import com.google.api.services.admin.directory.Directory;
import com.google.api.services.admin.directory.model.Group;
import com.google.api.services.admin.directory.model.Member;

/**
 * A GroupHandler is a util class to cover all Group related operations.
 *
 * @author Laszlo Hordos
 */
public class GroupHandler implements FilterVisitor<Void, Directory.Groups.List> {

    /**
     * Setup logging for the {@link GroupHandler}.
     */
    private static final Log LOG = Log.getLog(GroupHandler.class);

    @Override
    public Void visitAndFilter(Directory.Groups.List list, AndFilter andFilter) {
        throw getException();
    }

    @Override
    public Void visitContainsFilter(Directory.Groups.List list, ContainsFilter containsFilter) {
        if (containsFilter.getAttribute().is(GoogleAppsConnector.MEMBERS_ATTR)) {
            list.setUserKey(containsFilter.getValue());
        } else {
            throw getException();
        }
        return null;
    }

    @Override
    public Void visitContainsAllValuesFilter(Directory.Groups.List list,
            ContainsAllValuesFilter containsAllValuesFilter) {
        throw getException();
    }

    protected RuntimeException getException() {
        return new UnsupportedOperationException(
                "Only EqualsFilter(['domain','customer','userKey']) and ContainsFilter('members') are supported");
    }

    @Override
    public Void visitEqualsFilter(Directory.Groups.List list, EqualsFilter equalsFilter) {
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
            throw getException();
        }

        return null;
    }

    @Override
    public Void visitExtendedFilter(Directory.Groups.List list, Filter filter) {
        throw getException();
    }

    @Override
    public Void visitGreaterThanFilter(Directory.Groups.List list,
            GreaterThanFilter greaterThanFilter) {
        throw getException();
    }

    @Override
    public Void visitGreaterThanOrEqualFilter(Directory.Groups.List list,
            GreaterThanOrEqualFilter greaterThanOrEqualFilter) {
        throw getException();
    }

    @Override
    public Void visitLessThanFilter(Directory.Groups.List list, LessThanFilter lessThanFilter) {
        throw getException();
    }

    @Override
    public Void visitLessThanOrEqualFilter(Directory.Groups.List list,
            LessThanOrEqualFilter lessThanOrEqualFilter) {
        throw getException();
    }

    @Override
    public Void visitNotFilter(Directory.Groups.List list, NotFilter notFilter) {
        throw getException();
    }

    @Override
    public Void visitOrFilter(Directory.Groups.List list, OrFilter orFilter) {
        throw getException();
    }

    @Override
    public Void visitStartsWithFilter(Directory.Groups.List list, StartsWithFilter startsWithFilter) {
        throw getException();
    }

    @Override
    public Void visitEndsWithFilter(Directory.Groups.List list, EndsWithFilter endsWithFilter) {
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
        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsConnector.NAME_ATTR));
        builder.addAttributeInfo(PredefinedAttributeInfos.DESCRIPTION);

        // Read-only
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.ADMIN_CREATED_ATTR, Boolean.TYPE)
                .setUpdateable(false).setCreateable(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.ALIASES_ATTR).setUpdateable(false)
                .setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.NON_EDITABLE_ALIASES_ATTR)
                .setUpdateable(false).setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.DIRECT_MEMBERS_COUNT_ATTR, Long.TYPE)
                .setUpdateable(false).setCreateable(false).build());

        // Virtual Attribute
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.MEMBERS_ATTR).setMultiValued(true)
                .setReturnedByDefault(false).build());

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
        builder.setType(GoogleAppsConnector.MEMBER.getObjectClassValue());
        builder.addAttributeInfo(AttributeInfoBuilder.define(Name.NAME).setUpdateable(false)
                .setCreateable(false)/* .setRequired(true) */.build());

        // optional
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.GROUP_KEY_ATTR).setUpdateable(false)
                /* .setCreateable(false) */.setRequired(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.EMAIL_ATTR).setUpdateable(false)
                /* .setCreateable(false) */.setRequired(true).build());

        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsConnector.ROLE_ATTR));
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.TYPE_ATTR).setUpdateable(false)
                .setCreateable(false).build());

        return builder.build();
    }

    // https://support.google.com/a/answer/33386
    public static Directory.Groups.Insert createGroup(Directory.Groups groups,
            AttributesAccessor attributes) {
        Group group = new Group();
        group.setEmail(GoogleAppsUtil.getName(attributes.getName()));
        // Optional
        group.setDescription(attributes.findString(PredefinedAttributes.DESCRIPTION));
        group.setName(attributes.findString(GoogleAppsConnector.NAME_ATTR));

        try {
            return groups.insert(group).setFields(GoogleAppsConnector.ID_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Groups#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Groups.Patch updateGroup(Directory.Groups groups, String groupKey,
            AttributesAccessor attributes) {
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
        Attribute name = attributes.find(GoogleAppsConnector.NAME_ATTR);
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
            return groups.patch(groupKey, group).setFields(GoogleAppsConnector.ID_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Groups#Patch");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Members.Insert createMember(Directory.Members service,
            AttributesAccessor attributes) {
        String groupKey = attributes.findString(GoogleAppsConnector.GROUP_KEY_ATTR);
        if (StringUtil.isBlank(groupKey)) {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'groupKey'. "
                    + "Identifies the group in the API request. Required when creating a Member.");
        }

        String memberKey = attributes.findString(GoogleAppsConnector.EMAIL_ATTR);
        if (StringUtil.isBlank(memberKey)) {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'memberKey'. "
                    + "Identifies the group member in the API request. Required when creating a Member.");
        }
        String role = attributes.findString(GoogleAppsConnector.ROLE_ATTR);

        return createMember(service, groupKey, memberKey, role);
    }

    public static Directory.Members.Insert createMember(Directory.Members service, String groupKey,
            String memberKey, String role) {

        Member content = new Member();
        content.setEmail(memberKey);
        if (StringUtil.isBlank(role)) {
            content.setRole("MEMBER");
        } else {
            // OWNER. MANAGER. MEMBER.
            content.setRole(role);
        }
        try {
            return service.insert(groupKey, content).setFields(GoogleAppsConnector.EMAIL_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Members#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Members.Patch updateMembers(Directory.Members service, String groupKey,
            String memberKey, String role) {
        Member content = new Member();
        content.setEmail(memberKey);

        if (StringUtil.isBlank(role)) {
            content.setRole("MEMBER");
        } else {
            // OWNER. MANAGER. MEMBER.
            content.setRole(role);
        }
        try {
            return service.patch(groupKey, memberKey, content).setFields(GoogleAppsConnector.EMAIL_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Members#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Members.Delete deleteMembers(Directory.Members service,
            String groupKey, String memberKey) {
        try {
            return service.delete(groupKey, memberKey);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Members#Delete");
            throw ConnectorException.wrap(e);
        }
    }

    public static ConnectorObject fromMember(String groupKey, Member content) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(GoogleAppsConnector.MEMBER);

        Uid uid = generateMemberId(groupKey, content);
        builder.setUid(uid);
        builder.setName(uid.getUidValue());

        builder.addAttribute(AttributeBuilder.build(GoogleAppsConnector.GROUP_KEY_ATTR, content.getId()));
        builder.addAttribute(AttributeBuilder.build(GoogleAppsConnector.EMAIL_ATTR, content.getEmail()));
        builder.addAttribute(AttributeBuilder.build(GoogleAppsConnector.ROLE_ATTR, content.getRole()));
        builder.addAttribute(AttributeBuilder.build(GoogleAppsConnector.TYPE_ATTR, content.getType()));

        return builder.build();
    }

    public static Uid generateMemberId(String groupKey, Member content) {
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
