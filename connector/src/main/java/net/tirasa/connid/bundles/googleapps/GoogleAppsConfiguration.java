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
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.apache.v2.ApacheHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.directory.Directory;
import com.google.api.services.directory.DirectoryScopes;
import com.google.api.services.licensing.Licensing;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.http.HttpHost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.common.security.SecurityUtil;
import org.identityconnectors.framework.common.exceptions.ConfigurationException;
import org.identityconnectors.framework.spi.AbstractConfiguration;
import org.identityconnectors.framework.spi.ConfigurationProperty;
import org.identityconnectors.framework.spi.StatefulConfiguration;

/**
 * Extends the {@link AbstractConfiguration} class to provide all the necessary
 * parameters to initialize the GoogleApps Connector.
 */
public class GoogleAppsConfiguration extends AbstractConfiguration implements StatefulConfiguration {

    private static final Log LOG = Log.getLog(GoogleAppsConfiguration.class);

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new GsonFactory();

    private static final String APPLICATION_NAME = "ConnId";

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

    private GoogleCredentials googleCredentials = null;

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

    public void setDomain(final String domain) {
        this.domain = domain;
    }

    @ConfigurationProperty(order = 2, displayMessageKey = "clientid.display",
            groupMessageKey = "basic.group", helpMessageKey = "clientid.help", required = true,
            confidential = false)
    public String getClientId() {
        return clientId;
    }

    public void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    @ConfigurationProperty(order = 3, displayMessageKey = "clientsecret.display",
            groupMessageKey = "basic.group", helpMessageKey = "clientsecret.help", required = true,
            confidential = true)
    public GuardedString getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(final GuardedString clientSecret) {
        this.clientSecret = clientSecret;
    }

    @ConfigurationProperty(order = 4, displayMessageKey = "refreshtoken.display",
            groupMessageKey = "basic.group", helpMessageKey = "refreshtoken.help", required = true,
            confidential = true)
    public GuardedString getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(final GuardedString refreshToken) {
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

    private void initGoogleCredentials() {
        synchronized (this) {
            if (null == googleCredentials) {
                UserCredentials.Builder credentialsBuilder = UserCredentials.newBuilder();

                Optional<String> httpProxyHost = Optional.ofNullable(System.getProperty("http.proxyHost"));
                Optional<String> httpProxyPort = Optional.ofNullable(System.getProperty("http.proxyPort"));
                Optional<String> httpsProxyHost = Optional.ofNullable(System.getProperty("https.proxyHost"));
                Optional<String> httpsProxyPort = Optional.ofNullable(System.getProperty("https.proxyPort"));
                Optional<String> socksProxyHost = Optional.ofNullable(System.getProperty("socksProxyHost"));
                Optional<String> socksProxyPort = Optional.ofNullable(System.getProperty("socksProxyPort"));
                Proxy.Type proxyType =
                        (httpProxyHost.isPresent() && httpProxyPort.isPresent())
                        || (httpsProxyHost.isPresent() && httpsProxyPort.isPresent())
                        ? Proxy.Type.HTTP
                        : socksProxyHost.isPresent() && socksProxyPort.isPresent()
                        ? Proxy.Type.SOCKS
                        : Proxy.Type.DIRECT;

                if (Proxy.Type.HTTP == proxyType) {
                    HttpClientBuilder clientBuilder = HttpClientBuilder.create();
                    clientBuilder.useSystemProperties();
                    clientBuilder.setProxy(httpsProxyHost.isPresent() && httpsProxyPort.isPresent()
                            ? new HttpHost(httpsProxyHost.orElseThrow(),
                                    Integer.parseInt(httpsProxyPort.orElseThrow()))
                            : new HttpHost(httpProxyHost.orElseThrow(),
                                    Integer.parseInt(httpProxyPort.orElseThrow())));
                    credentialsBuilder.setHttpTransportFactory(new HttpTransportFactory() {

                        @Override
                        public HttpTransport create() {
                            return new ApacheHttpTransport(clientBuilder.build());
                        }
                    });
                }
                credentialsBuilder.setClientId(getClientId()).setClientSecret(SecurityUtil.decrypt(getClientSecret()));

                getRefreshToken().access(chars -> credentialsBuilder.setRefreshToken(new String(chars)));

                UserCredentials userCredentials = credentialsBuilder.build();

                googleCredentials = userCredentials.createScoped(Arrays.asList(
                        DirectoryScopes.ADMIN_DIRECTORY_USER,
                        DirectoryScopes.ADMIN_DIRECTORY_USER_ALIAS,
                        DirectoryScopes.ADMIN_DIRECTORY_USERSCHEMA,
                        DirectoryScopes.ADMIN_DIRECTORY_ORGUNIT,
                        DirectoryScopes.ADMIN_DIRECTORY_DOMAIN,
                        DirectoryScopes.ADMIN_DIRECTORY_GROUP,
                        DirectoryScopes.ADMIN_DIRECTORY_GROUP_MEMBER));

                HttpTransport httpTransport = Proxy.Type.DIRECT == proxyType
                        ? new NetHttpTransport()
                        : new NetHttpTransport.Builder().setProxy(new Proxy(proxyType,
                                Proxy.Type.SOCKS == proxyType
                                        ? new InetSocketAddress(socksProxyHost.orElseThrow(),
                                                Integer.parseInt(socksProxyHost.orElseThrow()))
                                        : httpsProxyHost.isPresent() && httpsProxyPort.isPresent()
                                        ? new InetSocketAddress(httpsProxyHost.orElseThrow(),
                                                Integer.parseInt(httpsProxyPort.orElseThrow()))
                                        : new InetSocketAddress(httpProxyHost.orElseThrow(),
                                                Integer.parseInt(httpProxyPort.orElseThrow()))))
                                .build();

                HttpRequestInitializer requestInitializer = new HttpCredentialsAdapter(googleCredentials);
                directory = new Directory.Builder(httpTransport, JSON_FACTORY, requestInitializer).
                        setApplicationName(APPLICATION_NAME).
                        build();
                licensing = new Licensing.Builder(httpTransport, JSON_FACTORY, requestInitializer).
                        setApplicationName(APPLICATION_NAME).
                        build();
            }
        }
    }

    public void test() throws IOException {
        initGoogleCredentials();
        googleCredentials.refreshIfExpired();
    }

    @Override
    public void release() {
        googleCredentials = null;
    }

    public Directory getDirectory() {
        initGoogleCredentials();
        return directory;
    }

    public Licensing getLicensing() {
        initGoogleCredentials();
        return licensing;
    }
}
