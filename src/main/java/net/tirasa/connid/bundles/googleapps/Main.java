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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AbstractPromptReceiver;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleOAuthConstants;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;

/**
 * Class to represent a GoogleApps Connector Configuration Class.
 *
 * @author Laszlo Hordos
 */
public class Main {

    // Path to client_secrets.json which should contain a JSON document such as:
    // {
    // "web or installed": {
    // "client_id": "[[YOUR_CLIENT_ID]]",
    // "client_secret": "[[YOUR_CLIENT_SECRET]]",
    // "auth_uri": "https://accounts.google.com/o/oauth2/auth",
    // "token_uri": "https://accounts.google.com/o/oauth2/token"
    // }
    // }
    private static final String CLIENTSECRETS_LOCATION = "client_secrets.json";

    public static final java.lang.String ADMIN_DIRECTORY_GROUP =
            "https://www.googleapis.com/auth/admin.directory.group";

    public static final java.lang.String ADMIN_DIRECTORY_ORGUNIT =
            "https://www.googleapis.com/auth/admin.directory.orgunit";

    public static final java.lang.String ADMIN_DIRECTORY_USER =
            "https://www.googleapis.com/auth/admin.directory.user";

    public static final java.lang.String ADMIN_ENTERPRISE_LICENSE =
            "https://www.googleapis.com/auth/apps.licensing";

    private static final List<String> SCOPES = Arrays.asList(
            ADMIN_DIRECTORY_GROUP,
            ADMIN_DIRECTORY_ORGUNIT,
            ADMIN_DIRECTORY_USER,
            ADMIN_ENTERPRISE_LICENSE);

    /**
     * Global instance of the HTTP transport.
     */
    private static final HttpTransport HTTP_TRANSPORT;

    static {
        HttpTransport t = null;
        try {
            t = GoogleNetHttpTransport.newTrustedTransport();
        } catch (Exception e) {
            try {
                t = new NetHttpTransport.Builder().doNotValidateCertificate().build();
            } catch (GeneralSecurityException e1) {
            }
        }
        HTTP_TRANSPORT = t;
    }

    /**
     * Global instance of the JSON factory.
     */
    private static final JsonFactory JSON_FACTORY = new JacksonFactory();

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            File clientJson = new File(args[0]);
            if (clientJson.isDirectory()) {
                clientJson = new File(clientJson, CLIENTSECRETS_LOCATION);
            }

            if (clientJson.exists() && clientJson.isFile()) {
                Map<String, Object> configMap = getConfigurationMap(clientJson);
                System.out.println(JSON_FACTORY.toPrettyString(configMap));
                return;
            } else {
                System.err.println("Invalid client secret path. File not exits " + clientJson);
            }
        }

        String fileName = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI()).
                getName();
        System.out.print("Usage: java -jar " + fileName + " <path to client_secrets.json>");
    }

    static Map<String, Object> getConfigurationMap(File clientJson) throws IOException, URISyntaxException {

        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new FileReader(clientJson));

        Credential credential =
                new AuthorizationCodeInstalledApp(
                        new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES).
                        setAccessType("offline").setApprovalPrompt("force").
                        setDataStoreFactory(MemoryDataStoreFactory.getDefaultInstance()).build(),
                        new AbstractPromptReceiver() {

                    @Override
                    public String getRedirectUri() throws IOException {
                        return GoogleOAuthConstants.OOB_REDIRECT_URI;
                    }
                }).authorize("user");

        Map<String, Object> configMap = new LinkedHashMap<String, Object>(3);
        configMap.put("clientId", clientSecrets.getDetails().getClientId());
        configMap.put("clientSecret", clientSecrets.getDetails().getClientSecret());
        configMap.put("refreshToken", credential.getRefreshToken());
        return configMap;
    }

}
