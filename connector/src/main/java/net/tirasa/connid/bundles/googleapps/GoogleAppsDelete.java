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

import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.services.directory.DirectoryRequest;
import com.google.api.services.licensing.LicensingRequest;
import com.google.api.services.licensing.model.Empty;
import java.io.IOException;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.Uid;

public class GoogleAppsDelete {

    private static final Log LOG = Log.getLog(GoogleAppsDelete.class);

    private final GoogleAppsConfiguration configuration;

    private final ObjectClass objectClass;

    private final Uid uid;

    public GoogleAppsDelete(
            final GoogleAppsConfiguration configuration,
            final ObjectClass objectClass,
            final Uid uid) {

        this.configuration = configuration;
        this.objectClass = objectClass;
        this.uid = uid;
    }

    public void execute() {
        DirectoryRequest<Void> directoryRequest = null;
        LicensingRequest<Empty> licesingRequest = null;

        try {
            if (ObjectClass.ACCOUNT.equals(objectClass)) {
                directoryRequest = configuration.getDirectory().users().delete(uid.getUidValue());
            } else if (ObjectClass.GROUP.equals(objectClass)) {
                directoryRequest = configuration.getDirectory().groups().delete(uid.getUidValue());
            } else if (GoogleAppsUtil.MEMBER.equals(objectClass)) {
                String[] ids = uid.getUidValue().split("/");
                if (ids.length == 2) {
                    directoryRequest = configuration.getDirectory().members().delete(ids[0], ids[1]);
                } else {
                    throw new UnknownUidException("Invalid ID format");
                }
            } else if (GoogleAppsUtil.ORG_UNIT.equals(objectClass)) {
                directoryRequest = configuration.getDirectory().orgunits().
                        delete(GoogleAppsUtil.MY_CUSTOMER_ID, uid.getUidValue());
            } else if (GoogleAppsUtil.LICENSE_ASSIGNMENT.equals(objectClass)) {
                licesingRequest = LicenseAssignmentsHandler.delete(
                        configuration.getLicensing().licenseAssignments(), uid.getUidValue());
            }
        } catch (IOException e) {
            throw ConnectorException.wrap(e);
        }

        if (null == directoryRequest && null == licesingRequest) {
            LOG.warn("Delete of type {0} is not supported", configuration.getConnectorMessages()
                    .format(objectClass.getDisplayNameKey(), objectClass.getObjectClassValue()));
            throw new UnsupportedOperationException("Delete of type"
                    + objectClass.getObjectClassValue() + " is not supported");
        }

        if (directoryRequest != null) {
            GoogleApiExecutor.execute(directoryRequest,
                    new RequestResultHandler<AbstractGoogleJsonClientRequest<Void>, Void, Void>() {

                @Override
                public Void handleResult(final AbstractGoogleJsonClientRequest<Void> request, final Void value) {
                    return null;
                }

                @Override
                public Void handleNotFound(final IOException e) {
                    throw new UnknownUidException(uid, objectClass);
                }
            });
        }
        if (licesingRequest != null) {
            GoogleApiExecutor.execute(licesingRequest,
                    new RequestResultHandler<AbstractGoogleJsonClientRequest<Empty>, Empty, Empty>() {

                @Override
                public Empty handleResult(final AbstractGoogleJsonClientRequest<Empty> request, final Empty value) {
                    return null;
                }

                @Override
                public Empty handleNotFound(final IOException e) {
                    throw new UnknownUidException(uid, objectClass);
                }
            });
        }
    }
}
