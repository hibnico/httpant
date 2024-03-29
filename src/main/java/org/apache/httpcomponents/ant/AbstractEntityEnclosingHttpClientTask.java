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

import java.net.URI;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;

public abstract class AbstractEntityEnclosingHttpClientTask extends AbstractHttpClientTask {

    private HttpEntity entity;

    private EntityNode entityNode;

    protected abstract HttpEntityEnclosingRequestBase buildEntityEnclosingRequest(URI u);

    public void add(EntityNode entity) {
        if (this.entity != null) {
            throw new BuildException("Only one entity is allowed");
        }
        this.entityNode = entity;
        this.entity = entity.buildHttpEntity();
    }

    public void addConfiguredEntity(BasicEntityNode entity) {
        add(entity);
    }

    public void addConfiguredMultipartEntity(MultipartEntityNode entity) {
        add(entity);
    }

    @Override
    protected HttpEntityEnclosingRequestBase buildRequest(URI u) {
        HttpEntityEnclosingRequestBase request = buildEntityEnclosingRequest(u);
        if (entity != null) {
            request.setEntity(entity);
            entityNode.log(this, Project.MSG_VERBOSE);
        }
        return request;
    }

}
