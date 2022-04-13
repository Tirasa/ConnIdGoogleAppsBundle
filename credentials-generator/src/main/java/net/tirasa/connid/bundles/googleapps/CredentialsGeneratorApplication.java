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

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SpringBootApplication
@EnableConfigurationProperties
public class CredentialsGeneratorApplication implements CommandLineRunner {

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

    public static final String ADMIN_DIRECTORY_GROUP =
            "https://www.googleapis.com/auth/admin.directory.group";

    public static final String ADMIN_DIRECTORY_ORGUNIT =
            "https://www.googleapis.com/auth/admin.directory.orgunit";

    public static final String ADMIN_DIRECTORY_USER =
            "https://www.googleapis.com/auth/admin.directory.user";

    public static final String ADMIN_ENTERPRISE_LICENSE =
            "https://www.googleapis.com/auth/apps.licensing";

    public static final Map<String, Object> configMap = new LinkedHashMap<String, Object>(3);

    public static final JsonFactory JSON_FACTORY = new JacksonFactory();

    public static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    private static final List<String> SCOPES = Arrays.asList(
            ADMIN_DIRECTORY_GROUP,
            ADMIN_DIRECTORY_ORGUNIT,
            ADMIN_DIRECTORY_USER,
            ADMIN_ENTERPRISE_LICENSE);

    @Value("${redirect.uri}")
    private String redirectUri;

    public static void main(final String[] args) {
        SpringApplication sa = new SpringApplication(CredentialsGeneratorApplication.class);
        sa.setLogStartupInfo(false);
        sa.setBannerMode(Banner.Mode.OFF);
        sa.run(args);
    }

    private void getConfigurationMap(File clientJson) throws IOException {
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new FileReader(clientJson));
        configMap.put("clientId", clientSecrets.getDetails().getClientId());
        configMap.put("clientSecret", clientSecrets.getDetails().getClientSecret());

        System.out.println("Request Url is " + new GoogleAuthorizationCodeRequestUrl(
                clientSecrets.getDetails().getClientId(),
                redirectUri, SCOPES)
                .setState("/profile").build());
    }

    @Bean
    public ServletRegistrationBean codeProcessorServlet() {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setServlet(new CodeProcessorServlet(redirectUri));
        srb.setUrlMappings(Arrays.asList("/code-processor/*"));
        return srb;
    }

    @Override
    public void run(String... args) throws IOException {
        if (args.length == 1) {
            File clientJson = new File(args[0]);
            if (clientJson.isDirectory()) {
                clientJson = new File(clientJson, CLIENTSECRETS_LOCATION);
            }

            if (clientJson.exists() && clientJson.isFile()) {
                getConfigurationMap(clientJson);
            } else {
                System.err.println("Invalid client secret path. File not exists " + clientJson);
            }
        }
    }
}
