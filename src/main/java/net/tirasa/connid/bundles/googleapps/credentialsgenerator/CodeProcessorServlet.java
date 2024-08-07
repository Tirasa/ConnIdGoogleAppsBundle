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

import com.google.api.client.auth.oauth2.AuthorizationCodeResponseUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class CodeProcessorServlet extends HttpServlet {

    private static final long serialVersionUID = -6813584667162798300L;

    private final String redirectUri;

    public CodeProcessorServlet(final String redirectUri) {
        this.redirectUri = redirectUri;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
        GoogleTokenResponse response = new GoogleAuthorizationCodeTokenRequest(
                CredentialsGeneratorApplication.HTTP_TRANSPORT,
                CredentialsGeneratorApplication.JSON_FACTORY,
                CredentialsGeneratorApplication.CONFIG_MAP.get("clientId").toString(),
                CredentialsGeneratorApplication.CONFIG_MAP.get("clientSecret").toString(),
                new AuthorizationCodeResponseUrl(req.getQueryString() == null
                        ? req.getRequestURL().toString()
                        : new StringBuilder(req.getRequestURL().toString()).append('?')
                                .append(req.getQueryString())
                                .toString()).getCode(), redirectUri)
                .execute();

        CredentialsGeneratorApplication.CONFIG_MAP.put("refreshToken", response.getRefreshToken());

        String secretsContent =
                CredentialsGeneratorApplication.JSON_FACTORY.toPrettyString(CredentialsGeneratorApplication.CONFIG_MAP);
        System.out.println(secretsContent);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().write(secretsContent);
        resp.getWriter().flush();
    }
}
