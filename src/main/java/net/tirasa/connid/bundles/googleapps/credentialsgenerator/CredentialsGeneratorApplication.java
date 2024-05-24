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
package net.tirasa.connid.bundles.googleapps.credentialsgenerator;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import java.awt.Desktop;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties
public class CredentialsGeneratorApplication implements CommandLineRunner {

    private static final Logger LOG = LoggerFactory.getLogger(CredentialsGeneratorApplication.class);

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

    private static final String ADMIN_DIRECTORY_GROUP =
            "https://www.googleapis.com/auth/admin.directory.group";

    private static final String ADMIN_DIRECTORY_ORGUNIT =
            "https://www.googleapis.com/auth/admin.directory.orgunit";

    private static final String ADMIN_DIRECTORY_USER =
            "https://www.googleapis.com/auth/admin.directory.user";

    private static final String ADMIN_ENTERPRISE_LICENSE =
            "https://www.googleapis.com/auth/apps.licensing";

    public static final Map<String, Object> CONFIG_MAP = new LinkedHashMap<>(3);

    public static final JsonFactory JSON_FACTORY = new GsonFactory();

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
        sa.setHeadless(false);
        sa.run(args);
    }

    private void getConfigurationMap(final File clientJson) throws IOException, URISyntaxException {
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new FileReader(clientJson));
        CONFIG_MAP.put("clientId", clientSecrets.getDetails().getClientId());
        CONFIG_MAP.put("clientSecret", clientSecrets.getDetails().getClientSecret());

        String requestUrl = new GoogleAuthorizationCodeRequestUrl(
                clientSecrets.getDetails().getClientId(), redirectUri, SCOPES).setState("/profile").build();
        LOG.info("Request Url is {}", requestUrl);

        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(new URI(requestUrl));
            } catch (IOException | URISyntaxException e) {
                LOG.error("Could not browse the URL above", e);
            }
        } else {
            Runtime runtime = Runtime.getRuntime();
            try {
                runtime.exec("xdg-open " + requestUrl);
            } catch (IOException e) {
                LOG.error("Could not browse the URL above", e);
            }
        }
    }

    @Bean
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ServletRegistrationBean codeProcessorServlet() {
        ServletRegistrationBean srb = new ServletRegistrationBean();
        srb.setServlet(new CodeProcessorServlet(redirectUri));
        srb.setUrlMappings(Arrays.asList("/code-processor/*"));
        return srb;
    }

    @Override
    public void run(final String... args) throws IOException, URISyntaxException {
        if (args.length == 1) {
            File clientJson = new File(args[0]);
            if (clientJson.isDirectory()) {
                clientJson = new File(clientJson, CLIENTSECRETS_LOCATION);
            }

            if (clientJson.exists() && clientJson.isFile()) {
                getConfigurationMap(clientJson);
            } else {
                LOG.error("Invalid client secret path: {}", clientJson);
            }
        }
    }
}
