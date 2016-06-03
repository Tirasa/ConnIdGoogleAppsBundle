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
import java.util.Map;
import java.util.Set;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.PredefinedAttributeInfos;
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
import com.google.api.services.admin.directory.model.Alias;
import com.google.api.services.admin.directory.model.User;
import com.google.api.services.admin.directory.model.UserName;
import com.google.api.services.admin.directory.model.UserPhoto;
import com.google.common.base.CharMatcher;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;

/**
 *
 * @author Laszlo Hordos
 */
public class UserHandler implements FilterVisitor<StringBuilder, Directory.Users.List> {

    /**
     * Setup logging for the {@link UserHandler}.
     */
    private static final Log LOG = Log.getLog(UserHandler.class);

    private static final Map<String, String> NAME_DICTIONARY;

    private static final Set<String> S;

    private static final Set<String> SW;

    static {
        Map<String, String> dictionary = CollectionUtil.newCaseInsensitiveMap();
        dictionary.put(Uid.NAME, Uid.NAME);
        dictionary.put(GoogleAppsConnector.EMAIL_ATTR, GoogleAppsConnector.EMAIL_ATTR);
        dictionary.put(Name.NAME, GoogleAppsConnector.EMAIL_ATTR);
        dictionary.put(GoogleAppsConnector.GIVEN_NAME_ATTR, GoogleAppsConnector.GIVEN_NAME_ATTR);
        dictionary.put(GoogleAppsConnector.FAMILY_NAME_ATTR, GoogleAppsConnector.FAMILY_NAME_ATTR);
        dictionary.put(GoogleAppsConnector.IS_ADMIN_ATTR, GoogleAppsConnector.IS_ADMIN_ATTR);
        dictionary.put(GoogleAppsConnector.IS_DELEGATED_ADMIN_ATTR, GoogleAppsConnector.IS_DELEGATED_ADMIN_ATTR);
        dictionary.put("isSuspended", "isSuspended");
        dictionary.put("im", "im");
        dictionary.put("externalId", "externalId");
        dictionary.put("manager", "manager");
        dictionary.put("managerId", "managerId");
        dictionary.put("directManager", "directManager");
        dictionary.put("directManagerId", "directManagerId");
        dictionary.put("address", "address");
        dictionary.put("addressPoBox", "addressPoBox");
        dictionary.put("address/poBox", "addressPoBox");
        dictionary.put("addressExtended", "addressExtended");
        dictionary.put("address/extended", "addressExtended");
        dictionary.put("addressStreet", "addressStreet");
        dictionary.put("address/street", "addressStreet");
        dictionary.put("addressLocality", "addressLocality");
        dictionary.put("address/locality", "addressLocality");
        dictionary.put("addressRegion", "addressRegion");
        dictionary.put("address/region", "addressRegion");
        dictionary.put("addressPostalCode", "addressPostalCode");
        dictionary.put("address/postalCode", "addressPostalCode");
        dictionary.put("addressCountry", "addressCountry");
        dictionary.put("address/country", "addressCountry");
        dictionary.put("orgName", "orgName");
        dictionary.put("organizations/name", "orgName");
        NAME_DICTIONARY = dictionary;

        Set<String> s = CollectionUtil.newCaseInsensitiveSet();
        s.add(Name.NAME);
        s.add("email");
        s.add("givenName");
        s.add("familyName");
        SW = CollectionUtil.newCaseInsensitiveSet();
        SW.addAll(s);

        s.add("im");
        s.add("externalId");
        s.add("address");
        s.add("addressPoBox");
        s.add("addressExtended");
        s.add("addressStreet");
        s.add("addressLocality");
        s.add("addressRegion");
        s.add("addressPostalCode");
        s.add("addressCountry");
        s.add("orgName");
        s.add("orgTitle");
        s.add("orgDepartment");
        s.add("orgDescription");
        s.add("orgCostCenter");
        S = s;
    }

    private static final Escaper STRING_ESCAPER = Escapers.builder().addEscape('\'', "\\'").build();

