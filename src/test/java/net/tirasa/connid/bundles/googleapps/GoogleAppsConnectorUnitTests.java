/**
 * Copyright © 2018 ConnId (connid-dev@googlegroups.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tirasa.connid.bundles.googleapps;

import static org.junit.jupiter.api.Assertions.assertTrue;

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

public class GoogleAppsConnectorUnitTests {

    private static ConnectorFacade CONNECTOR;

    private static GoogleAppsConfiguration CONN_CONF;

    @BeforeAll
    public static void setUp() {
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

    private static ConnectorFacade newFacade() {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration impl = TestHelpers.createTestConfiguration(GoogleAppsConnector.class, CONN_CONF);
        impl.getResultsHandlerConfiguration().setFilteredResultsHandlerInValidationMode(true);
        return factory.newInstance(impl);
    }

    @Test
    public void validate() {
        CONNECTOR.validate();
    }

    @Test
    public void schema() {
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
