/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tirasa.connid.bundles.googleapps;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
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
import java.net.URI;
import java.net.URISyntaxException;
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

    private void getConfigurationMap(File clientJson) throws IOException, URISyntaxException {
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new FileReader(clientJson));
        configMap.put("clientId", clientSecrets.getDetails().getClientId());
        configMap.put("clientSecret", clientSecrets.getDetails().getClientSecret());

        String requestUrl = new GoogleAuthorizationCodeRequestUrl(
                clientSecrets.getDetails().getClientId(),
                redirectUri, SCOPES)
                .setState("/profile").build();
        System.out.println("Request Url is " + requestUrl);

        java.awt.Desktop.getDesktop().getDesktop().browse(new URI(requestUrl));
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
    public void run(String... args) throws IOException, URISyntaxException {
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
