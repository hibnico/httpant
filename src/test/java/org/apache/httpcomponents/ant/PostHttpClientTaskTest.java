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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.resources.PropertyResource;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
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

    @Test
    public void testMultipartEntity() throws Exception {
        final List<Part> parts = new ArrayList<Part>();
        httpServerShell.setHandler(new AbstractHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException,
                    ServletException {
                baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, new MultipartConfigElement((String) null));
                parts.clear();
                parts.addAll(baseRequest.getParts());
            }
        });

        PostHttpClientTask task = new PostHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        MultipartEntityNode entity = new MultipartEntityNode();
        FieldPartNode part = new FieldPartNode();
        part.setName("field");
        part.setValue("value");
        entity.add(part);
        task.add(entity);
        task.execute();

        assertEquals(1, parts.size());
        assertEquals("field", parts.get(0).getName());
        assertEquals("value", IOUtils.toString(parts.get(0).getInputStream()));

        task = new PostHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        entity = new MultipartEntityNode();
        part = new FieldPartNode();
        part.setName("field");
        part.setValue("value");
        entity.add(part);
        part = new FieldPartNode();
        part.setName("field2");
        part.setValue("value2");
        part.setMimeType("text/xml");
        part.setCharset("UTF-8");
        entity.add(part);
        task.add(entity);
        task.execute();

        assertEquals(2, parts.size());
        assertEquals("field", parts.get(0).getName());
        assertEquals("value", IOUtils.toString(parts.get(0).getInputStream()));
        assertEquals("field2", parts.get(1).getName());
        assertEquals("value2", IOUtils.toString(parts.get(1).getInputStream()));
        assertEquals("text/xml; charset=UTF-8", parts.get(1).getContentType());

        String data = "Ant and HttpClient rulez";
        File dataFile = new File(tempDir, "data2.txt");
        FileUtils.writeStringToFile(dataFile, data);

        task = new PostHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        entity = new MultipartEntityNode();
        FilePartNode filepart = new FilePartNode();
        filepart.setName("datafile");
        filepart.setFile(dataFile);
        entity.add(filepart);
        task.add(entity);
        task.execute();

        assertEquals(1, parts.size());
        assertEquals("datafile", parts.get(0).getName());
        assertEquals(data, IOUtils.toString(parts.get(0).getInputStream()));

        String data3 = "Ant and HttpClient rulez";
        File data3File = new File(tempDir, "data3.txt");
        FileUtils.writeStringToFile(data3File, data3);

        task = new PostHttpClientTask();
        task.setProject(project);
        task.setUri(httpServerShell.getHttpServerUri());
        task.setExpectedStatus(200);
        entity = new MultipartEntityNode();
        filepart = new FilePartNode();
        filepart.setName("datafile");
        filepart.setFile(dataFile);
        entity.add(filepart);
        part = new FieldPartNode();
        part.setName("field");
        part.setValue("value");
        entity.add(part);
        filepart = new FilePartNode();
        filepart.setName("data3file");
        filepart.setFile(data3File);
        filepart.setMimeType("text/xml");
        filepart.setCharset("UTF-8");
        entity.add(filepart);
        task.add(entity);
        task.execute();

        assertEquals(3, parts.size());
        assertEquals("field", parts.get(0).getName());
        assertEquals("value", IOUtils.toString(parts.get(0).getInputStream()));
        assertEquals("data3file", parts.get(1).getName());
        assertEquals(data3, IOUtils.toString(parts.get(1).getInputStream()));
        assertEquals("text/xml; charset=UTF-8", parts.get(1).getContentType());
        assertEquals("datafile", parts.get(2).getName());
        assertEquals(data, IOUtils.toString(parts.get(2).getInputStream()));
    }
}
