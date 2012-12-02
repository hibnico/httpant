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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.HttpEntity;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.Resource;

public class BasicEntityNode extends EntityNode {

    private File file;

    private String value;

    private Resource resource;

    private String contentType;

    private String contentEncoding;

    public void setFile(File file) {
        this.file = file;
    }

    public void setValue(String value) {
        addText(value);
    }

    public void addText(String value) {
        if (this.value != null) {
            this.value += value;
        } else {
            this.value = value;
        }
    }

    public void add(Resource resource) {
        this.resource = resource;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setContentEncoding(String contentEncoding) {
        this.contentEncoding = contentEncoding;
    }

    @Override
    public HttpEntity buildHttpEntity() {
        AbstractHttpEntity entity;
        if (file != null) {
            if (value != null || resource != null) {
                throw new BuildException("Only one of 'file' or 'value' attribute or nested resource is supported");
            }
            entity = new FileEntity(file);
        } else if (value != null) {
            if (file != null || resource != null) {
                throw new BuildException("Only one of 'file' or 'value' attribute or nested resource is supported");
            }
            try {
                entity = new StringEntity(value);
            } catch (UnsupportedEncodingException e) {
                throw new BuildException("Unsupported default encoding", e);
            }
        } else if (resource != null) {
            if (file != null || value != null) {
                throw new BuildException("Only one of 'file' or 'value' attribute or nested resource is supported");
            }
            BasicHttpEntity basicEntity = new BasicHttpEntity();
            try {
                basicEntity.setContent(resource.getInputStream());
            } catch (IOException e) {
                throw new BuildException("I/O error while getting a stream out of the resource " + resource, e);
            }
            basicEntity.setContentLength(resource.getSize());
            entity = basicEntity;
        } else {
            throw new BuildException("At least one of 'file' or 'value' attribute or nested resource is required");
        }
        if (contentType != null) {
            entity.setContentType(contentType);
        }
        if (contentEncoding != null) {
            entity.setContentEncoding(contentEncoding);
        }
        return entity;
    }

    @Override
    public void log(Task task, int msgLevel) {
        if (file != null) {
            task.log("Request body from file: " + file, msgLevel);
        } else if (value != null) {
            task.log("---- Request body ----", msgLevel);
            String[] lines = value.split("\n");
            for (String line : lines) {
                task.log(line, msgLevel);
            }
            task.log("---- EOF ----", msgLevel);
        } else if (resource != null) {
            task.log("Request body from resource: " + resource, msgLevel);
        }
    }
}
