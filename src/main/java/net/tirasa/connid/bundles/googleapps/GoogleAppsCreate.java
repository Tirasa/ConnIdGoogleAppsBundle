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
import com.google.api.services.directory.model.Alias;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Member;
import com.google.api.services.directory.model.OrgUnit;
import com.google.api.services.directory.model.User;
import com.google.api.services.directory.model.UserMakeAdmin;
import com.google.api.services.directory.model.UserPhoto;
import com.google.api.services.licensing.Licensing;
import com.google.api.services.licensing.model.LicenseAssignment;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.RetryableException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.Uid;

public class GoogleAppsCreate {

    private static final Log LOG = Log.getLog(GoogleAppsCreate.class);

    private final GoogleAppsConfiguration configuration;

    private final ObjectClass objectClass;

    private final Set<Attribute> createAttributes;

    public GoogleAppsCreate(
            final GoogleAppsConfiguration configuration,
            final ObjectClass objectClass,
            final Set<Attribute> createAttributes) {

        this.configuration = configuration;
        this.objectClass = objectClass;
        this.createAttributes = createAttributes;
    }

    private Uid createUser(final AttributesAccessor accessor) {
        Uid uid = GoogleApiExecutor.execute(UserHandler.createUser(
                configuration.getDirectory().users(), accessor, configuration.getCustomSchemasJSON()),
                new RequestResultHandler<Directory.Users.Insert, User, Uid>() {

            @Override
            public Uid handleResult(final Directory.Users.Insert request, final User value) {
                LOG.ok("New User is created: {0} with ETag {1}", value.getId(), value.getEtag());
                return new Uid(value.getId(), value.getEtag());
            }
        });

        List<Object> aliases = accessor.findList(GoogleAppsUtil.ALIASES_ATTR);
        if (null != aliases) {
            final Directory.Users.Aliases aliasesService = configuration.getDirectory().users().aliases();
            for (Object alias : aliases) {
                if (alias instanceof String) {
                    String id = GoogleApiExecutor.execute(
                            UserHandler.createUserAlias(aliasesService, uid.getUidValue(), (String) alias),
                            new RequestResultHandler<Directory.Users.Aliases.Insert, Alias, String>() {

                        @Override
                        public String handleResult(final Directory.Users.Aliases.Insert request, final Alias value) {
                            return value == null ? null : value.getId();
                        }
                    });

                    if (null == id) {
                        // TODO make warn about failed update
                    }
                } else if (null != alias) {
                    // Delete user and Error or
                    RetryableException e =
                            RetryableException.wrap("Invalid attribute value: " + String.valueOf(alias), uid);
                    e.initCause(new InvalidAttributeValueException("Attribute 'aliases' must be a String list"));
                    throw e;
                }
            }
        }

        Attribute photo = accessor.find(GoogleAppsUtil.PHOTO_ATTR);
        if (null != photo) {
            Object photoObject = AttributeUtil.getSingleValue(photo);
            if (photoObject instanceof byte[]) {
                String id = GoogleApiExecutor.execute(UserHandler.createUpdateUserPhoto(
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
                RetryableException e = RetryableException.wrap(
                        "Invalid attribute value: " + String.valueOf(photoObject), uid);
                e.initCause(new InvalidAttributeValueException("Attribute 'photo' must be a single byte[] value"));
                throw e;
            }
        }

        Attribute isAdmin = accessor.find(GoogleAppsUtil.IS_ADMIN_ATTR);
        if (null != isAdmin) {
            try {
                Boolean isAdminValue = AttributeUtil.getBooleanValue(isAdmin);
                if (null != isAdminValue && isAdminValue) {
                    UserMakeAdmin content = new UserMakeAdmin();
                    content.setStatus(isAdminValue);

                    GoogleApiExecutor.execute(
                            configuration.getDirectory().users().makeAdmin(uid.getUidValue(), content),
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

        Attribute groups = accessor.find(PredefinedAttributes.GROUPS_NAME);
        if (null != groups && null != groups.getValue()) {
            final Directory.Members service = configuration.getDirectory().members();
            if (!groups.getValue().isEmpty()) {
                final List<Directory.Members.Insert> addGroups = new ArrayList<>();

                for (Object group : groups.getValue()) {
                    if (group instanceof String) {
                        String email = accessor.getName().getNameValue();
                        addGroups.add(MembersHandler.create(service, (String) group, email, null));
                    } else if (null != group) {
                        // throw error/revert?
                        throw new InvalidAttributeValueException("Attribute '__GROUPS__' must be a String list");
                    }
                }

                // Add new Member object
                for (Directory.Members.Insert insert : addGroups) {
                    GoogleApiExecutor.execute(insert,
                            new RequestResultHandler<Directory.Members.Insert, Member, Object>() {

                        @Override
                        public Object handleResult(final Directory.Members.Insert request, final Member value) {
                            return null;
                        }

                        @Override
                        public Object handleDuplicate(final IOException e) {
                            // Do nothing
                            return null;
                        }
                    });
                }
            }
        }

        return uid;
    }

    private Uid createGroup(final AttributesAccessor accessor) {
        Uid uid = GoogleApiExecutor.execute(
                GroupHandler.create(configuration.getDirectory().groups(), accessor),
                new RequestResultHandler<Directory.Groups.Insert, Group, Uid>() {

            @Override
            public Uid handleResult(final Directory.Groups.Insert request, final Group value) {
                LOG.ok("New Group is created:{0}", value.getEmail());
                return new Uid(value.getId(), value.getEtag());
            }
        });

        List<Object> aliases = accessor.findList(GoogleAppsUtil.ALIASES_ATTR);
        if (null != aliases) {
            final Directory.Groups.Aliases aliasesService = configuration.getDirectory().groups().aliases();
            for (Object alias : aliases) {
                if (alias instanceof String) {
                    String id = GoogleApiExecutor.execute(
                            GroupHandler.createGroupAlias(aliasesService, uid.getUidValue(), (String) alias),
                            new RequestResultHandler<Directory.Groups.Aliases.Insert, Alias, String>() {

                        @Override
                        public String handleResult(final Directory.Groups.Aliases.Insert request, final Alias value) {
                            return value == null ? null : value.getId();
                        }
                    });

                    if (null == id) {
                        // TODO make warn about failed update
                    }
                } else if (null != alias) {
                    // Delete group and Error or
                    RetryableException e =
                            RetryableException.wrap("Invalid attribute value: " + String.valueOf(alias), uid);
                    e.initCause(new InvalidAttributeValueException("Attribute 'aliases' must be a String list"));
                    throw e;
                }
            }
        }

        List<Object> members = accessor.findList(GoogleAppsUtil.MEMBERS_ATTR);
        if (null != members) {
            final Directory.Members membersService = configuration.getDirectory().members();
            for (Object member : members) {
                if (member instanceof Map) {
                    String email = (String) ((Map) member).get(GoogleAppsUtil.EMAIL_ATTR);
                    String role = (String) ((Map) member).get(GoogleAppsUtil.ROLE_ATTR);

                    String id = GoogleApiExecutor.execute(
                            MembersHandler.create(membersService, uid.getUidValue(), email, role),
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
    }

    public Uid execute() {
        final AttributesAccessor accessor = new AttributesAccessor(createAttributes);

        if (ObjectClass.ACCOUNT.equals(objectClass)) {
            return createUser(accessor);
        }

        if (ObjectClass.GROUP.equals(objectClass)) {
            return createGroup(accessor);
        }

        if (GoogleAppsUtil.MEMBER.equals(objectClass)) {
            return GoogleApiExecutor.execute(
                    MembersHandler.create(configuration.getDirectory().members(), accessor),
                    new RequestResultHandler<Directory.Members.Insert, Member, Uid>() {

                @Override
                public Uid handleResult(final Directory.Members.Insert request, final Member value) {
                    LOG.ok("New Member is created:{0}/{1}", request.getGroupKey(), value.getEmail());
                    return MembersHandler.generateUid(request.getGroupKey(), value);
                }
            });
        }

        if (GoogleAppsUtil.ORG_UNIT.equals(objectClass)) {
            return GoogleApiExecutor.execute(
                    OrgunitsHandler.create(configuration.getDirectory().orgunits(), accessor),
                    new RequestResultHandler<Directory.Orgunits.Insert, OrgUnit, Uid>() {

                @Override
                public Uid handleResult(final Directory.Orgunits.Insert request, final OrgUnit value) {
                    LOG.ok("New OrgUnit is created:{0}", value.getName());
                    return OrgunitsHandler.generateUid(value);
                }
            });
        }

        if (GoogleAppsUtil.LICENSE_ASSIGNMENT.equals(objectClass)) {
            return GoogleApiExecutor.execute(
                    LicenseAssignmentsHandler.create(configuration.getLicensing().licenseAssignments(), accessor),
                    new RequestResultHandler<Licensing.LicenseAssignments.Insert, LicenseAssignment, Uid>() {

                @Override
                public Uid handleResult(
                        final Licensing.LicenseAssignments.Insert request,
                        final LicenseAssignment value) {

                    LOG.ok("LicenseAssignment is Created:{0}/{1}/{2}",
                            value.getProductId(), value.getSkuId(), value.getUserId());
                    return LicenseAssignmentsHandler.generateUid(value);
                }
            });
        }

        throw new UnsupportedOperationException("Create of type "
                + objectClass.getObjectClassValue() + " is not supported");
    }
}
