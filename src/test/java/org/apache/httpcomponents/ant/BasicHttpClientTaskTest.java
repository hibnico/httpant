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
import static org.junit.Assert.fail;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class BasicHttpClientTaskTest {

    private static HTTPServerShell httpServerShell = new HTTPServerShell();

    private static File tempDir;

    private Project project;

    @BeforeClass
    public static void beforeClass() throws Exception {
        httpServerShell.startServer();
        httpServerShell.setHandler(HTTPServerShell.PING_HANDLER);
        tempDir = File.createTempFile("httpant-test", "");
        tempDir.delete();
        tempDir.mkdir();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        httpServerShell.stopServer();
        FileUtils.deleteDirectory(tempDir);
    }

    @Before
    public void before() {
        project = new Project();
    }

    @Test
    public void testGet() throws Exception {
        GetHttpClientTask task = new GetHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setStatusProperty("status");
        task.execute();

        assertEquals("200", project.getProperty("status"));
    }

    @Test
    public void testGetExpected() throws Exception {
        GetHttpClientTask task = new GetHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        task.execute();

        task = new GetHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(400);
        try {
            task.execute();
            fail("BuildException expected");
        } catch (BuildException b) {
            // ok
        }
    }

    @Test
    public void testGetResponse() throws Exception {
        GetHttpClientTask task = new GetHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        task.setResponseProperty("response");
        task.execute();

        assertEquals(HTTPServerShell.PING_RESPONSE, project.getProperty("response"));
    }

    @Test
    public void testGetResponseFile() throws Exception {
        GetHttpClientTask task = new GetHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        File responseFile = new File(tempDir, "response.txt");
        task.setResponseFile(responseFile);
        task.execute();

        assertEquals(HTTPServerShell.PING_RESPONSE, FileUtils.readFileToString(responseFile));
    }

    @Test
    public void testHead() throws Exception {
        HeadHttpClientTask task = new HeadHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        task.execute();
    }

    @Test
    public void testDelete() throws Exception {
        DeleteHttpClientTask task = new DeleteHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        task.execute();
    }

    @Test
    public void testOptions() throws Exception {
        OptionsHttpClientTask task = new OptionsHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        task.execute();
    }

    @Test
    public void testTrace() throws Exception {
        TraceHttpClientTask task = new TraceHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        task.execute();
    }

}
