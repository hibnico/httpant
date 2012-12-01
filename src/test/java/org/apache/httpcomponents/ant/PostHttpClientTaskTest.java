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

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.resources.PropertyResource;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class PostHttpClientTaskTest {

    private static HTTPServerShell httpServerShell = new HTTPServerShell();

    private static File tempDir;

    private Project project;

    @BeforeClass
    public static void beforeClass() throws Exception {
        httpServerShell.startServer();
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
    public void testPing() throws Exception {
        httpServerShell.setHandler(HTTPServerShell.PING_HANDLER);

        AbstractHttpClientTask task = new PostHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        task.execute();

        task = new PutHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        task.execute();

        task = new PatchHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        task.execute();
    }

    @Test
    public void testValueEntity() throws Exception {
        httpServerShell.setHandler(HTTPServerShell.ECHO_HANDLER);

        String data = "Ant and HttpClient rulez";

        PostHttpClientTask task = new PostHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        BasicEntityNode entity = new BasicEntityNode();
        entity.setValue(data);
        task.add(entity);
        task.setResponseProperty("response");
        task.execute();

        assertEquals(data, project.getProperty("response"));
    }

    @Test
    public void testFileEntity() throws Exception {
        httpServerShell.setHandler(HTTPServerShell.ECHO_HANDLER);

        String data = "Ant and HttpClient rulez";
        File dataFile = new File(tempDir, "data.txt");
        FileUtils.writeStringToFile(dataFile, data);

        PostHttpClientTask task = new PostHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        BasicEntityNode entity = new BasicEntityNode();
        entity.setFile(dataFile);
        task.add(entity);
        task.setResponseProperty("response");
        task.execute();

        assertEquals(data, project.getProperty("response"));
    }

    @Test
    public void testResourceEntity() throws Exception {
        httpServerShell.setHandler(HTTPServerShell.ECHO_HANDLER);

        String data = "Ant and HttpClient rulez";
        project.setNewProperty("data", data);
        PropertyResource resourceData = new PropertyResource(project, "data");

        PostHttpClientTask task = new PostHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        BasicEntityNode entity = new BasicEntityNode();
        entity.add(resourceData);
        task.add(entity);
        task.setResponseProperty("response");
        task.execute();

        assertEquals(data, project.getProperty("response"));
    }
}
