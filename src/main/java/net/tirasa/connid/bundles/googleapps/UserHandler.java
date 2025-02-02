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

import com.google.api.client.util.GenericData;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Alias;
import com.google.api.services.directory.model.Aliases;
import com.google.api.services.directory.model.User;
import com.google.api.services.directory.model.UserAddress;
import com.google.api.services.directory.model.UserExternalId;
import com.google.api.services.directory.model.UserIm;
import com.google.api.services.directory.model.UserName;
import com.google.api.services.directory.model.UserOrganization;
import com.google.api.services.directory.model.UserPhone;
import com.google.api.services.directory.model.UserPhoto;
import com.google.api.services.directory.model.UserRelation;
import com.google.common.base.CharMatcher;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import java.io.IOException;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
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
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
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
        dictionary.put(GoogleAppsUtil.EMAIL_ATTR, GoogleAppsUtil.EMAIL_ATTR);
        dictionary.put(Name.NAME, GoogleAppsUtil.EMAIL_ATTR);
        dictionary.put(GoogleAppsUtil.GIVEN_NAME_ATTR, GoogleAppsUtil.GIVEN_NAME_ATTR);
        dictionary.put(GoogleAppsUtil.FAMILY_NAME_ATTR, GoogleAppsUtil.FAMILY_NAME_ATTR);
        dictionary.put(GoogleAppsUtil.IS_ADMIN_ATTR, GoogleAppsUtil.IS_ADMIN_ATTR);
        dictionary.put(GoogleAppsUtil.IS_DELEGATED_ADMIN_ATTR, GoogleAppsUtil.IS_DELEGATED_ADMIN_ATTR);
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
    public StringBuilder visitAndFilter(final Directory.Users.List list, final AndFilter andFilter) {
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
    public StringBuilder visitContainsFilter(final Directory.Users.List list, final ContainsFilter filter) {
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
    protected StringBuilder getStringBuilder(
            final Attribute attribute,
            final char operator,
            final Character postfix,
            final String filedName) {

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
    public StringBuilder visitStartsWithFilter(final Directory.Users.List list, final StartsWithFilter filter) {
        String filedName = NAME_DICTIONARY.get(filter.getName());
        if (null != filedName && SW.contains(filedName)) {
            return getStringBuilder(filter.getAttribute(), ':', '*', filedName);
        } else {
            // Warning: not supported field name
            throw new InvalidAttributeValueException("");
        }
    }

    @Override
    public StringBuilder visitEqualsFilter(final Directory.Users.List list, final EqualsFilter equalsFilter) {
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
    public StringBuilder visitEqualsIgnoreCaseFilter(
            final Directory.Users.List list, final EqualsIgnoreCaseFilter equalsFilter) {

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
    public StringBuilder visitContainsAllValuesFilter(
            final Directory.Users.List list, final ContainsAllValuesFilter filter) {

        return null;
    }

    @Override
    public StringBuilder visitExtendedFilter(final Directory.Users.List list, final Filter filter) {
        return null;
    }

    @Override
    public StringBuilder visitGreaterThanFilter(
            final Directory.Users.List list, final GreaterThanFilter filter) {

        return null;
    }

    @Override
    public StringBuilder visitGreaterThanOrEqualFilter(
            final Directory.Users.List list, final GreaterThanOrEqualFilter filter) {

        return null;
    }

    @Override
    public StringBuilder visitLessThanFilter(final Directory.Users.List list, final LessThanFilter filter) {
        return null;
    }

    @Override
    public StringBuilder visitLessThanOrEqualFilter(
            final Directory.Users.List list, final LessThanOrEqualFilter filter) {

        return null;
    }

    @Override
    public StringBuilder visitNotFilter(final Directory.Users.List list, final NotFilter filter) {
        return null;
    }

    @Override
    public StringBuilder visitOrFilter(final Directory.Users.List list, final OrFilter orFilter) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Filter filter : orFilter.getFilters()) {
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
    public StringBuilder visitEndsWithFilter(final Directory.Users.List list, final EndsWithFilter filter) {
        return null;
    }

    // /////////////
    //
    // USER https://developers.google.com/admin-sdk/directory/v1/reference/users
    //
    // /////////////
    public static ObjectClassInfo getObjectClassInfo(final String customSchemasJSON) {
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();

        // primaryEmail
        builder.addAttributeInfo(Name.INFO);

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.ID_ATTR).setRequired(true)
                .build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.GIVEN_NAME_ATTR).setRequired(true)
                .build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.FAMILY_NAME_ATTR).setRequired(true)
                .build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.FULL_NAME_ATTR).setUpdateable(false)
                .setCreateable(false).build());

        // Virtual attribute Modify supported
        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsUtil.IS_ADMIN_ATTR, Boolean.TYPE));

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.IS_DELEGATED_ADMIN_ATTR, Boolean.TYPE)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.LAST_LOGIN_TIME_ATTR).setUpdateable(
                false).setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.CREATION_TIME_ATTR).setUpdateable(
                false).setCreateable(false).setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.AGREED_TO_TERMS_ATTR, Boolean.TYPE)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(OperationalAttributes.PASSWORD_NAME,
                GuardedString.class).setRequired(true).setReadable(false).setReturnedByDefault(
                false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.SUSPENSION_REASON_ATTR).
                setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.build(
                GoogleAppsUtil.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR, Boolean.class));
        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsUtil.IP_WHITELISTED_ATTR, Boolean.class));

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.IMS_ATTR, Map.class).
                setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.EMAILS_ATTR, Map.class)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.EXTERNAL_IDS_ATTR, Map.class)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.RELATIONS_ATTR, Map.class)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.ADDRESSES_ATTR, Map.class)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.ORGANIZATIONS_ATTR, Map.class)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.PHONES_ATTR, Map.class)
                .setMultiValued(true).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.ALIASES_ATTR).setUpdateable(false)
                .setCreateable(false).setMultiValued(true).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.NON_EDITABLE_ALIASES_ATTR)
                .setUpdateable(false).setCreateable(false).setMultiValued(true).build());

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.CUSTOMER_ID_ATTR).setUpdateable(false)
                .setCreateable(false).build());

        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsUtil.ORG_UNIT_PATH_ATTR));

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.IS_MAILBOX_SETUP_ATTR, Boolean.class)
                .setUpdateable(false).setCreateable(false).build());

        builder.addAttributeInfo(
                AttributeInfoBuilder.build(GoogleAppsUtil.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, Boolean.class));

        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.THUMBNAIL_PHOTO_URL_ATTR)
                .setUpdateable(false).setCreateable(false).build());
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.DELETION_TIME_ATTR).
                setUpdateable(
                        false).setCreateable(false).build());

        // Virtual Attribute
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.PHOTO_ATTR, byte[].class).
                setReturnedByDefault(false).build());

        builder.addAttributeInfo(PredefinedAttributeInfos.GROUPS);

        // custom schemas
        if (StringUtil.isNotBlank(customSchemasJSON)) {
            List<GoogleAppsCustomSchema> customSchemas = GoogleAppsUtil.extractCustomSchemas(customSchemasJSON);
            for (GoogleAppsCustomSchema customSchema : customSchemas) {
                if (customSchema.getType().equals("object")) {
                    // parse inner schemas
                    String basicName = customSchema.getName();
                    // manage only first level inner schemas
                    for (GoogleAppsCustomSchema innerSchema : customSchema.getInnerSchemas()) {
                        builder.addAttributeInfo(getAttributeInfoBuilder(basicName + "." + innerSchema.getName(),
                                innerSchema).build());
                    }
                } else {
                    LOG.warn("CustomSchema type {0} not allowed at this level", customSchema.getType());
                }
            }
        }

        return builder.build();
    }

    private static AttributeInfoBuilder getAttributeInfoBuilder(
            final String name,
            final GoogleAppsCustomSchema schema) {

        AttributeInfoBuilder attributeInfoBuilder = AttributeInfoBuilder.define(name);
        attributeInfoBuilder.setMultiValued(schema.getMultiValued());
        switch (schema.getType()) {
            case "string":
                attributeInfoBuilder.setType(String.class);
                break;
            case "boolean":
                attributeInfoBuilder.setType(Boolean.class);
                break;
            case "int":
                attributeInfoBuilder.setType(Integer.class);
                break;
            default:
        }
        return attributeInfoBuilder;
    }

    // https://support.google.com/a/answer/33386
    public static Directory.Users.Insert createUser(
            final Directory.Users service,
            final AttributesAccessor attributes,
            final String customSchemas) {

        User user = new User();
        user.setPrimaryEmail(GoogleAppsUtil.getName(attributes.getName()));
        GuardedString password = attributes.getPassword();
        if (null != password) {
            user.setPassword(SecurityUtil.decrypt(password));
        } else {
            throw new InvalidAttributeValueException("Missing required attribute '__PASSWORD__'");
        }

        user.setName(new UserName());
        // givenName The user's first name. Required when creating a user account.
        String givenName = attributes.findString(GoogleAppsUtil.GIVEN_NAME_ATTR);
        if (StringUtil.isNotBlank(givenName)) {
            user.getName().setGivenName(givenName);
        } else {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'givenName'."
                    + "The user's first name. Required when creating a user account.");
        }

        // familyName The user's last name. Required when creating a user account.
        String familyName = attributes.findString(GoogleAppsUtil.FAMILY_NAME_ATTR);
        if (StringUtil.isNotBlank(familyName)) {
            user.getName().setFamilyName(familyName);
        } else {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'familyName'."
                    + "The user's last name. Required when creating a user account.");
        }

        // Optional
        user.setEmails(attributes.findList(GoogleAppsUtil.EMAILS_ATTR));
        // complex attributes 
        user.setIms(buildObjs(attributes.findList(GoogleAppsUtil.IMS_ATTR), UserIm.class));
        user.setRelations(buildObjs(attributes.findList(GoogleAppsUtil.RELATIONS_ATTR), UserRelation.class));
        user.setAddresses(buildObjs(attributes.findList(GoogleAppsUtil.ADDRESSES_ATTR), UserAddress.class));
        user.setOrganizations(
                buildObjs(attributes.findList(GoogleAppsUtil.ORGANIZATIONS_ATTR), UserOrganization.class));
        user.setPhones(buildObjs(attributes.findList(GoogleAppsUtil.PHONES_ATTR), UserPhone.class));
        user.setExternalIds(buildObjs(Optional.ofNullable(attributes.findList(
                GoogleAppsUtil.EXTERNAL_IDS_ATTR)).orElse(null), UserExternalId.class));

        Optional.ofNullable(attributes.findBoolean(OperationalAttributes.ENABLE_NAME)).
                ifPresent(enable -> user.setSuspended(!enable));
        user.setChangePasswordAtNextLogin(
                attributes.findBoolean(GoogleAppsUtil.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR));
        user.setIpWhitelisted(attributes.findBoolean(GoogleAppsUtil.IP_WHITELISTED_ATTR));
        user.setOrgUnitPath(attributes.findString(GoogleAppsUtil.ORG_UNIT_PATH_ATTR));
        user.setIncludeInGlobalAddressList(
                attributes.findBoolean(GoogleAppsUtil.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR));

        // customSchemas
        if (StringUtil.isNotBlank(customSchemas)) {
            user.setCustomSchemas(buildCustomAttrs(customSchemas, attributes));
        }

        try {
            return service.insert(user).setFields(GoogleAppsUtil.ID_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Groups#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    private static <T extends GenericData> List<T> buildObjs(final List<Object> values, final Class<T> clazz) {
        return values == null
                ? null
                : values.stream().map(v -> {
                    T obj = null;
                    if (v instanceof String) {
                        obj = GoogleAppsUtil.extract(v.toString(), clazz);
                    } else {
                        LOG.error("Unable to build obj from object {0}", v);
                    }
                    return obj;
                }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static Object getValueByType(
            final GoogleAppsCustomSchema innerSchema,
            final AttributesAccessor attributes,
            final String innerSchemaName) {

        return innerSchema.getMultiValued()
                ? attributes.findStringList(innerSchemaName)
                : attributes.findString(innerSchemaName);
    }

    private static Map<String, Map<String, Object>> buildCustomAttrs(
            final String customSchemas, final AttributesAccessor attributes) {

        List<GoogleAppsCustomSchema> schemas = GoogleAppsUtil.extractCustomSchemas(customSchemas);
        Map<String, Map<String, Object>> attrsToAdd = new HashMap<>();
        for (GoogleAppsCustomSchema customSchema : schemas) {
            if (customSchema.getType().equals("object")) {
                // parse inner schemas
                String basicName = customSchema.getName();
                // manage only first level inner schemas
                for (GoogleAppsCustomSchema innerSchema : customSchema.getInnerSchemas()) {
                    final String innerSchemaName = basicName + "." + innerSchema.getName();
                    if (attrsToAdd.containsKey(basicName)) {
                        attrsToAdd.get(basicName).put(
                                innerSchema.getName(),
                                getValueByType(innerSchema, attributes, innerSchemaName));
                    } else {
                        Map<String, Object> value = new HashMap<>();
                        value.put(innerSchema.getName(), getValueByType(innerSchema, attributes, innerSchemaName));
                        attrsToAdd.put(basicName, value);
                    }
                }
            } else {
                LOG.warn("CustomSchema type {0} not allowed at this level", customSchema.getType());
            }
        }
        return attrsToAdd;
    }

    private static void set(final AtomicReference<User> content, final Consumer<User> consumer) {
        if (content.get() == null) {
            content.set(new User());
        }
        consumer.accept(content.get());
    }

    public static Directory.Users.Patch updateUser(
            final Directory.Users service,
            final String userKey,
            final AttributesAccessor attributes,
            final String customSchemas) {

        AtomicReference<User> content = new AtomicReference<>();

        Optional.ofNullable(attributes.getName())
                .filter(email -> email.getNameValue() != null)
                .ifPresent(email -> set(content, u -> u.setPrimaryEmail(email.getNameValue())));

        Optional.ofNullable(attributes.findBoolean(OperationalAttributes.ENABLE_NAME))
                .ifPresent(enable -> set(content, u -> u.setSuspended(!enable)));

        Optional.ofNullable(attributes.getPassword())
                .ifPresent(password -> set(content, u -> u.setPassword(SecurityUtil.decrypt(password))));

        Optional.ofNullable(attributes.find(GoogleAppsUtil.GIVEN_NAME_ATTR))
                .flatMap(GoogleAppsUtil::getStringValue)
                .ifPresent(stringValue -> set(content, u -> {

            u.setName(new UserName());
            u.getName().setGivenName(stringValue);
        }));

        Optional.ofNullable(attributes.find(GoogleAppsUtil.FAMILY_NAME_ATTR))
                .flatMap(GoogleAppsUtil::getStringValue)
                .ifPresent(stringValue -> set(content, u -> {

            Optional.ofNullable(u.getName()).orElseGet(() -> {
                u.setName(new UserName());
                return u.getName();
            }).setFamilyName(stringValue);
        }));

        Optional.ofNullable(attributes.find(GoogleAppsUtil.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR))
                .flatMap(GoogleAppsUtil::getBooleanValue)
                .ifPresent(booleanValue -> set(content, u -> u.setChangePasswordAtNextLogin(booleanValue)));

        Optional.ofNullable(attributes.find(GoogleAppsUtil.IP_WHITELISTED_ATTR))
                .flatMap(GoogleAppsUtil::getBooleanValue)
                .ifPresent(booleanValue -> set(content, u -> u.setIpWhitelisted(booleanValue)));

        Optional.ofNullable(attributes.find(GoogleAppsUtil.ORG_UNIT_PATH_ATTR))
                .flatMap(GoogleAppsUtil::getStringValue)
                .ifPresent(stringValue -> set(content, u -> u.setOrgUnitPath(stringValue)));

        Optional.ofNullable(attributes.find(GoogleAppsUtil.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR))
                .flatMap(GoogleAppsUtil::getBooleanValue)
                .ifPresent(booleanValue -> set(content, u -> u.setIncludeInGlobalAddressList(booleanValue)));

        Optional.ofNullable(attributes.find(GoogleAppsUtil.EMAILS_ATTR))
                .ifPresent(emails -> set(content, u -> u.setEmails(emails.getValue())));

        // Complex attributes
        Optional.ofNullable(attributes.findList(GoogleAppsUtil.IMS_ATTR))
                .ifPresent(value -> set(content, u -> u.setIms(buildObjs(value, UserIm.class))));
        Optional.ofNullable(attributes.findList(GoogleAppsUtil.EXTERNAL_IDS_ATTR))
                .ifPresent(value -> set(content, u -> u.setExternalIds(buildObjs(value, UserExternalId.class))));
        Optional.ofNullable(attributes.findList(GoogleAppsUtil.RELATIONS_ATTR))
                .ifPresent(value -> set(content, u -> u.setRelations(buildObjs(value, UserRelation.class))));
        Optional.ofNullable(attributes.findList(GoogleAppsUtil.ADDRESSES_ATTR))
                .ifPresent(value -> set(content, u -> u.setAddresses(buildObjs(value, UserAddress.class))));
        Optional.ofNullable(attributes.findList(GoogleAppsUtil.ORGANIZATIONS_ATTR))
                .ifPresent(value -> set(content, u -> u.setOrganizations(buildObjs(value, UserOrganization.class))));
        Optional.ofNullable(attributes.findList(GoogleAppsUtil.PHONES_ATTR))
                .ifPresent(value -> set(content, u -> u.setPhones(buildObjs(value, UserPhone.class))));

        if (StringUtil.isNotBlank(customSchemas)) {
            set(content, u -> u.setCustomSchemas(buildCustomAttrs(customSchemas, attributes)));
        }

        if (null == content.get()) {
            return null;
        }
        try {
            return service.patch(userKey, content.get()).setFields(GoogleAppsUtil.ID_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Users#Patch");
            throw ConnectorException.wrap(e);
        }
    }

    private static Object getValueByType(
            final GoogleAppsCustomSchema innerSchema,
            final Set<AttributeDelta> modifications,
            final String innerSchemaName) {

        return Optional.ofNullable(AttributeDeltaUtil.find(innerSchemaName, modifications)).
                map(attrDelta -> innerSchema.getMultiValued()
                ? attrDelta.getValuesToReplace() : AttributeDeltaUtil.getStringValue(attrDelta)).
                orElse(null);
    }

    private static Map<String, Map<String, Object>> buildCustomAttrs(
            final String customSchemas, final Set<AttributeDelta> modifications) {

        List<GoogleAppsCustomSchema> schemas = GoogleAppsUtil.extractCustomSchemas(customSchemas);
        Map<String, Map<String, Object>> attrsToAdd = new HashMap<>();
        for (GoogleAppsCustomSchema customSchema : schemas) {
            if (customSchema.getType().equals("object")) {
                // parse inner schemas
                String basicName = customSchema.getName();
                // manage only first level inner schemas
                for (GoogleAppsCustomSchema innerSchema : customSchema.getInnerSchemas()) {
                    final String innerSchemaName = basicName + "." + innerSchema.getName();
                    if (attrsToAdd.containsKey(basicName)) {
                        attrsToAdd.get(basicName).put(
                                innerSchema.getName(),
                                getValueByType(innerSchema, modifications, innerSchemaName));
                    } else {
                        Map<String, Object> value = new HashMap<>();
                        value.put(innerSchema.getName(), getValueByType(innerSchema, modifications, innerSchemaName));
                        attrsToAdd.put(basicName, value);
                    }
                }
            } else {
                LOG.warn("CustomSchema type {0} not allowed at this level", customSchema.getType());
            }
        }
        return attrsToAdd;
    }

    public static Directory.Users.Update updateUser(
            final Directory.Users service,
            final String userKey,
            final Set<AttributeDelta> modifications,
            final String customSchemas) {

        if (AttributeDeltaUtil.getUidAttributeDelta(modifications) != null
                || AttributeDeltaUtil.getAttributeDeltaForName(modifications) != null) {

            throw new IllegalArgumentException("Do not perform rename via updateDelta, use standard update");
        }

        AtomicReference<User> content = new AtomicReference<>();

        Optional.ofNullable(AttributeDeltaUtil.find(OperationalAttributes.ENABLE_NAME, modifications))
                .flatMap(GoogleAppsUtil::getBooleanValue)
                .ifPresent(enable -> set(content, u -> u.setSuspended(!enable)));

        Optional.ofNullable(AttributeDeltaUtil.getPasswordValue(modifications))
                .ifPresent(password -> set(content, u -> u.setPassword(SecurityUtil.decrypt(password))));

        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.GIVEN_NAME_ATTR, modifications))
                .flatMap(GoogleAppsUtil::getStringValue)
                .ifPresent(stringValue -> set(content, u -> {

            u.setName(new UserName());
            u.getName().setGivenName(stringValue);
        }));

        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.FAMILY_NAME_ATTR, modifications))
                .flatMap(GoogleAppsUtil::getStringValue)
                .ifPresent(stringValue -> set(content, u -> {

            Optional.ofNullable(u.getName()).orElseGet(() -> {
                u.setName(new UserName());
                return u.getName();
            }).setFamilyName(stringValue);
        }));

        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR, modifications))
                .flatMap(GoogleAppsUtil::getBooleanValue)
                .ifPresent(booleanValue -> set(content, u -> u.setChangePasswordAtNextLogin(booleanValue)));

        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.IP_WHITELISTED_ATTR, modifications))
                .flatMap(GoogleAppsUtil::getBooleanValue)
                .ifPresent(booleanValue -> set(content, u -> u.setIpWhitelisted(booleanValue)));

        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.ORG_UNIT_PATH_ATTR, modifications))
                .flatMap(GoogleAppsUtil::getStringValue)
                .ifPresent(stringValue -> set(content, u -> u.setOrgUnitPath(stringValue)));

        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, modifications))
                .flatMap(GoogleAppsUtil::getBooleanValue)
                .ifPresent(booleanValue -> set(content, u -> u.setIncludeInGlobalAddressList(booleanValue)));

        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.EMAILS_ATTR, modifications))
                .ifPresent(a -> set(content, u -> u.setEmails(a.getValuesToReplace())));

        // Complex attributes
        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.IMS_ATTR, modifications))
                .ifPresent(a -> set(content, u -> u.setIms(
                buildObjs(a.getValuesToReplace(), UserIm.class))));
        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.EXTERNAL_IDS_ATTR, modifications))
                .ifPresent(a -> set(content, u -> u.setExternalIds(
                buildObjs(a.getValuesToReplace(), UserExternalId.class))));
        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.RELATIONS_ATTR, modifications))
                .ifPresent(a -> set(content, u -> u.setRelations(
                buildObjs(a.getValuesToReplace(), UserRelation.class))));
        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.ADDRESSES_ATTR, modifications))
                .ifPresent(a -> set(content, u -> u.setAddresses(
                buildObjs(a.getValuesToReplace(), UserAddress.class))));
        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.ORGANIZATIONS_ATTR, modifications))
                .ifPresent(a -> set(content, u -> u.setOrganizations(
                buildObjs(a.getValuesToReplace(), UserOrganization.class))));
        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.PHONES_ATTR, modifications))
                .ifPresent(a -> set(content, u -> u.setPhones(
                buildObjs(a.getValuesToReplace(), UserPhone.class))));

        if (StringUtil.isNotBlank(customSchemas)) {
            set(content, u -> u.setCustomSchemas(buildCustomAttrs(customSchemas, modifications)));
        }

        if (null == content.get()) {
            return null;
        }
        try {
            return service.update(userKey, content.get()).setFields(GoogleAppsUtil.ID_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Users#update");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Photos.Update createUpdateUserPhoto(
            final Directory.Users.Photos service, final String userKey, final byte[] data) {

        UserPhoto content = new UserPhoto();
        content.setPhotoData(Base64.getMimeEncoder().encodeToString(data));

        try {
            return service.update(userKey, content).setFields(GoogleAppsUtil.ID_ATTR);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Aliases#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Set<String> listAliases(final Directory.Users.Aliases service, final String userKey) {
        try {
            return execute(
                    service.list(userKey),
                    new RequestResultHandler<Directory.Users.Aliases.List, Aliases, Set<String>>() {

                @SuppressWarnings("unchecked")
                @Override
                public Set<String> handleResult(final Directory.Users.Aliases.List request, final Aliases value) {
                    return Optional.ofNullable(value.getAliases()).map(aliases -> aliases.stream().
                            map(map -> ((Map<String, String>) map).get(GoogleAppsUtil.ALIAS_ATTR)).
                            filter(Objects::nonNull).collect(Collectors.toSet())).
                            orElse(Collections.emptySet());
                }

                @Override
                public Set<String> handleError(final Throwable e) {
                    LOG.error(e, "While getting aliases for {0}", userKey);
                    return Collections.emptySet();
                }
            });
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Aliases#list");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Aliases.Insert createUserAlias(
            final Directory.Users.Aliases service, final String userKey, final String alias) {

        Alias content = new Alias();
        content.setAlias(alias);
        try {
            return service.insert(userKey, content).setFields(GoogleAppsUtil.ID_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Aliases#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    public static Directory.Users.Aliases.Delete deleteUserAlias(
            final Directory.Users.Aliases service, final String userKey, final String alias) {

        try {
            return service.delete(userKey, alias);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Aliases#Delete");
            throw ConnectorException.wrap(e);
        }
    }

    private static Object getValueFromKey(
            final String customSchema,
            final Map<String, Map<String, Object>> customSchemas) {

        String[] names = customSchema.split("\\.");
        return names.length > 1
                ? customSchemas.get(names[0]) != null ? customSchemas.get(names[0]).get(names[1]) : null
                : null;
    }

    public static ConnectorObject fromUser(
            final GoogleAppsConfiguration configuration,
            final User user,
            final Set<String> attributesToGet,
            final Directory.Groups service) {

        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        if (null != user.getEtag()) {
            builder.setUid(new Uid(user.getId(), user.getEtag()));
        } else {
            builder.setUid(user.getId());
        }
        builder.setName(user.getPrimaryEmail());
        Optional.ofNullable(user.getSuspended()).
                ifPresent(suspended -> builder.addAttribute(AttributeBuilder.buildEnabled(!suspended)));

        if ((null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.ID_ATTR))) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.ID_ATTR, user.getId()));
        }
        if ((null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.PRIMARY_EMAIL_ATTR))) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.PRIMARY_EMAIL_ATTR, user.getPrimaryEmail()));
        }
        // Optional
        // If both givenName and familyName are empty then Google didn't return with 'name'
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.GIVEN_NAME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.GIVEN_NAME_ATTR,
                    null != user.getName() ? user.getName().getGivenName() : null));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.FAMILY_NAME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.FAMILY_NAME_ATTR,
                    null != user.getName() ? user.getName().getFamilyName() : null));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.FULL_NAME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.FULL_NAME_ATTR,
                    null != user.getName() ? user.getName().getFullName() : null));
        }

        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.IS_ADMIN_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.IS_ADMIN_ATTR, user.getIsAdmin()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.IS_DELEGATED_ADMIN_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.IS_DELEGATED_ADMIN_ATTR, user.getIsDelegatedAdmin()));
        }
        if ((null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.LAST_LOGIN_TIME_ATTR))
                && user.getLastLoginTime() != null) {

            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.LAST_LOGIN_TIME_ATTR, user.getLastLoginTime().toString()));
        }
        if ((null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.CREATION_TIME_ATTR))
                && user.getCreationTime() != null) {

            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.CREATION_TIME_ATTR, user.getCreationTime().toString()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.AGREED_TO_TERMS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.AGREED_TO_TERMS_ATTR, user.getAgreedToTerms()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.SUSPENSION_REASON_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.SUSPENSION_REASON_ATTR, user.getSuspensionReason()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.CHANGE_PASSWORD_AT_NEXT_LOGIN_ATTR, user.getChangePasswordAtNextLogin()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.IP_WHITELISTED_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.IP_WHITELISTED_ATTR, user.getIpWhitelisted()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.IMS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.IMS_ATTR, (Collection) user.getIms()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.EMAILS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.EMAILS_ATTR, (Collection) user.getEmails()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.EXTERNAL_IDS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.EXTERNAL_IDS_ATTR, (Collection) user.getExternalIds()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.RELATIONS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.RELATIONS_ATTR, (Collection) user.getRelations()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.ADDRESSES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.ADDRESSES_ATTR, (Collection) user.getAddresses()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.ORGANIZATIONS_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.ORGANIZATIONS_ATTR, (Collection) user.getOrganizations()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.PHONES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.PHONES_ATTR, (Collection) user.getPhones()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.ALIASES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.ALIASES_ATTR, user.getAliases()));
        }

        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.NON_EDITABLE_ALIASES_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.NON_EDITABLE_ALIASES_ATTR, user.getNonEditableAliases()));
        }

        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.CUSTOMER_ID_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.CUSTOMER_ID_ATTR, user.getCustomerId()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.ORG_UNIT_PATH_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.ORG_UNIT_PATH_ATTR, user.getOrgUnitPath()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.IS_MAILBOX_SETUP_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.IS_MAILBOX_SETUP_ATTR, user.getIsMailboxSetup()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.INCLUDE_IN_GLOBAL_ADDRESS_LIST_ATTR, user.getIncludeInGlobalAddressList()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.THUMBNAIL_PHOTO_URL_ATTR)) {
            builder.addAttribute(
                    AttributeBuilder.build(GoogleAppsUtil.THUMBNAIL_PHOTO_URL_ATTR, user.getThumbnailPhotoUrl()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.DELETION_TIME_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.DELETION_TIME_ATTR,
                    null != user.getDeletionTime() ? user.getDeletionTime().toString() : null));
        }
        if (null == attributesToGet || ("full".equals(configuration.getProjection())
                && StringUtil.isNotBlank(configuration.getCustomSchemasJSON()))) {

            GoogleAppsUtil.extractCustomSchemas(configuration.getCustomSchemasJSON()).forEach(customSchema -> {
                if (customSchema.getType().equals("object")) {
                    // manage only first level inner schemas
                    for (GoogleAppsCustomSchema innerSchema : customSchema.getInnerSchemas()) {
                        String innerSchemaName = customSchema.getName() + "." + innerSchema.getName();
                        builder.addAttribute(AttributeBuilder.build(
                                innerSchemaName,
                                null != user.getCustomSchemas()
                                ? getValueFromKey(innerSchemaName, user.getCustomSchemas())
                                : null));
                    }
                } else {
                    LOG.warn("CustomSchema type {0} not allowed at this level", customSchema.getType());
                }
            });
        }
        // Expensive to get
        if (null != attributesToGet && attributesToGet.contains(PredefinedAttributes.GROUPS_NAME)) {
            builder.addAttribute(AttributeBuilder.build(PredefinedAttributes.GROUPS_NAME,
                    GroupHandler.listGroups(service, user.getId(), configuration.getDomain())));
        }

        return builder.build();
    }
}
