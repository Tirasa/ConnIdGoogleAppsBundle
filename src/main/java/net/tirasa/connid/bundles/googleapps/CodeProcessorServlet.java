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

import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CodeProcessorServlet extends HttpServlet {

    private static final long serialVersionUID = -6813584667162798300L;
    
    private String redirectUri;

    public CodeProcessorServlet(final String redirectUri) {
        this.redirectUri = redirectUri;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        GoogleTokenResponse response =
                new GoogleAuthorizationCodeTokenRequest(CredentialsGeneratorApplication.HTTP_TRANSPORT,
                        CredentialsGeneratorApplication.JSON_FACTORY,
                        CredentialsGeneratorApplication.configMap.get("clientId").toString(),
                        CredentialsGeneratorApplication.configMap.get("clientSecret").toString(),
                        new AuthorizationCodeResponseUrl(req.getQueryString() == null
                                ? req.getRequestURL().toString()
                                : new StringBuilder(req.getRequestURL().toString()).append('?')
                                .append(req.getQueryString())
                                .toString()).getCode(), redirectUri)
                        .execute();

        CredentialsGeneratorApplication.configMap.put("refreshToken", response.getRefreshToken());

        String secretsContent =
                CredentialsGeneratorApplication.JSON_FACTORY.toPrettyString(CredentialsGeneratorApplication.configMap);
        System.out.println(secretsContent);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(secretsContent);
        resp.getWriter().flush();
    }
}