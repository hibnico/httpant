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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

public abstract class AbstractHttpClientTask extends Task {

    private String uri;

    private List<HeaderNode> headers = new ArrayList<HeaderNode>();

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void add(HeaderNode header) {
        headers.add(header);
    }

    abstract protected HttpUriRequest buildRequest(URI u);

    @Override
    public void execute() throws BuildException {
        if (uri == null) {
            throw new BuildException("Missing attribute 'uri'");
        }

        HttpClient client = new DefaultHttpClient();
        URI u;
        try {
            u = new URI(uri);
        } catch (URISyntaxException e) {
            throw new BuildException("Incorrect URI '" + uri + "'", e);
        }
        HttpUriRequest request = buildRequest(u);
        for (HeaderNode header : headers) {
            request.addHeader(header.getName(), header.getValue());
        }
        HttpResponse response;
        try {
            response = client.execute(request);
        } catch (ClientProtocolException e) {
            throw new BuildException("HTTP error on request for '" + uri + "'", e);
        } catch (IOException e) {
            throw new BuildException("I/O error on request for '" + uri + "'", e);
        } finally {
            client.getConnectionManager().shutdown();
        }
    }

}
