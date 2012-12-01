/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.httpcomponents.ant;

import java.io.IOException;
import java.security.KeyStore;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.AbstractHandlerContainer;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.ssl.SslContextFactory;

public class HTTPServerManager {

    static final String PING_CONTEXT = "/ping";
    static final String PING_RESPONSE = "pong?";

    static final String ECHO_CONTEXT = "/echo";
    static final String ECHO_TEXT = "text";

    static final String INTERNAL_SERVER_ERROR_CONTEXT = "/500";
    static final String INTERNAL_SERVER_ERROR_RESPONSE = "Internal Server Error";

    static final String SECURE_CONTEXT = "/secure";

    static final String REALM = "Test Realm";
    static final String USERNAME = "user";
    static final String PASSWORD = "password";

    static final String ZIP_CONTEXT = "/zip";
    static final String PNG_CONTEXT = "/png";

    static final String ZIP = "hw.zip";
    static final String PNG = "hw.png";

    static final String KEYSTORE = "keystore.jks";
    static final String KEYSTORE_PASSWORD = "password";

    int httpServerPort = 10080;

    int httpsServerPort = 10443;

    private Server server;

    String getHttpServerUri() {
        return "http://localhost:" + httpServerPort;
    }

    String getHttpsServerUri() {
        return "https://localhost:" + httpsServerPort;
    }

    void stopServer() throws Exception {
        server.stop();
    }

    void startServer() throws Exception {
        server = new Server();

        Connector connector = new SelectChannelConnector();
        connector.setHost("localhost");
        connector.setPort(httpServerPort);
        server.addConnector(connector);

        SslContextFactory ssl = new SslContextFactory();
        final char[] passphrase = KEYSTORE_PASSWORD.toCharArray();
        final KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(this.getClass().getResourceAsStream(KEYSTORE), passphrase);
        ssl.setKeyStore(ks);
        ssl.setKeyStorePassword(KEYSTORE_PASSWORD);
        SslSelectChannelConnector sslconnector = new SslSelectChannelConnector(ssl);
        sslconnector.setHost("localhost");
        sslconnector.setPort(httpsServerPort);
        server.addConnector(sslconnector);

        attachHttpHandlers(server);

        server.start();
    }

