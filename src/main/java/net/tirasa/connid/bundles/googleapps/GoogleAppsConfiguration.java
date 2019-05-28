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

import com.fasterxml.jackson.core.type.TypeReference;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.framework.spi.StatefulConfiguration;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.admin.directory.Directory;
import com.google.api.services.licensing.Licensing;
import java.io.IOException;
import java.util.List;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;

/**
 * Extends the {@link AbstractConfiguration} class to provide all the necessary
 * parameters to initialize the GoogleApps Connector.
 */
public class GoogleAppsConfiguration extends AbstractConfiguration implements StatefulConfiguration {

    private static final Log LOG = Log.getLog(GoogleAppsConfiguration.class);

    /**
     * Global instance of the HTTP transport.
     */
    private static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private String domain = null;

    /**
     * Client identifier issued to the client during the registration process.
     */
    private String clientId;

    /**
     * Client secret or {@code null} for none.
     */
    private GuardedString clientSecret = null;

    private GuardedString refreshToken = null;

    private Credential credential = null;

    private Directory directory;

    private Licensing licensing;

    private String projection = "basic";

    private String customSchemaJSON;

    private String[] skuIds = {};
    
    private String productId;
    
    private boolean removeLicenseOnDisable = false;
    
    @ConfigurationProperty(order = 1, displayMessageKey = "domain.display",
            groupMessageKey = "basic.group", helpMessageKey = "domain.help", required = true,
            confidential = false)
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    @ConfigurationProperty(order = 2, displayMessageKey = "clientid.display",
            groupMessageKey = "basic.group", helpMessageKey = "clientid.help", required = true,
            confidential = false)
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @ConfigurationProperty(order = 3, displayMessageKey = "clientsecret.display",
            groupMessageKey = "basic.group", helpMessageKey = "clientsecret.help", required = true,
            confidential = true)
    public GuardedString getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(GuardedString clientSecret) {
        this.clientSecret = clientSecret;
    }

    @ConfigurationProperty(order = 4, displayMessageKey = "refreshtoken.display",
            groupMessageKey = "basic.group", helpMessageKey = "refreshtoken.help", required = true,
            confidential = true)
    public GuardedString getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(GuardedString refreshToken) {
        this.refreshToken = refreshToken;
    }

    @ConfigurationProperty(order = 5, displayMessageKey = "search.projection",
            groupMessageKey = "basic.group", helpMessageKey = "search.projection.help", required = false,
            confidential = false)
    public String getProjection() {
        return projection;
    }

    public void setProjection(final String projection) {
        this.projection = projection;
    }

    @ConfigurationProperty(displayMessageKey = "customSchemaJSON.display",
            helpMessageKey = "customSchemaJSON.help", order = 6)
    public String getCustomSchemasJSON() {
        return customSchemaJSON;
    }

    public void setCustomSchemasJSON(final String customAttributesJSON) {
        this.customSchemaJSON = customAttributesJSON;
    }

     @ConfigurationProperty(displayMessageKey = "skuIds.display",
            helpMessageKey = "skuIds.help", required = false, order = 7)
    public String[] getSkuIds() {
        return skuIds;
    }

    public void setSkuIds(final String[] skuIds) {
        this.skuIds = skuIds;
    }

     @ConfigurationProperty(displayMessageKey = "productId.display",
            helpMessageKey = "productId.help", required = false, order = 8)
    public String getProductId() {
        return productId;
    }

    public void setProductId(final String productId) {
        this.productId = productId;
    }

    @ConfigurationProperty(displayMessageKey = "removeLicenseOnDisable.display",
            helpMessageKey = "removeLicenseOnDisable.help", required = false, order = 9)
    public boolean getRemoveLicenseOnDisable() {
        return removeLicenseOnDisable;
    }

    public void setRemoveLicenseOnDisable(final boolean removeLicenseOnDisable) {
        this.removeLicenseOnDisable = removeLicenseOnDisable;
    }

    @Override
    public void validate() {
        if (StringUtil.isBlank(domain)) {
            throw new IllegalArgumentException("Domain cannot be null or empty.");
        }
        if (StringUtil.isBlank(clientId)) {
            throw new IllegalArgumentException("Client Id cannot be null or empty.");
        }
        if (null == clientSecret) {
            throw new IllegalArgumentException("Client Secret cannot be null.");
        }
        if (null == refreshToken) {
            throw new IllegalArgumentException("Refresh Token cannot be null.");
        }
        if (StringUtil.isNotBlank(projection)
                && !"basic".equals(projection)
                && !"full".equals(projection)
                && !"custom".equals(projection)) {
            throw new IllegalArgumentException("Projection must be a value among [basic, full, custom]");
        }
        if (StringUtil.isNotBlank(customSchemaJSON)) {
            try {
                GoogleAppsUtil.MAPPER.readValue(customSchemaJSON, new TypeReference<List<GoogleAppsCustomSchema>>() {
                });
            } catch (IOException e) {
                LOG.error(e, "While validating customSchemaJSON");
                throw new ConfigurationException("'customSchemaJSON' parameter must be a valid JSON.");
            }
        }
    }

    public Credential getGoogleCredential() {
        synchronized (this) {
            if (null == credential) {
                credential = new GoogleCredential.Builder().
                        setTransport(HTTP_TRANSPORT).
                        setJsonFactory(JSON_FACTORY).
                        setClientAuthentication(new ClientParametersAuthentication(
                                getClientId(),
                                SecurityUtil.decrypt(getClientSecret()))).
                        build();

                getRefreshToken().access(new GuardedString.Accessor() {

                    @Override
                    public void access(char[] chars) {
                        credential.setRefreshToken(new String(chars));
                    }
                });

                directory = new Directory.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).
                        setApplicationName("ConnId").
                        build();
                licensing = new Licensing.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).
                        setApplicationName("ConnId").
                        build();
            }
        }
        return credential;
    }

    @Override
    public void release() {
    }

    public Directory getDirectory() {
        getGoogleCredential();
        return directory;
    }

    public Licensing getLicensing() {
        getGoogleCredential();
        if (null == licensing) {
            throw new ConnectorException("Licensing is not enabled");
        }
        return licensing;
    }

}
