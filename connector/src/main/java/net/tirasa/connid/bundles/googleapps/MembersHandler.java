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

import static net.tirasa.connid.bundles.googleapps.GoogleApiExecutor.execute;

import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Member;
import com.google.api.services.directory.model.Members;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import org.identityconnectors.framework.common.objects.Uid;

public final class MembersHandler {

    private static final Log LOG = Log.getLog(MembersHandler.class);

    public static ObjectClassInfo getObjectClassInfo() {
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

    public static Directory.Members.Insert create(
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

        return create(service, groupKey, memberKey, role);
    }

    public static Directory.Members.Insert create(
            final Directory.Members service, final String groupKey, final Uid uid, final String role) {

        Member content = new Member();
        content.setId(uid.getUidValue());
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

    public static Directory.Members.Insert create(
            final Directory.Members service, final String groupKey, final String email, final String role) {

        Member content = new Member();
        content.setEmail(email);
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

    public static Directory.Members.Patch update(
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

    public static Directory.Members.Delete delete(
            final Directory.Members service, final String groupKey, final String memberKey) {

        try {
            return service.delete(groupKey, memberKey);
        } catch (IOException e) {
            LOG.warn(e, "Failed to initialize Members#Delete");
            throw ConnectorException.wrap(e);
        }
    }

    public static ConnectorObject from(final String groupKey, final Member content) {
        ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
        builder.setObjectClass(GoogleAppsUtil.MEMBER);

        Uid uid = generateUid(groupKey, content);
        builder.setUid(uid);
        builder.setName(uid.getUidValue());

        builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.GROUP_KEY_ATTR, content.getId()));
        builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.EMAIL_ATTR, content.getEmail()));
        builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.ROLE_ATTR, content.getRole()));
        builder.addAttribute(AttributeBuilder.build(GoogleAppsUtil.TYPE_ATTR, content.getType()));

        return builder.build();
    }

    public static Uid generateUid(final String groupKey, final Member content) {
        String memberName = groupKey + '/' + content.getEmail();

        Uid uid;
        if (null != content.getEtag()) {
            uid = new Uid(memberName, content.getEtag());
        } else {
            uid = new Uid(memberName);
        }
        return uid;
    }

    public static List<Map<String, String>> listMembers(
            final Directory.Members service, final String groupKey, final String roles) {

        final List<Map<String, String>> result = new ArrayList<>();
        try {
            Directory.Members.List request = service.list(groupKey);
            request.setRoles(StringUtil.isBlank(roles) ? "OWNER,MANAGER,MEMBER" : roles);

            String nextPageToken;
            do {
                nextPageToken = execute(request, new RequestResultHandler<Directory.Members.List, Members, String>() {

                    @Override
                    public String handleResult(final Directory.Members.List request, final Members value) {
                        if (null != value.getMembers()) {
                            for (Member member : value.getMembers()) {
                                Map<String, String> m = new LinkedHashMap<>(2);
                                m.put(GoogleAppsUtil.EMAIL_ATTR, member.getEmail());
                                m.put(GoogleAppsUtil.ROLE_ATTR, member.getRole());
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

    private MembersHandler() {
        // private constructor for static utility class
    }
}
