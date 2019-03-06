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

import static org.junit.Assert.assertTrue;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.APIConfiguration;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.api.ConnectorFacadeFactory;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.BeforeClass;
import org.junit.Test;

public class GoogleAppsConnectorUnitTests {

    private static final Log LOG = Log.getLog(GoogleAppsConnectorUnitTests.class);

    private static ConnectorFacade connector;

    private static GoogleAppsConfiguration connectorConfiguration;

    @BeforeClass
    public static void setUp() {
        connectorConfiguration = new GoogleAppsConfiguration();

        connectorConfiguration.setClientId("aclientid");
        connectorConfiguration.setClientSecret(new GuardedString("aclientsecret".toCharArray()));
        connectorConfiguration.setDomain("adomain");
        connectorConfiguration.setProjection("full");
        connectorConfiguration.setRefreshToken(new GuardedString("arefreshtoken".toCharArray()));
        connectorConfiguration.setCustomSchemasJSON("[{\n" + "\"name\": \"Classificazione\",\n"
                + "\"multiValued\": false,\n" + "\"type\": \"object\",\n" + "\"innerSchemas\": [{\n"
                + "\"name\": \"Funzionale\",\n" + "\"multiValued\": false,\n" + "\"type\": \"boolean\",\n"
                + "\"innerSchemas\": []\n" + "},\n" + "{\n" + "\"name\": \"Multivalue\",\n" + "\"multiValued\": true,\n"
                + "\"type\": \"String\",\n" + "\"innerSchemas\": []\n" + "}]\n" + "},\n" + "{\n"
                + "\"name\": \"Classificazione2\",\n" + "\"multiValued\": false,\n" + "\"type\": \"object\",\n"
                + "\"innerSchemas\": [{\n" + "\"name\": \"Funzionale2\",\n" + "\"multiValued\": false,\n"
                + "\"type\": \"boolean\",\n" + "\"innerSchemas\": []\n" + "}]\n" + "}]");
        connector = newFacade();
    }

    private static ConnectorFacade newFacade() {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration impl = TestHelpers.createTestConfiguration(GoogleAppsConnector.class, connectorConfiguration);
        impl.getResultsHandlerConfiguration().setFilteredResultsHandlerInValidationMode(true);
        return factory.newInstance(impl);
    }

    @Test
    public void validate() {
        connector.validate();
    }

    @Test
    public void schema() {
        Schema schema = connector.schema();
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
