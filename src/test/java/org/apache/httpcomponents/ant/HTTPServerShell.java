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

public class HTTPServerShell {

    public static final String KEYSTORE = "keystore.jks";

    public static final String KEYSTORE_PASSWORD = "password";

    public static final int httpServerPort = Integer.parseInt(System.getProperty("httpserver.port", "10080"));

    public static final int httpsServerPort = Integer.parseInt(System.getProperty("httpserver.ssl.port", "10443"));

    private Server server;

    private Handler handler;

    public void setHandler(Handler handler) throws Exception {
        if (this.handler != null) {
            this.handler.stop();
        }
        this.handler = handler;
        this.handler.start();
    }

    public String getHttpServerUri() {
        return "http://localhost:" + httpServerPort;
    }

    public String getHttpsServerUri() {
        return "https://localhost:" + httpsServerPort;
    }

    public void stopServer() throws Exception {
        server.stop();
    }

    public void startServer() throws Exception {
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

        server.setHandler(new AbstractHandlerContainer() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException,
                    ServletException {
                handler.handle(target, baseRequest, request, response);
                baseRequest.setHandled(true);
            }

            @Override
            public Handler[] getHandlers() {
                return new Handler[] {};
            }
        });
        server.start();
    }

    public static Handler buildBasicAuth(Handler handler, String realm, String user, String password, String[] roles) {
        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(roles);
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        HashLoginService loginService = new HashLoginService();
        loginService.putUser(user, Credential.getCredential(password), roles);
        loginService.setName(realm);

        ConstraintSecurityHandler secureHandler = new ConstraintSecurityHandler();
        secureHandler.setHandler(handler);
        secureHandler.setRealmName(realm);
        secureHandler.setAuthenticator(new BasicAuthenticator());
        secureHandler.addConstraintMapping(cm);
        secureHandler.setLoginService(loginService);
        return secureHandler;
    }

    public static final String PING_RESPONSE = "pong";

    public static final Handler PING_HANDLER = new AbstractHandler() {
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

    public static final Handler ECHO_HANDLER = new AbstractHandler() {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException,
                ServletException {
            String responseEntity = baseRequest.getParameter("echo");
            if (responseEntity == null) {
                responseEntity = IOUtils.toString(baseRequest.getInputStream());
            }
            response.addHeader("Content-Type", "text/plain");
            response.setStatus(200);
            response.setContentLength(responseEntity.getBytes().length);
            response.getOutputStream().write(responseEntity.getBytes());
            response.getOutputStream().close();
        }
    };
}
