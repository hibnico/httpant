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

import static org.junit.Assert.fail;

import java.io.File;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class SSLHttpClientTaskTest {

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
    public void testSSL() throws Exception {
        httpServerShell.setHandler(HTTPServerShell.PING_HANDLER);

        GetHttpClientTask task = new GetHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpsServerUri());
        task.setExpectedStatus(200);
        try {
            task.execute();
            fail("Expecting BuildException : unconfigured SSL shouldn't work");
        } catch (BuildException e) {
            // ok
        }

        task = new GetHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpsServerUri());
        task.setExpectedStatus(200);
        SSLNode ssl = new SSLNode();
        ssl.setTruststoreFile(new File(this.getClass().getResource(HTTPServerShell.KEYSTORE).toURI()));
        ssl.setTruststorePassword(HTTPServerShell.KEYSTORE_PASSWORD);
        task.add(ssl);
        task.execute();
    }

}
