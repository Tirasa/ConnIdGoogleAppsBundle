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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.util.GenericData;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;

public final class GoogleAppsUtil {

    private static final Log LOG = Log.getLog(GoogleAppsUtil.class);

    public static final ObjectClass ORG_UNIT = new ObjectClass("OrgUnit");

    public static final ObjectClass MEMBER = new ObjectClass("Member");

    public static final ObjectClass ALIAS = new ObjectClass("Alias");

    public static final ObjectClass LICENSE_ASSIGNMENT = new ObjectClass("LicenseAssignment");

    public static final String ID_ETAG = "id,etag";

    public static final String EMAIL_ETAG = "email,etag";

    public static final String ID_EMAIL_ETAG = "id,email,etag";

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

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_EMPTY);

    public static List<GoogleAppsCustomSchema> extractCustomSchemas(final String json) {
        List<GoogleAppsCustomSchema> customSchemasObj = null;
        try {
            customSchemasObj = MAPPER.readValue(
                    json,
                    new TypeReference<List<GoogleAppsCustomSchema>>() {
            });
        } catch (IOException e) {
            LOG.error(e, "While validating customSchemaJSON");
        }
        return customSchemasObj;
    }

    public static <T extends GenericData> T extract(final String json, final Class<T> clazz) {
        T obj = null;
        try {
            obj = MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            LOG.error(e, "While extracting UserOrganization");
        }
        return obj;
    }

    public static String getName(final Name name) {
        if (name != null) {
            String email = name.getNameValue();
            if (StringUtil.isBlank(email)) {
                throw new InvalidAttributeValueException("Required attribute __NAME__ is blank");
            }
            return email;
        }
        throw new InvalidAttributeValueException("Required attribute __NAME__ is missing");
    }

    public static Optional<String> getStringValue(final Attribute source) {
        Object value = AttributeUtil.getSingleValue(source);
        if (value instanceof String) {
            return Optional.of((String) value);
        } else if (null != value) {
            throw new InvalidAttributeValueException("The " + source.getName()
                    + " attribute is not String value attribute. It has value with type "
                    + value.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    public static Optional<Boolean> getBooleanValue(final Attribute source) {
        Object value = AttributeUtil.getSingleValue(source);
        if (value instanceof Boolean) {
            return Optional.of((Boolean) value);
        } else if (null != value) {
            throw new InvalidAttributeValueException("The " + source.getName()
                    + " attribute is not Boolean value attribute. It has value with type "
                    + value.getClass().getSimpleName());
        }
        return Optional.empty();
    }

    public static String generateLicenseId(final String productId, final String skuId, final String userId) {
        if (StringUtil.isBlank(productId) || StringUtil.isBlank(skuId) || StringUtil.isBlank(userId)) {
            throw new IllegalArgumentException("productId, skuId and/or userId value(s) are not valid");
        }
        return productId + "/sku/" + skuId + "/user/" + userId;
    }

    private GoogleAppsUtil() {
        // private constructor for static utility class
    }
}
