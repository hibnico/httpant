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

import static org.junit.Assert.assertEquals;

import org.apache.tools.ant.Project;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class AuthHttpClientTaskTest {

    private static HTTPServerShell httpServerShell = new HTTPServerShell();

    private Project project;

    @BeforeClass
    public static void beforeClass() throws Exception {
        httpServerShell.startServer();
        httpServerShell.setHandler(HTTPServerShell.buildBasicAuth(HTTPServerShell.PING_HANDLER, "Test Realm", "johndoe", "p4S5w0rd",
                new String[] { "admin" }));
    }

    @AfterClass
    public static void afterClass() throws Exception {
        httpServerShell.stopServer();
    }

    @Before
    public void before() {
        project = new Project();
    }

    @Test
    public void testBasicAuth() {
        GetHttpClientTask task = new GetHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        CredentialNode cred = new CredentialNode();
        cred.setUsername("johndoe");
        cred.setPassword("p4S5w0rd");
        task.add(cred);
        task.setResponseProperty("response");
        task.execute();

        assertEquals(HTTPServerShell.PING_RESPONSE, project.getProperty("response"));
    }

    @Test
    public void testBasicAuthUnauthorized() {
        GetHttpClientTask task = new GetHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(401);
        task.execute();

        task = new GetHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(401);
        CredentialNode cred = new CredentialNode();
        cred.setUsername("johndoe");
        cred.setPassword("badpassword");
        task.add(cred);
        task.execute();
    }

}