    private ConstraintSecurityHandler buildBasicAuth(Handler handler, String realm, String user, String password) {
        ConstraintSecurityHandler secureHandler = new ConstraintSecurityHandler();
        secureHandler.setHandler(handler);
        secureHandler.setRealmName(realm);
        secureHandler.setAuthenticator(new BasicAuthenticator());
        ConstraintMapping cm = new ConstraintMapping();
        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[] { "user" });
        constraint.setAuthenticate(true);
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");
        secureHandler.addConstraintMapping(cm);
        HashLoginService loginService = new HashLoginService();
        loginService.putUser(user, Credential.getCredential(password), new String[] { "user" });
        loginService.setName(realm);
        secureHandler.setLoginService(loginService);
        return secureHandler;
    }

    void attachHttpHandlers(Server server) {

        final AbstractHandler pingHandler = new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException,
                    ServletException {
                response.addHeader("Content-Type", "text/plain");
                response.setStatus(200);
                response.setContentLength(PING_RESPONSE.getBytes().length);
                response.getOutputStream().write(PING_RESPONSE.getBytes());
                response.getOutputStream().close();
            }
        };

        final ConstraintSecurityHandler securePingHandler = buildBasicAuth(pingHandler, REALM, USERNAME, PASSWORD);

        final AbstractHandler echoHandler = new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException,
                    ServletException {
                String responseEntity = "";
                if (baseRequest.getMethod().equals("POST") || baseRequest.getMethod().equals("PUT")) {
                    responseEntity = IOUtils.toString(baseRequest.getInputStream());
                } else if (baseRequest.getMethod().equals("GET")) {
                    responseEntity = baseRequest.getParameter(ECHO_TEXT);
                }
                response.addHeader("Content-Type", "text/plain");
                response.setStatus(200);
                response.setContentLength(responseEntity.getBytes().length);
                response.getOutputStream().write(responseEntity.getBytes());
                response.getOutputStream().close();
            }
        };

        final ConstraintSecurityHandler secureEchoHandler = buildBasicAuth(echoHandler, REALM, USERNAME, PASSWORD);

        final AbstractHandler errorHandler = new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException,
                    ServletException {
                response.addHeader("Content-Type", "text/plain");
                response.setStatus(500);
                response.setContentLength(INTERNAL_SERVER_ERROR_RESPONSE.getBytes().length);
                response.getOutputStream().write(INTERNAL_SERVER_ERROR_RESPONSE.getBytes());
                response.getOutputStream().close();
            }
        };

        final ConstraintSecurityHandler secureErrorHandler = buildBasicAuth(errorHandler, REALM, USERNAME, PASSWORD);

        final AbstractHandler zipHandler = new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException,
                    ServletException {
                response.addHeader("Content-Type", "application/zip");
                response.setStatus(200);
                byte[] bytes = IOUtils.toByteArray(this.getClass().getResourceAsStream(ZIP));
                response.setContentLength(bytes.length);
                response.getOutputStream().write(bytes);
                response.getOutputStream().close();
            }
        };

        final ConstraintSecurityHandler secureZipHandler = buildBasicAuth(zipHandler, REALM, USERNAME, PASSWORD);

        final AbstractHandler pngHandler = new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException,
                    ServletException {
                response.addHeader("Content-Type", "image/png");
                response.setStatus(200);
                byte[] bytes = IOUtils.toByteArray(this.getClass().getResourceAsStream(PNG));
                response.setContentLength(bytes.length);
                response.getOutputStream().write(bytes);
                response.getOutputStream().close();
            }
        };

        final ConstraintSecurityHandler securePngHandler = buildBasicAuth(pngHandler, REALM, USERNAME, PASSWORD);

        server.setHandler(new AbstractHandlerContainer() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException,
                    ServletException {
                if (target.equals(PING_CONTEXT)) {
                    pingHandler.handle(target, baseRequest, request, response);
                } else if (target.equals(SECURE_CONTEXT + PING_CONTEXT)) {
                    securePingHandler.handle(target, baseRequest, request, response);
                } else if (target.equals(ECHO_CONTEXT)) {
                    echoHandler.handle(target, baseRequest, request, response);
                } else if (target.equals(SECURE_CONTEXT + ECHO_CONTEXT)) {
                    secureEchoHandler.handle(target, baseRequest, request, response);
                } else if (target.equals(INTERNAL_SERVER_ERROR_CONTEXT)) {
                    errorHandler.handle(target, baseRequest, request, response);
                } else if (target.equals(SECURE_CONTEXT + INTERNAL_SERVER_ERROR_CONTEXT)) {
                    secureErrorHandler.handle(target, baseRequest, request, response);
                } else if (target.equals(ZIP_CONTEXT)) {
                    zipHandler.handle(target, baseRequest, request, response);
                } else if (target.equals(SECURE_CONTEXT + ZIP_CONTEXT)) {
                    secureZipHandler.handle(target, baseRequest, request, response);
                } else if (target.equals(PNG_CONTEXT)) {
                    pngHandler.handle(target, baseRequest, request, response);
                } else if (target.equals(SECURE_CONTEXT + PNG_CONTEXT)) {
                    securePngHandler.handle(target, baseRequest, request, response);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
                baseRequest.setHandled(true);
            }

            @Override
            public Handler[] getHandlers() {
                return new Handler[] { pingHandler, securePingHandler, echoHandler, secureEchoHandler, errorHandler, secureErrorHandler, zipHandler,
                        secureZipHandler };
            }
        });
    }

}