    @Override
    public StringBuilder visitAndFilter(Directory.Users.List list, AndFilter andFilter) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Filter filter : andFilter.getFilters()) {
            StringBuilder sb = filter.accept(this, list);
            if (null != sb) {
                if (!first) {
                    builder.append(' ');
                } else {
                    first = false;
                }
                builder.append(sb);
            }
        }
        return builder;
    }

    @Override
    public StringBuilder visitContainsFilter(Directory.Users.List list, ContainsFilter filter) {
        String filedName = NAME_DICTIONARY.get(filter.getName());
        if (null != filedName && S.contains(filedName)) {
            return getStringBuilder(filter.getAttribute(), ':', null, filedName);
        } else {
            // Warning: not supported field name
            throw new InvalidAttributeValueException("");
        }
    }

    /**
     * Surround with single quotes ' if the query contains whitespace. Escape
     * single quotes in queries with \', for example 'Valentine\'s Day'.
     *
     * @param attribute
     * @param operator
     * @param postfix
     * @param filedName
     * @return
     */
    protected StringBuilder getStringBuilder(Attribute attribute, char operator, Character postfix, String filedName) {
        StringBuilder builder = new StringBuilder();
        builder.append(filedName).append(operator);
        String stringValue = AttributeUtil.getAsStringValue(attribute);
        if (StringUtil.isNotBlank(stringValue)) {
            stringValue = STRING_ESCAPER.escape(stringValue);
            if (CharMatcher.WHITESPACE.matchesAnyOf(stringValue)) {
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
    public StringBuilder visitStartsWithFilter(Directory.Users.List list, StartsWithFilter filter) {
        String filedName = NAME_DICTIONARY.get(filter.getName());
        if (null != filedName && SW.contains(filedName)) {
            return getStringBuilder(filter.getAttribute(), ':', '*', filedName);
        } else {
            // Warning: not supported field name
            throw new InvalidAttributeValueException("");
        }
    }

    @Override
    public StringBuilder visitEqualsFilter(Directory.Users.List list, EqualsFilter equalsFilter) {
        if (AttributeUtil.namesEqual(equalsFilter.getName(), "customer")) {
            if (null != list.getDomain()) {
                throw new InvalidAttributeValueException(
                        "The 'customer' and 'domain' can not be in the same query");
            } else {
                list.setCustomer(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            }
        } else if (AttributeUtil.namesEqual(equalsFilter.getName(), "domain")) {
            if (null != list.getCustomer()) {
                throw new InvalidAttributeValueException(
                        "The 'customer' and 'domain' can not be in the same query");
            } else {
                list.setDomain(AttributeUtil.getStringValue(equalsFilter.getAttribute()));
            }
        } else {
            String filedName = NAME_DICTIONARY.get(equalsFilter.getName());
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
    public StringBuilder visitContainsAllValuesFilter(Directory.Users.List list,
            ContainsAllValuesFilter filter) {
        return null;
    }

    @Override
    public StringBuilder visitExtendedFilter(Directory.Users.List list, Filter filter) {
        return null;
    }

    @Override
    public StringBuilder visitGreaterThanFilter(Directory.Users.List list, GreaterThanFilter filter) {
        return null;
    }

    @Override
    public StringBuilder visitGreaterThanOrEqualFilter(Directory.Users.List list,
            GreaterThanOrEqualFilter filter) {
        return null;
    }

    @Override
    public StringBuilder visitLessThanFilter(Directory.Users.List list, LessThanFilter filter) {
        return null;
    }

    @Override
    public StringBuilder visitLessThanOrEqualFilter(Directory.Users.List list, LessThanOrEqualFilter filter) {
        return null;
    }

    @Override
    public StringBuilder visitNotFilter(Directory.Users.List list, NotFilter filter) {
        return null;
    }

    @Override
    public StringBuilder visitOrFilter(Directory.Users.List list, OrFilter filter) {
        return null;
    }

    @Override
    public StringBuilder visitEndsWithFilter(Directory.Users.List list, EndsWithFilter filter) {
        return null;
    }

    // /////////////
    //
    // USER https://developers.google.com/admin-sdk/directory/v1/reference/users
    //
    // /////////////
    public static ObjectClassInfo getUserClassInfo() {
        // @formatter:off
        /*
         * {
         * "kind": "admin#directory#user",
         * "id": string,
         * "etag": etag,
         * "primaryEmail": string,
         * "name": {
         * "givenName": string,
         * "familyName": string,
         * "fullName": string
         * },
         * "isAdmin": boolean,
         * "isDelegatedAdmin": boolean,
         * "lastLoginTime": datetime,
         * "creationTime": datetime,
         * "deletionTime": datetime,
         * "agreedToTerms": boolean,
         * "password": string,
         * "hashFunction": string,
         * "suspended": boolean,
         * "suspensionReason": string,
         * "changePasswordAtNextLogin": boolean,
         * "ipWhitelisted": boolean,
         * "ims": [
         * {
         * "type": string,
         * "customType": string,
         * "protocol": string,
         * "customProtocol": string,
         * "im": string,
         * "primary": boolean
         * }
         * ],
         * "emails": [
         * {
         * "address": string,
         * "type": string,
         * "customType": string,
         * "primary": boolean
         * }
         * ],
         * "externalIds": [
         * {
         * "value": string,
         * "type": string,
         * "customType": string
         * }
         * ],
         * "relations": [
         * {
         * "value": string,
         * "type": string,
         * "customType": string
         * }
         * ],
         * "addresses": [
         * {
         * "type": string,
         * "customType": string,
         * "sourceIsStructured": boolean,
         * "formatted": string,
         * "poBox": string,
         * "extendedAddress": string,
         * "streetAddress": string,
         * "locality": string,
         * "region": string,
         * "postalCode": string,
         * "country": string,
         * "primary": boolean,
         * "countryCode": string
         * }
         * ],
         * "organizations": [
         * {
         * "name": string,
         * "title": string,
         * "primary": boolean,
         * "type": string,
         * "customType": string,
         * "department": string,
         * "symbol": string,
         * "location": string,
         * "description": string,
         * "domain": string,
         * "costCenter": string
         * }
         * ],
         * "phones": [
         * {
         * "value": string,
         * "primary": boolean,
         * "type": string,
         * "customType": string
         * }
         * ],
         * "aliases": [
         * string
         * ],
         * "nonEditableAliases": [
         * string
         * ],
         * "customerId": string,
         * "orgUnitPath": string,
         * "isMailboxSetup": boolean,
         * "includeInGlobalAddressList": boolean,
         * "thumbnailPhotoUrl": string
         * }
         */
        // @formatter:on
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();

        // primaryEmail
        builder.addAttributeInfo(Name.INFO);

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.GIVEN_NAME_ATTR).setRequired(true)
                .build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.FAMILY_NAME_ATTR).setRequired(true)
                .build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.FULL_NAME_ATTR).setUpdateable(false)
                .setCreateable(false).build());

        // Virtual attribute Modify supported
        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsConnector.IS_ADMIN_ATTR, Boolean.TYPE));

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.IS_DELEGATED_ADMIN_ATTR, Boolean.TYPE)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.LAST_LOGIN_TIME_ATTR).setUpdateable(
                false).setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.CREATION_TIME_ATTR).setUpdateable(
                false).setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.AGREED_TO_TERMS_ATTR, Boolean.TYPE)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(OperationalAttributes.PASSWORD_NAME,
                GuardedString.class).setRequired(true).setReadable(false).setReturnedByDefault(
                false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsConnector.SUSPENDED_ATTR, Boolean.class));
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.SUSPENSION_REASON_ATTR).
                setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.build(
                GoogleAppsConnector.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR, Boolean.class));
        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsConnector.IP_WHITELISTED_ATTR, Boolean.class));

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.ALIASES_ATTR).setUpdateable(false)
                .setCreateable(false).setMultiValued(true).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.NON_EDITABLE_ALIASES_ATTR)
                .setUpdateable(false).setCreateable(false).setMultiValued(true).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.CUSTOMER_ID_ATTR).setUpdateable(false)
                .setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsConnector.ORG_UNIT_PATH_ATTR));

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.IS_MAILBOX_SETUP_ATTR, Boolean.class)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(
                AttributeInfoBuilder.build(GoogleAppsConnector.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, Boolean.class));

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.THUMBNAIL_PHOTO_URL_ATTR)
                .setUpdateable(false).setCreateable(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.DELETION_TIME_ATTR).
                setUpdateable(
                        false).setCreateable(false).build());

        // Virtual Attribute
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsConnector.PHOTO_ATTR, byte[].class).
                setReturnedByDefault(false).build());

        builder.addAttributeInfo(PredefinedAttributeInfos.GROUPS);

        return builder.build();
    }

    // https://support.google.com/a/answer/33386
    public static Directory.Users.Insert createUser(Directory.Users users, AttributesAccessor attributes) {
        User user = new User();
        user.setPrimaryEmail(GoogleAppsUtil.getName(attributes.getName()));
        GuardedString password = attributes.getPassword();
        if (null != password) {
            user.setPassword(SecurityUtil.decrypt(password));
        } else {
            throw new InvalidAttributeValueException("Missing required attribute '__PASSWORD__'");
        }

        user.setName(new UserName());
        // givenName The user's first name. Required when creating a user
        // account.
        String givenName = attributes.findString(GoogleAppsConnector.GIVEN_NAME_ATTR);
        if (StringUtil.isNotBlank(givenName)) {
            user.getName().setGivenName(givenName);
        } else {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'givenName'."
                    + "The user's first name. Required when creating a user account.");
        }

        // familyName The user's last name. Required when creating a user account.
        String familyName = attributes.findString(GoogleAppsConnector.FAMILY_NAME_ATTR);
        if (StringUtil.isNotBlank(familyName)) {
            user.getName().setFamilyName(familyName);
        } else {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'familyName'."
                    + "The user's last name. Required when creating a user account.");
        }

        // Optional
        user.setSuspended(attributes.findBoolean(GoogleAppsConnector.SUSPENDED_ATTR));
        user.setChangePasswordAtNextLogin(
                attributes.findBoolean(GoogleAppsConnector.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR));
        user.setIpWhitelisted(attributes.findBoolean(GoogleAppsConnector.IP_WHITELISTED_ATTR));
        user.setOrgUnitPath(attributes.findString(GoogleAppsConnector.ORG_UNIT_PATH_ATTR));
        user.setIncludeInGlobalAddressList(
                attributes.findBoolean(GoogleAppsConnector.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR));

        try {
            return users.insert(user).setFields(GoogleAppsConnector.ID_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Groups#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Patch updateUser(
            Directory.Users users, String groupKey, AttributesAccessor attributes) {

        User content = null;

        Name email = attributes.getName();
        if (email != null && email.getNameValue() != null) {
            content = new User();
            content.setPrimaryEmail(email.getNameValue());
        }

        Attribute givenName = attributes.find(GoogleAppsConnector.GIVEN_NAME_ATTR);
        if (null != givenName) {
            String stringValue = GoogleAppsUtil.getStringValueWithDefault(givenName, null);
            if (null != stringValue) {
                if (null == content) {
                    content = new User();
                }
                content.setName(new UserName());
                content.getName().setGivenName(stringValue);
            }
        }

        Attribute familyName = attributes.find(GoogleAppsConnector.FAMILY_NAME_ATTR);
        if (null != familyName) {
            String stringValue = GoogleAppsUtil.getStringValueWithDefault(familyName, null);
            if (null != stringValue) {
                if (null == content) {
                    content = new User();
                }
                if (null == content.getName()) {
                    content.setName(new UserName());
                }
                content.getName().setFamilyName(stringValue);
            }
        }

        GuardedString password = attributes.getPassword();
        if (null != password) {
            if (null == content) {
                content = new User();
            }
            content.setPassword(SecurityUtil.decrypt(password));
        }

        Attribute suspended = attributes.find(GoogleAppsConnector.SUSPENDED_ATTR);
        if (null != suspended) {
            Boolean booleanValue = GoogleAppsUtil.getBooleanValueWithDefault(suspended, null);
            if (null != booleanValue) {
                if (null == content) {
                    content = new User();
                }
                content.setSuspended(booleanValue);
            }
        }

        Attribute changePasswordAtNextLogin = attributes.find(GoogleAppsConnector.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR);
        if (null != changePasswordAtNextLogin) {
            Boolean booleanValue =
                    GoogleAppsUtil.getBooleanValueWithDefault(changePasswordAtNextLogin, null);
            if (null != booleanValue) {
                if (null == content) {
                    content = new User();
                }
                content.setChangePasswordAtNextLogin(booleanValue);
            }
        }

        Attribute ipWhitelisted = attributes.find(GoogleAppsConnector.IP_WHITELISTED_ATTR);
        if (null != ipWhitelisted) {
            Boolean booleanValue = GoogleAppsUtil.getBooleanValueWithDefault(ipWhitelisted, null);
            if (null != booleanValue) {
                if (null == content) {
                    content = new User();
                }
                content.setIpWhitelisted(booleanValue);
            }
        }

        // Maps --> Removed

        Attribute orgUnitPath = attributes.find(GoogleAppsConnector.ORG_UNIT_PATH_ATTR);
        if (null != orgUnitPath) {
            String stringValue = GoogleAppsUtil.getStringValueWithDefault(orgUnitPath, null);
            if (null != stringValue) {
                if (null == content) {
                    content = new User();
                }
                content.setOrgUnitPath(stringValue);
            }
        }

        Attribute includeInGlobalAddressList = attributes.find(GoogleAppsConnector.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR);
        if (null != includeInGlobalAddressList) {
            Boolean booleanValue =
                    GoogleAppsUtil.getBooleanValueWithDefault(includeInGlobalAddressList, null);
            if (null != booleanValue) {
                if (null == content) {
                    content = new User();
                }
                content.setIncludeInGlobalAddressList(booleanValue);
            }
        }

        if (null == content) {
            return null;
        }
        try {
            return users.patch(groupKey, content).setFields(GoogleAppsConnector.ID_ETAG);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Groups#Patch");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Photos.Update createUpdateUserPhoto(
            Directory.Users.Photos service, String userKey, byte[] data) {

        UserPhoto content = new UserPhoto();
        // Required
        content.setPhotoData(com.google.api.client.util.Base64.encodeBase64URLSafeString(data));

        // @formatter:off
        /*
         * content.setPhotoData(com.google.api.client.util.Base64
         * .encodeBase64URLSafeString((byte[]) data.get("photoData")));
         * content.setHeight((Integer) data.get("height"));
         * content.setWidth((Integer) data.get("width"));
         *
         * // Allowed values are JPEG, PNG, GIF, BMP, TIFF,
         * content.setMimeType((String) data.get("mimeType"));
         */
        // @formatter:on
        try {
            return service.update(userKey, content).setFields(GoogleAppsConnector.ID_ATTR);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Aliases#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Aliases.Insert createUserAlias(
            Directory.Users.Aliases service, String userKey, String alias) {

        Alias content = new Alias();
        content.setAlias(alias);
        try {
            return service.insert(userKey, content).setFields(GoogleAppsConnector.ID_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Aliases#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Aliases.Delete deleteUserAlias(
            Directory.Users.Aliases service, String userKey, String alias) {

        try {
            return service.delete(userKey, alias);
            // } catch (HttpResponseException e){
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Aliases#Delete");
            throw ConnectorException.wrap(e);
        }
    }

}
