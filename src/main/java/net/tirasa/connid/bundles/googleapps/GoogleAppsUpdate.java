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
import static net.tirasa.connid.bundles.googleapps.GroupHandler.listGroups;
import static net.tirasa.connid.bundles.googleapps.MembersHandler.listMembers;

import com.google.api.services.directory.Directory;
import com.google.api.services.directory.model.Alias;
import com.google.api.services.directory.model.Group;
import com.google.api.services.directory.model.Member;
import com.google.api.services.directory.model.OrgUnit;
import com.google.api.services.directory.model.User;
import com.google.api.services.licensing.Licensing;
import com.google.api.services.licensing.model.LicenseAssignment;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.InvalidAttributeValueException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeDelta;
import org.identityconnectors.framework.common.objects.AttributeDeltaUtil;
import org.identityconnectors.framework.common.objects.AttributesAccessor;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.Uid;

public class GoogleAppsUpdate {

    private static final Log LOG = Log.getLog(GoogleAppsUpdate.class);

    private final GoogleAppsConfiguration configuration;

    private final ObjectClass objectClass;

    private final Uid uid;

    public GoogleAppsUpdate(
            final GoogleAppsConfiguration configuration,
            final ObjectClass objectClass,
            final Uid uid) {

        this.configuration = configuration;
        this.objectClass = objectClass;
        this.uid = uid;
    }

