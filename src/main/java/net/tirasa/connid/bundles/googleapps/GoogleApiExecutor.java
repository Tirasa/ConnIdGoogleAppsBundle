/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2024 ConnId. All Rights Reserved
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
package net.tirasa.connid.bundles.googleapps;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.json.AbstractGoogleJsonClientRequest;
import com.google.api.client.http.HttpStatusCodes;
import java.io.IOException;
import java.security.SecureRandom;
import org.identityconnectors.common.Assertions;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.RetryableException;

public final class GoogleApiExecutor {

    private static final Log LOG = Log.getLog(GoogleApiExecutor.class);

    private static final SecureRandom RANDOM = new SecureRandom();

    public static <G extends AbstractGoogleJsonClientRequest<T>, T, R> R execute(
            final G request, final RequestResultHandler<G, T, R> handler) {

        return execute(
                Assertions.nullChecked(request, "Google Json ClientRequest"),
                Assertions.nullChecked(handler, "handler"), -1);
    }

    public static <G extends AbstractGoogleJsonClientRequest<T>, T, R> R execute(
            final G request, final RequestResultHandler<G, T, R> handler, final int retry) {

        try {
            if (retry >= 0) {
                long sleep = (long) ((1000 * Math.pow(2, retry)) + nextLong(1000));
                try {
                    Thread.sleep(sleep);
                } catch (InterruptedException e) {
                    throw ConnectorException.wrap(e);
                }
            }
            return handler.handleResult(request, request.execute());
        } catch (GoogleJsonResponseException e) {
            GoogleJsonError details = e.getDetails();
            if (null != details && null != details.getErrors()) {
                GoogleJsonError.ErrorInfo errorInfo = details.getErrors().get(0);
                // error: 403
                LOG.error("Unable to execute request {0} - {1} - {2}",
                        e.getStatusCode(), e.getStatusMessage(), errorInfo.getReason());
                switch (e.getStatusCode()) {
                    case HttpStatusCodes.STATUS_CODE_FORBIDDEN:
                        if ("userRateLimitExceeded".equalsIgnoreCase(errorInfo.getReason())
                                || "rateLimitExceeded".equalsIgnoreCase(errorInfo.getReason())) {
                            return handler.handleError(e);
                        }
                        break;
                    case HttpStatusCodes.STATUS_CODE_NOT_FOUND:
                        if ("notFound".equalsIgnoreCase(errorInfo.getReason())) {
                            return handler.handleNotFound(e);
                        }
                        break;
                    case 409:
                        if ("duplicate".equalsIgnoreCase(errorInfo.getReason())) {
                            // Already Exists
                            handler.handleDuplicate(e);
                        }
                        break;
                    case 400:
                        if ("invalid".equalsIgnoreCase(errorInfo.getReason())) {
                            // Already Exists "Invalid Ou Id"
                        }
                        break;
                    case HttpStatusCodes.STATUS_CODE_SERVICE_UNAVAILABLE:
                        if ("backendError".equalsIgnoreCase(errorInfo.getReason())) {
                            throw RetryableException.wrap(e.getMessage(), e);
                        }
                        break;
                    default:
                        break;
                }
            }

            if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_FORBIDDEN) {
                LOG.error("Forbidden request");
                handler.handleError(e);
            } else if (e.getStatusCode() == HttpStatusCodes.STATUS_CODE_NOT_FOUND) {
                LOG.error("Endpoint not found for request");
                return handler.handleNotFound(e);
            }
            throw ConnectorException.wrap(e);
        } catch (IOException e) {
            // https://developers.google.com/admin-sdk/directory/v1/limits
            // rateLimitExceeded or userRateLimitExceeded
            if (retry < 5) {
                return execute(request, handler, retry + 1);
            } else {
                return handler.handleError(e);
            }
        }
    }

    private static long nextLong(final long n) {
        long bits, val;
        do {
            bits = (RANDOM.nextLong() << 1) >>> 1;
            val = bits % n;
        } while (bits - val + (n - 1) < 0L);
        return val;
    }

    private GoogleApiExecutor() {
        // private constructor for static utility class
    }
}
