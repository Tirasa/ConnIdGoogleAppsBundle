/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2016 ConnId All Rights Reserved
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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GoogleAppsConnectorUnitTests {

    public static class GoogleAppsTestConnector extends GoogleAppsConnector {

        @Override
        public void checkAlive() {
            // nothing to do
        }
    }

    private static ConnectorFacade CONNECTOR;

    private static GoogleAppsConfiguration CONN_CONF;

    private static ConnectorFacade newFacade() {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration impl = TestHelpers.createTestConfiguration(GoogleAppsTestConnector.class, CONN_CONF);
        impl.getResultsHandlerConfiguration().setFilteredResultsHandlerInValidationMode(true);
        return factory.newInstance(impl);
    }

    @BeforeAll
    static void setUp() throws IOException {
        CONN_CONF = new GoogleAppsConfiguration();

        CONN_CONF.setClientId("aclientid");
        CONN_CONF.setClientSecret(new GuardedString("aclientsecret".toCharArray()));
        CONN_CONF.setDomain("adomain");
        CONN_CONF.setProjection("full");
        CONN_CONF.setRefreshToken(new GuardedString("arefreshtoken".toCharArray()));
        CONN_CONF.setCustomSchemasJSON("[{"
                + "\"name\": \"Classificazione\","
                + "\"multiValued\": false,"
                + "\"type\": \"object\","
                + "\"innerSchemas\": [{"
                + "\"name\": \"Funzionale\","
                + "\"multiValued\": false,"
                + "\"type\": \"boolean\","
                + "\"innerSchemas\": []"
                + "},"
                + "{"
                + "\"name\": \"Multivalue\","
                + "\"multiValued\": true,"
                + "\"type\": \"String\","
                + "\"innerSchemas\": []"
                + "}]"
                + "},"
                + "{"
                + "\"name\": \"Classificazione2\","
                + "\"multiValued\": false,"
                + "\"type\": \"object\","
                + "\"innerSchemas\": [{"
                + "\"name\": \"Funzionale2\","
                + "\"multiValued\": false,"
                + "\"type\": \"boolean\","
                + "\"innerSchemas\": []"
                + "}]"
                + "}]");

        CONNECTOR = newFacade();
    }

    @Test
    void validate() {
        CONNECTOR.validate();
    }

    @Test
    void schema() {
        Schema schema = CONNECTOR.schema();
        boolean accountFound = false;
        boolean customSingleValuedSchemaFound1 = false;
        boolean customSingleValuedSchemaFound2 = false;
        boolean customMultivaluedSchemaFound = false;
        for (ObjectClassInfo oci : schema.getObjectClassInfo()) {
            if (ObjectClass.ACCOUNT_NAME.equals(oci.getType())) {
                accountFound = true;
                for (AttributeInfo attributeInfo : oci.getAttributeInfo()) {
                    if ("Classificazione.Funzionale".equals(attributeInfo.getName())) {
                        customSingleValuedSchemaFound1 = true;
                    }
                    if ("Classificazione.Multivalue".equals(attributeInfo.getName())) {
                        customMultivaluedSchemaFound = true;
                    }
                    if ("Classificazione2.Funzionale2".equals(attributeInfo.getName())) {
                        customSingleValuedSchemaFound2 = true;
                    }
                }
            }
        }
        assertTrue(accountFound);
        assertTrue(customSingleValuedSchemaFound1);
        assertTrue(customSingleValuedSchemaFound2);
        assertTrue(customMultivaluedSchemaFound);
    }
}
