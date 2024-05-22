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
import com.google.api.services.admin.directory.model.OrgUnit;
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
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.PredefinedAttributeInfos;
import org.identityconnectors.framework.common.objects.Uid;

/**
 * OrgunitsHandler is a util class to cover all Organizations Unit related operations.
 *
 * @author Laszlo Hordos
 */
public final class OrgunitsHandler {

    /**
     * Setup logging for the {@link OrgunitsHandler}.
     */
    private static final Log LOG = Log.getLog(OrgunitsHandler.class);

    // /////////////
    //
    // ORGUNIT
    // https://developers.google.com/admin-sdk/directory/v1/reference/orgunits
    //
    // /////////////
    public static ObjectClassInfo getObjectClassInfo() {
        // @formatter:off
        /*
         * {
         * "kind": "admin#directory#orgUnit",
         * "etag": etag,
         * "name": string,
         * "description": string,
         * "orgUnitPath": string,
         * "parentOrgUnitPath": string,
         * "blockInheritance": boolean
         * }
         */
        // @formatter:on
        ObjectClassInfoBuilder builder = new ObjectClassInfoBuilder();
        builder.setType(GoogleAppsUtil.ORG_UNIT.getObjectClassValue());
        builder.setContainer(true);
        // primaryEmail
        builder.addAttributeInfo(Name.INFO);
        // parentOrgUnitPath
        builder.addAttributeInfo(AttributeInfoBuilder.define(GoogleAppsUtil.PARENT_ORG_UNIT_PATH_ATTR).
                setRequired(true).build());

        // optional
        builder.addAttributeInfo(PredefinedAttributeInfos.DESCRIPTION);
        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsUtil.ORG_UNIT_PATH_ATTR));
        builder.addAttributeInfo(AttributeInfoBuilder.build(GoogleAppsUtil.BLOCK_INHERITANCE_ATTR, Boolean.class));

        return builder.build();
    }

    private static Optional<String> getOrgUnitNameFromPath(final Name name) {
        if (null == name) {
            return Optional.empty();
        }
        String fullName = name.getNameValue();
        String[] elements = fullName.split("/");
        if (elements.length == 0) {
            return Optional.of(fullName);
        }
        return Optional.of(elements[elements.length - 1]);
    }

    private static String getParentOrgUnitPath(final AttributesAccessor attributes) {
        // parentOrgUnitPath The organization unit's parent path. For example,
        // /corp/sales is the parent path for /corp/sales/sales_support organization unit.
        String parentOrgUnitPath = attributes.findString(GoogleAppsUtil.PARENT_ORG_UNIT_PATH_ATTR);
        if (StringUtil.isNotBlank(parentOrgUnitPath)) {
            if (parentOrgUnitPath.charAt(0) != '/') {
                parentOrgUnitPath = "/" + parentOrgUnitPath;
            }
        }

        if (StringUtil.isBlank(parentOrgUnitPath)) {
            throw new InvalidAttributeValueException(
                    "Missing required attribute 'parentOrgUnitPath'. "
                    + "The organization unit's parent path. Required when creating an orgunit.");
        }
        return parentOrgUnitPath;
    }

    public static Directory.Orgunits.Insert create(
            final Directory.Orgunits service, final AttributesAccessor attributes) {

        OrgUnit resource = new OrgUnit();
        resource.setParentOrgUnitPath(getParentOrgUnitPath(attributes));
        getOrgUnitNameFromPath(attributes.getName()).ifPresent(resource::setName);

        // Optional
        resource.setBlockInheritance(attributes.findBoolean(GoogleAppsUtil.BLOCK_INHERITANCE_ATTR));
        resource.setDescription(attributes.findString(GoogleAppsUtil.DESCRIPTION_ATTR));

        try {
            return service.
                    insert(GoogleAppsUtil.MY_CUSTOMER_ID, resource).
                    setFields(GoogleAppsUtil.ORG_UNIT_PATH_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Groups#Insert");
            throw ConnectorException.wrap(e);
        }
    }

    private static void set(final AtomicReference<OrgUnit> content, final Consumer<OrgUnit> consumer) {
        if (content.get() == null) {
            content.set(new OrgUnit());
        }
        consumer.accept(content.get());
    }

    public static Directory.Orgunits.Patch update(
            final Directory.Orgunits service,
            final String orgUnitPath,
            final AttributesAccessor attributes) {

        AtomicReference<OrgUnit> content = new AtomicReference<>();

        getOrgUnitNameFromPath(attributes.getName()).ifPresent(name -> set(content, o -> o.setName(name)));

        Optional.ofNullable(attributes.findString(GoogleAppsUtil.PARENT_ORG_UNIT_PATH_ATTR))
                .ifPresent(ignore -> set(content, o -> o.setParentOrgUnitPath(getParentOrgUnitPath(attributes))));

        Optional.ofNullable(attributes.find(GoogleAppsUtil.DESCRIPTION_ATTR))
                .flatMap(GoogleAppsUtil::getStringValue)
                .ifPresent(stringValue -> set(content, o -> o.setDescription(stringValue)));

        Optional.ofNullable(attributes.findBoolean(GoogleAppsUtil.BLOCK_INHERITANCE_ATTR))
                .ifPresent(blockInheritance -> set(content, u -> u.setBlockInheritance(!blockInheritance)));

        if (null == content.get()) {
            return null;
        }
        try {
            // Full path of the organization unit
            return service.patch(
                    GoogleAppsUtil.MY_CUSTOMER_ID,
                    CollectionUtil.newList(orgUnitPath),
                    content.get()).setFields(GoogleAppsUtil.ORG_UNIT_PATH_ETAG);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Orgunits#Patch");
            throw ConnectorException.wrap(e);
        }
    }

    public static ConnectorObject from(final OrgUnit content, final Set<String> attributesToGet) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(GoogleAppsUtil.ORG_UNIT);

        builder.setUid(generateUid(content));
        builder.setName(content.getName());

        // Optional
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.DESCRIPTION_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.DESCRIPTION_ATTR, content.getDescription()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.ORG_UNIT_PATH_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.ORG_UNIT_PATH_ATTR, content.getOrgUnitPath()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.PARENT_ORG_UNIT_PATH_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.PARENT_ORG_UNIT_PATH_ATTR, content.getParentOrgUnitPath()));
        }
        if (null == attributesToGet || attributesToGet.contains(GoogleAppsUtil.BLOCK_INHERITANCE_ATTR)) {
            builder.addAttribute(AttributeBuilder.build(
                    GoogleAppsUtil.BLOCK_INHERITANCE_ATTR, content.getBlockInheritance()));
        }

        return builder.build();
    }

    public static Uid generateUid(final OrgUnit content) {
        String orgUnitPath = content.getOrgUnitPath();
        if (orgUnitPath.startsWith("/")) {
            orgUnitPath = orgUnitPath.substring(1);
        }

        Uid uid;
        if (null != content.getEtag()) {
            uid = new Uid(orgUnitPath, content.getEtag());
        } else {
            uid = new Uid(orgUnitPath);
        }
        return uid;
    }

    private OrgunitsHandler() {
        // private constructor for static utility class
    }
}