    private Uid updateUser(final AttributesAccessor accessor) {
        Uid uidAfterUpdate = uid;

        Directory.Users.Patch patch = UserHandler.updateUser(
                configuration.getDirectory().users(),
                uid.getUidValue(),
                accessor,
                configuration.getCustomSchemasJSON());
        if (null != patch) {
            uidAfterUpdate = execute(patch, new RequestResultHandler<Directory.Users.Patch, User, Uid>() {

                @Override
                public Uid handleResult(final Directory.Users.Patch request, final User value) {
                    LOG.ok("User is Updated:{0}", value.getId());
                    return new Uid(value.getId(), value.getEtag());
                }
            });
        }

        List<Object> aliases = accessor.findList(GoogleAppsUtil.ALIASES_ATTR);
        if (null != aliases) {
            Directory.Users.Aliases service = configuration.getDirectory().users().aliases();
            Set<String> currentAliases = UserHandler.listAliases(service, uidAfterUpdate.getUidValue());

            if (aliases.isEmpty()) {
                // Remove all aliases
                for (Object alias : currentAliases) {
                    execute(UserHandler.deleteUserAlias(service, uidAfterUpdate.getUidValue(), (String) alias),
                            new RequestResultHandler<Directory.Users.Aliases.Delete, Void, Object>() {

                        @Override
                        public Object handleResult(final Directory.Users.Aliases.Delete request, final Void value) {
                            return null;
                        }

                        @Override
                        public Object handleNotFound(final IOException e) {
                            // It may be an indirect membership, not able to delete
                            return null;
                        }
                    });
                }
            } else {
                List<Directory.Users.Aliases.Insert> addAliases = new ArrayList<>();
                Set<String> keepAliases = CollectionUtil.newCaseInsensitiveSet();

                for (Object alias : aliases) {
                    if (currentAliases.contains(alias.toString())) {
                        keepAliases.add((String) alias);
                    } else {
                        addAliases.add(
                                UserHandler.createUserAlias(service, uidAfterUpdate.getUidValue(), (String) alias));
                    }
                }

                // Add new alias
                for (Directory.Users.Aliases.Insert insert : addAliases) {
                    execute(insert, new RequestResultHandler<Directory.Users.Aliases.Insert, Alias, Object>() {

                        @Override
                        public Object handleResult(final Directory.Users.Aliases.Insert insert, final Alias value) {
                            return null;
                        }

                        @Override
                        public Object handleDuplicate(final IOException e) {
                            return null;
                        }
                    });
                }

                // Delete existing aliases
                if (currentAliases.removeAll(keepAliases)) {
                    for (Object alias : currentAliases) {
                        execute(UserHandler.deleteUserAlias(service, (String) alias, uidAfterUpdate.getUidValue()),
                                new RequestResultHandler<Directory.Users.Aliases.Delete, Void, Object>() {

                            @Override
                            public Object handleResult(final Directory.Users.Aliases.Delete request, final Void value) {
                                return null;
                            }

                            @Override
                            public Object handleNotFound(final IOException e) {
                                return null;
                            }
                        });
                    }
                }
            }
        }

        Attribute groups = accessor.find(PredefinedAttributes.GROUPS_NAME);
        if (null != groups && null != groups.getValue()) {
            Directory.Members service = configuration.getDirectory().members();
            Set<String> currentGroups = listGroups(
                    configuration.getDirectory().groups(), uidAfterUpdate.getUidValue(), configuration.getDomain());

            if (groups.getValue().isEmpty()) {
                // Remove all membership
                for (String groupKey : currentGroups) {
                    execute(MembersHandler.delete(service, groupKey, uidAfterUpdate.getUidValue()),
                            new RequestResultHandler<Directory.Members.Delete, Void, Object>() {

                        @Override
                        public Object handleResult(final Directory.Members.Delete request, final Void value) {
                            return null;
                        }

                        @Override
                        public Object handleNotFound(final IOException e) {
                            // It may be an indirect membership, not able to delete
                            return null;
                        }
                    });
                }
            } else {
                List<Directory.Members.Insert> addGroups = new ArrayList<>();
                Set<String> keepGroups = CollectionUtil.newCaseInsensitiveSet();

                for (Object member : groups.getValue()) {
                    if (member instanceof String) {
                        if (currentGroups.contains((String) member)) {
                            keepGroups.add((String) member);
                        } else {
                            String email = accessor.getName().getNameValue();
                            addGroups.add(MembersHandler.create(service, (String) member, email, null));
                        }
                    } else if (null != member) {
                        // throw error/revert?
                        throw new InvalidAttributeValueException("Attribute '__GROUPS__' must be a String list");
                    }
                }

                // Add new Member object
                for (Directory.Members.Insert insert : addGroups) {
                    execute(insert, new RequestResultHandler<Directory.Members.Insert, Member, Object>() {

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

                // Delete existing Member object
                if (currentGroups.removeAll(keepGroups)) {
                    for (String groupKey : currentGroups) {
                        execute(MembersHandler.delete(service, groupKey, uidAfterUpdate.getUidValue()),
                                new RequestResultHandler<Directory.Members.Delete, Void, Object>() {

                            @Override
                            public Object handleResult(final Directory.Members.Delete request, final Void value) {
                                return null;
                            }

                            @Override
                            public Object handleNotFound(final IOException e) {
                                // It may be an indirect membership, not able to delete
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
                && accessor.hasAttribute(OperationalAttributes.ENABLE_NAME)
                && !accessor.findBoolean(OperationalAttributes.ENABLE_NAME)
                && StringUtil.isNotBlank(accessor.findString(GoogleAppsUtil.PRIMARY_EMAIL_ATTR))) {

            for (String skuId : configuration.getSkuIds()) {
                // 1. retrieve user license
                try {
                    // use email as key
                    Licensing.LicenseAssignments.Get request =
                            configuration.getLicensing().licenseAssignments().get(
                                    configuration.getProductId(),
                                    skuId,
                                    accessor.findString(GoogleAppsUtil.PRIMARY_EMAIL_ATTR));
                    execute(request,
                            new RequestResultHandler<Licensing.LicenseAssignments.Get, LicenseAssignment, Boolean>() {

                        @Override
                        public Boolean handleResult(
                                final Licensing.LicenseAssignments.Get request,
                                final LicenseAssignment value) {

                            try {
                                // 2. remove license
                                new GoogleAppsDelete(
                                        configuration,
                                        GoogleAppsUtil.LICENSE_ASSIGNMENT,
                                        new Uid(GoogleAppsUtil.generateLicenseId(
                                                value.getProductId(),
                                                value.getSkuId(),
                                                value.getUserId()))).execute();
                            } catch (Exception e) {
                                LOG.error(e, "Failed to delete license for user {0}", value.getUserId());
                                throw ConnectorException.wrap(e);
                            }
                            return true;
                        }

                        @Override
                        public Boolean handleNotFound(final IOException e) {
                            // Do nothing if not found
                            return true;
                        }
                    });
                } catch (IOException e) {
                    LOG.error(e, "Unable to find license for {0}-{1}-{2}", configuration.getProductId(), skuId,
                            accessor.findString(GoogleAppsUtil.PRIMARY_EMAIL_ATTR));
                }
            }
        }

        return uidAfterUpdate;
    }

    private Uid updateGroup(final AttributesAccessor accessor) {
        Uid uidAfterUpdate = uid;

        Directory.Groups.Patch patch = GroupHandler.update(
                configuration.getDirectory().groups(), uid.getUidValue(), accessor);
        if (null != patch) {
            uidAfterUpdate = execute(patch, new RequestResultHandler<Directory.Groups.Patch, Group, Uid>() {

                @Override
                public Uid handleResult(final Directory.Groups.Patch request, final Group value) {
                    LOG.ok("Group is Updated:{0}", value.getId());
                    return new Uid(value.getId(), value.getEtag());
                }
            });
        }

        Attribute members = accessor.find(GoogleAppsUtil.MEMBERS_ATTR);
        if (null != members && null != members.getValue()) {
            Directory.Members service = configuration.getDirectory().members();
            if (members.getValue().isEmpty()) {
                // Remove all membership
                for (Map<String, String> member : listMembers(service, uidAfterUpdate.getUidValue(), null)) {
                    execute(MembersHandler.delete(
                            service, uidAfterUpdate.getUidValue(), member.get(GoogleAppsUtil.EMAIL_ATTR)),
                            new RequestResultHandler<Directory.Members.Delete, Void, Object>() {

                        @Override
                        public Object handleResult(final Directory.Members.Delete request, final Void value) {
                            return null;
                        }

                        @Override
                        public Object handleNotFound(final IOException e) {
                            // Do nothing
                            return null;
                        }
                    });
                }
            } else {
                List<Map<String, String>> activeMembership = listMembers(service, uidAfterUpdate.getUidValue(), null);

                List<Directory.Members.Insert> addMembership = new ArrayList<>();
                List<Directory.Members.Patch> patchMembership = new ArrayList<>();

                for (Object member : members.getValue()) {
                    if (member instanceof Map) {
                        String email = (String) ((Map) member).get(GoogleAppsUtil.EMAIL_ATTR);
                        if (null == email) {
                            continue;
                        }
                        String role = (String) ((Map) member).get(GoogleAppsUtil.ROLE_ATTR);
                        if (null == role) {
                            role = "MEMBER";
                        }

                        boolean notMember = true;
                        for (Map<String, String> a : activeMembership) {
                            // How to handle ROLE update?
                            // OWNER -> MANAGER -> MEMBER
                            if (email.equalsIgnoreCase(a.get(GoogleAppsUtil.EMAIL_ATTR))) {
                                a.put("keep", null);
                                if (!role.equalsIgnoreCase(a.get(GoogleAppsUtil.ROLE_ATTR))) {
                                    patchMembership.add(MembersHandler.update(
                                            service, uidAfterUpdate.getUidValue(), email, role));
                                }
                                notMember = false;
                                break;
                            }
                        }
                        if (notMember) {
                            addMembership.add(MembersHandler.create(
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

                // Update existing Member object
                for (Directory.Members.Patch request : patchMembership) {
                    execute(request, new RequestResultHandler<Directory.Members.Patch, Member, Object>() {

                        @Override
                        public Object handleResult(final Directory.Members.Patch request, final Member value) {
                            return null;
                        }
                    });
                }

                // Delete existing Member object
                for (Map<String, String> am : activeMembership) {
                    if (!am.containsKey("keep")) {
                        execute(MembersHandler.delete(
                                service, uidAfterUpdate.getUidValue(), am.get(GoogleAppsUtil.EMAIL_ATTR)),
                                new RequestResultHandler<Directory.Members.Delete, Void, Object>() {

                            @Override
                            public Object handleResult(final Directory.Members.Delete request, final Void value) {
                                return null;
                            }

                            @Override
                            public Object handleNotFound(final IOException e) {
                                // Do nothing
                                return null;
                            }
                        });
                    }
                }
            }
        }

        List<Object> aliases = accessor.findList(GoogleAppsUtil.ALIASES_ATTR);
        if (null != aliases) {
            Directory.Groups.Aliases service = configuration.getDirectory().groups().aliases();
            Set<String> currentAliases = GroupHandler.listAliases(service, uidAfterUpdate.getUidValue());

            if (aliases.isEmpty()) {
                // Remove all aliases
                for (Object alias : currentAliases) {
                    execute(GroupHandler.deleteGroupAlias(service, uidAfterUpdate.getUidValue(), (String) alias),
                            new RequestResultHandler<Directory.Groups.Aliases.Delete, Void, Object>() {

                        @Override
                        public Object handleResult(final Directory.Groups.Aliases.Delete request, final Void value) {
                            return null;
                        }

                        @Override
                        public Object handleNotFound(final IOException e) {
                            // It may be an indirect membership, not able to delete
                            return null;
                        }
                    });
                }
            } else {
                List<Directory.Groups.Aliases.Insert> addAliases = new ArrayList<>();
                Set<String> keepAliases = CollectionUtil.newCaseInsensitiveSet();

                for (Object alias : aliases) {
                    if (currentAliases.contains(alias.toString())) {
                        keepAliases.add((String) alias);
                    } else {
                        addAliases.add(
                                GroupHandler.createGroupAlias(service, uidAfterUpdate.getUidValue(), (String) alias));
                    }
                }

                // Add new alias
                for (Directory.Groups.Aliases.Insert insert : addAliases) {
                    execute(insert, new RequestResultHandler<Directory.Groups.Aliases.Insert, Alias, Object>() {

                        @Override
                        public Object handleResult(final Directory.Groups.Aliases.Insert insert, final Alias value) {
                            return null;
                        }

                        @Override
                        public Object handleDuplicate(final IOException e) {
                            return null;
                        }
                    });
                }

                // Delete existing aliases
                if (currentAliases.removeAll(keepAliases)) {
                    for (Object alias : currentAliases) {
                        execute(GroupHandler.deleteGroupAlias(service, (String) alias, uidAfterUpdate.getUidValue()),
                                new RequestResultHandler<Directory.Groups.Aliases.Delete, Void, Object>() {

                            @Override
                            public Object handleResult(final Directory.Groups.Aliases.Delete request, final Void v) {
                                return null;
                            }

                            @Override
                            public Object handleNotFound(final IOException e) {
                                return null;
                            }
                        });
                    }
                }
            }
        }

        return uidAfterUpdate;
    }

    public Uid update(final Set<Attribute> replaceAttributes) {
        AttributesAccessor accessor = new AttributesAccessor(replaceAttributes);

        Uid uidAfterUpdate = uid;
        if (ObjectClass.ACCOUNT.equals(objectClass)) {
            uidAfterUpdate = updateUser(accessor);
        } else if (ObjectClass.GROUP.equals(objectClass)) {
            uidAfterUpdate = updateGroup(accessor);
        } else if (GoogleAppsUtil.MEMBER.equals(objectClass)) {
            String role = accessor.findString(GoogleAppsUtil.ROLE_ATTR);
            if (StringUtil.isNotBlank(role)) {
                String[] ids = uid.getUidValue().split("/");
                if (ids.length == 2) {
                    Directory.Members.Patch patch = MembersHandler.update(
                            configuration.getDirectory().members(), ids[0], ids[1], role).
                            setFields(GoogleAppsUtil.EMAIL_ETAG);
                    uidAfterUpdate = execute(patch, new RequestResultHandler<Directory.Members.Patch, Member, Uid>() {

                        @Override
                        public Uid handleResult(final Directory.Members.Patch request, final Member value) {
                            LOG.ok("Member is updated:{0}/{1}", request.getGroupKey(), value.getEmail());
                            return MembersHandler.generateUid(request.getGroupKey(), value);
                        }
                    });
                } else {
                    throw new UnknownUidException("Invalid ID format");
                }
            }
        } else if (GoogleAppsUtil.ORG_UNIT.equals(objectClass)) {
            Directory.Orgunits.Patch patch = OrgunitsHandler.update(
                    configuration.getDirectory().orgunits(), uid.getUidValue(), accessor);
            if (null != patch) {
                uidAfterUpdate = execute(patch, new RequestResultHandler<Directory.Orgunits.Patch, OrgUnit, Uid>() {

                    @Override
                    public Uid handleResult(final Directory.Orgunits.Patch request, final OrgUnit value) {
                        LOG.ok("OrgUnit is updated:{0}", value.getName());
                        return OrgunitsHandler.generateUid(value);
                    }
                });
            }
        } else if (GoogleAppsUtil.LICENSE_ASSIGNMENT.equals(objectClass)) {
            Licensing.LicenseAssignments.Patch patch = LicenseAssignmentsHandler.update(
                    configuration.getLicensing().licenseAssignments(), uid.getUidValue(), accessor);
            if (null != patch) {
                uidAfterUpdate = execute(patch,
                        new RequestResultHandler<Licensing.LicenseAssignments.Patch, LicenseAssignment, Uid>() {

                    @Override
                    public Uid handleResult(
                            final Licensing.LicenseAssignments.Patch request,
                            final LicenseAssignment value) {

                        LOG.ok("LicenseAssignment is Updated:{0}/{1}/{2}",
                                value.getProductId(), value.getSkuId(), value.getUserId());
                        return LicenseAssignmentsHandler.generateUid(value);
                    }
                });
            }
        } else {
            throw new UnsupportedOperationException("Update of type "
                    + objectClass.getObjectClassValue() + " is not supported");
        }
        return uidAfterUpdate;
    }

    private void updateDeltaUser(final Set<AttributeDelta> modifications) {
        Directory.Users.Update update = UserHandler.updateUser(
                configuration.getDirectory().users(),
                uid.getUidValue(),
                modifications,
                configuration.getCustomSchemasJSON());
        if (null != update) {
            execute(update, new RequestResultHandler<Directory.Users.Update, User, Uid>() {

                @Override
                public Uid handleResult(final Directory.Users.Update request, final User value) {
                    LOG.ok("User is Updated:{0}", value.getId());
                    return uid;
                }
            });
        }

        Set<String> groupsToAdd = new HashSet<>();
        Set<String> groupsToRemove = new HashSet<>();
        Optional.ofNullable(AttributeDeltaUtil.find(PredefinedAttributes.GROUPS_NAME, modifications)).
                ifPresent(groups -> {
                    if (CollectionUtil.isEmpty(groups.getValuesToReplace())) {
                        if (!CollectionUtil.isEmpty(groups.getValuesToAdd())) {
                            for (Object group : CollectionUtil.nullAsEmpty(groups.getValuesToAdd())) {
                                groupsToAdd.add(group.toString());
                            }
                        }

                        if (!CollectionUtil.isEmpty(groups.getValuesToRemove())) {
                            for (Object group : CollectionUtil.nullAsEmpty(groups.getValuesToRemove())) {
                                groupsToRemove.add(group.toString());
                            }
                        }
                    } else {
                        for (String groupKey : listGroups(
                                configuration.getDirectory().groups(),
                                uid.getUidValue(),
                                configuration.getDomain())) {

                            groupsToRemove.add(groupKey);
                        }

                        if (!CollectionUtil.isEmpty(groups.getValuesToAdd())) {
                            for (Object group : CollectionUtil.nullAsEmpty(groups.getValuesToReplace())) {
                                groupsToAdd.add(group.toString());
                            }
                        }
                    }
                });
        Directory.Members membersService = configuration.getDirectory().members();
        // Delete existing Member object
        for (String groupKey : groupsToRemove) {
            execute(MembersHandler.delete(membersService, groupKey, uid.getUidValue()),
                    new RequestResultHandler<Directory.Members.Delete, Void, Object>() {

                @Override
                public Object handleResult(final Directory.Members.Delete request, final Void value) {
                    return null;
                }

                @Override
                public Object handleNotFound(final IOException e) {
                    // It may be an indirect membership, not able to delete
                    return null;
                }
            });
        }
        // Add new Member object
        for (String groupKey : groupsToAdd) {
            execute(MembersHandler.create(membersService, groupKey, uid, null),
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

        Directory.Users.Aliases aliasService = configuration.getDirectory().users().aliases();
        Set<String> aliasesToAdd = new HashSet<>();
        Set<String> aliasesToRemove = new HashSet<>();
        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.ALIASES_ATTR, modifications)).
                ifPresent(aliases -> {
                    if (CollectionUtil.isEmpty(aliases.getValuesToReplace())) {
                        if (!CollectionUtil.isEmpty(aliases.getValuesToAdd())) {
                            for (Object alias : CollectionUtil.nullAsEmpty(aliases.getValuesToAdd())) {
                                aliasesToAdd.add(alias.toString());
                            }
                        }

                        if (!CollectionUtil.isEmpty(aliases.getValuesToRemove())) {
                            for (Object alias : CollectionUtil.nullAsEmpty(aliases.getValuesToRemove())) {
                                aliasesToRemove.add(alias.toString());
                            }
                        }
                    } else {
                        aliasesToRemove.addAll(UserHandler.listAliases(aliasService, uid.getUidValue()));

                        if (!CollectionUtil.isEmpty(aliases.getValuesToAdd())) {
                            for (Object group : CollectionUtil.nullAsEmpty(aliases.getValuesToReplace())) {
                                aliasesToAdd.add(group.toString());
                            }
                        }
                    }
                });
        // Delete existing aliases
        for (String alias : aliasesToRemove) {
            execute(UserHandler.deleteUserAlias(aliasService, alias, uid.getUidValue()),
                    new RequestResultHandler<Directory.Users.Aliases.Delete, Void, Object>() {

                @Override
                public Object handleResult(final Directory.Users.Aliases.Delete request, final Void v) {
                    return null;
                }

                @Override
                public Object handleNotFound(final IOException e) {
                    return null;
                }
            });
        }
        // Add new aliases
        for (String alias : aliasesToAdd) {
            execute(UserHandler.createUserAlias(aliasService, uid.getUidValue(), alias),
                    new RequestResultHandler<Directory.Users.Aliases.Insert, Alias, Object>() {

                @Override
                public Object handleResult(final Directory.Users.Aliases.Insert insert, final Alias value) {
                    return null;
                }

                @Override
                public Object handleDuplicate(final IOException e) {
                    return null;
                }
            });
        }
    }

    private void updateDeltaGroup(final Set<AttributeDelta> modifications) {
        Directory.Groups.Update update = GroupHandler.update(
                configuration.getDirectory().groups(), uid.getUidValue(), modifications);
        if (null != update) {
            execute(update, new RequestResultHandler<Directory.Groups.Update, Group, Uid>() {

                @Override
                public Uid handleResult(final Directory.Groups.Update request, final Group value) {
                    LOG.ok("Group is Updated:{0}", value.getId());
                    return uid;
                }
            });
        }

        Directory.Members membersService = configuration.getDirectory().members();

        Set<String> membersToAdd = new HashSet<>();
        Set<String> membersToRemove = new HashSet<>();
        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.MEMBERS_ATTR, modifications)).
                ifPresent(members -> {
                    if (CollectionUtil.isEmpty(members.getValuesToReplace())) {
                        if (!CollectionUtil.isEmpty(members.getValuesToAdd())) {
                            for (Object group : CollectionUtil.nullAsEmpty(members.getValuesToAdd())) {
                                membersToAdd.add(group.toString());
                            }
                        }

                        if (!CollectionUtil.isEmpty(members.getValuesToRemove())) {
                            for (Object group : CollectionUtil.nullAsEmpty(members.getValuesToRemove())) {
                                membersToRemove.add(group.toString());
                            }
                        }
                    } else {
                        for (Map<String, String> member : listMembers(
                                membersService,
                                uid.getUidValue(),
                                null)) {

                            membersToRemove.add(member.get(GoogleAppsUtil.EMAIL_ATTR));
                        }

                        if (!CollectionUtil.isEmpty(members.getValuesToAdd())) {
                            for (Object group : CollectionUtil.nullAsEmpty(members.getValuesToReplace())) {
                                membersToAdd.add(group.toString());
                            }
                        }
                    }
                });
        // Remove all membership
        for (String member : membersToRemove) {
            execute(MembersHandler.delete(membersService, uid.getUidValue(), member),
                    new RequestResultHandler<Directory.Members.Delete, Void, Object>() {

                @Override
                public Object handleResult(final Directory.Members.Delete request, final Void value) {
                    return null;
                }

                @Override
                public Object handleNotFound(final IOException e) {
                    // Do nothing
                    return null;
                }
            });
        }
        // Add new Member object
        for (String member : membersToAdd) {
            execute(MembersHandler.create(membersService, uid.getUidValue(), member, null),
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

        Directory.Groups.Aliases aliasService = configuration.getDirectory().groups().aliases();
        Set<String> aliasesToAdd = new HashSet<>();
        Set<String> aliasesToRemove = new HashSet<>();
        Optional.ofNullable(AttributeDeltaUtil.find(GoogleAppsUtil.ALIASES_ATTR, modifications)).
                ifPresent(aliases -> {
                    if (CollectionUtil.isEmpty(aliases.getValuesToReplace())) {
                        if (!CollectionUtil.isEmpty(aliases.getValuesToAdd())) {
                            for (Object alias : CollectionUtil.nullAsEmpty(aliases.getValuesToAdd())) {
                                aliasesToAdd.add(alias.toString());
                            }
                        }

                        if (!CollectionUtil.isEmpty(aliases.getValuesToRemove())) {
                            for (Object alias : CollectionUtil.nullAsEmpty(aliases.getValuesToRemove())) {
                                aliasesToRemove.add(alias.toString());
                            }
                        }
                    } else {
                        aliasesToRemove.addAll(GroupHandler.listAliases(aliasService, uid.getUidValue()));

                        if (!CollectionUtil.isEmpty(aliases.getValuesToAdd())) {
                            for (Object group : CollectionUtil.nullAsEmpty(aliases.getValuesToReplace())) {
                                aliasesToAdd.add(group.toString());
                            }
                        }
                    }
                });
        // Delete existing aliases
        for (String alias : aliasesToRemove) {
            execute(GroupHandler.deleteGroupAlias(aliasService, alias, uid.getUidValue()),
                    new RequestResultHandler<Directory.Groups.Aliases.Delete, Void, Object>() {

                @Override
                public Object handleResult(final Directory.Groups.Aliases.Delete request, final Void v) {
                    return null;
                }

                @Override
                public Object handleNotFound(final IOException e) {
                    return null;
                }
            });
        }
        // Add new aliases
        for (String alias : aliasesToAdd) {
            execute(GroupHandler.createGroupAlias(aliasService, uid.getUidValue(), alias),
                    new RequestResultHandler<Directory.Groups.Aliases.Insert, Alias, Object>() {

                @Override
                public Object handleResult(final Directory.Groups.Aliases.Insert insert, final Alias value) {
                    return null;
                }

                @Override
                public Object handleDuplicate(final IOException e) {
                    return null;
                }
            });
        }
    }

    public Set<AttributeDelta> updateDelta(final Set<AttributeDelta> modifications) {
        if (ObjectClass.ACCOUNT.equals(objectClass)) {
            updateDeltaUser(modifications);
        } else if (ObjectClass.GROUP.equals(objectClass)) {
            updateDeltaGroup(modifications);
        } else if (GoogleAppsUtil.ORG_UNIT.equals(objectClass)) {
            Directory.Orgunits.Update update = OrgunitsHandler.update(
                    configuration.getDirectory().orgunits(), uid.getUidValue(), modifications);
            if (null != update) {
                execute(update, new RequestResultHandler<Directory.Orgunits.Update, OrgUnit, Uid>() {

                    @Override
                    public Uid handleResult(final Directory.Orgunits.Update request, final OrgUnit value) {
                        LOG.ok("OrgUnit is updated:{0}", value.getName());
                        return uid;
                    }
                });
            }
        } else {
            throw new UnsupportedOperationException("Update delta of type "
                    + objectClass.getObjectClassValue() + " is not supported");
        }

        return modifications;
    }
}
