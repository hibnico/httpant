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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.util.FileUtils;

public abstract class AbstractHttpClientTask extends Task {

    private String uri;

    private List<HeaderNode> headers = new ArrayList<HeaderNode>();

    private String statusProperty;

    private Integer expectedStatus;

    private String statusReasonProperty;

    private String reponseHeaderPropertyPrefix;

    private List<ResponseHeaderNode> responseHeaders = new ArrayList<ResponseHeaderNode>();

    private File responseFile;

    private String responseProperty;

    private CredentialNode credential;

    public void setUri(String uri) {
        this.uri = uri;
    }

    public void setStatusProperty(String statusProperty) {
        this.statusProperty = statusProperty;
    }

    public void setExpectedStatus(int expectedStatus) {
        this.expectedStatus = expectedStatus;
    }

    public void setStatusReasonProperty(String statusReasonProperty) {
        this.statusReasonProperty = statusReasonProperty;
    }

    public void add(HeaderNode header) {
        if (header.getName() == null) {
            throw new BuildException("Missing attribute 'name' on header");
        }
        if (header.getValue() == null) {
            throw new BuildException("Missing attribute 'value' on header");
        }
        headers.add(header);
    }

    public void add(ResponseHeaderNode responseHeader) {
        if (responseHeader.getName() == null) {
            throw new BuildException("Missing attribute 'name' on responseheader");
        }
        if (responseHeader.getProperty() == null) {
            throw new BuildException("Missing attribute 'property' on responseheader");
        }
        responseHeaders.add(responseHeader);
    }

    public void add(CredentialNode credential) {
        if (this.credential != null) {
            throw new BuildException("Only one credential is allowed");
        }
        if (credential.getUsername() == null) {
            throw new BuildException("Missing attribute 'username'");
        }
        if (credential.getPassword() == null) {
            throw new BuildException("Missing attribute 'password'");
        }
        this.credential = credential;
    }

    public void setResponseFile(File responseFile) {
        this.responseFile = responseFile;
    }

    public void setResponseProperty(String responseProperty) {
        this.responseProperty = responseProperty;
    }

    abstract protected HttpUriRequest buildRequest(URI u);

    @Override
    public void execute() throws BuildException {
        if (uri == null) {
            throw new BuildException("Missing attribute 'uri'");
        }
        if (responseFile != null && responseProperty != null) {
            throw new BuildException("Only one of 'reponseProperty' or 'reponseFile' attribute can be set");
        }

        DefaultHttpClient client = new DefaultHttpClient();

        URI u;
        try {
            u = new URI(uri);
        } catch (URISyntaxException e) {
            throw new BuildException("Incorrect URI '" + uri + "'", e);
        }

        if (credential != null) {
            client.getCredentialsProvider().setCredentials(new AuthScope(u.getHost(), u.getPort()),
                    new UsernamePasswordCredentials(credential.getUsername(), credential.getPassword()));
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

        if (expectedStatus != null && expectedStatus != response.getStatusLine().getStatusCode()) {
            throw new BuildException("Expecting " + expectedStatus + " but received " + response.getStatusLine().getStatusCode());
        }
        if (statusProperty != null) {
            getProject().setNewProperty(statusProperty, Integer.toString(response.getStatusLine().getStatusCode()));
        }
        if (statusReasonProperty != null) {
            getProject().setNewProperty(statusReasonProperty, response.getStatusLine().getReasonPhrase());
        }

        if (reponseHeaderPropertyPrefix != null) {
            for (Header header : response.getAllHeaders()) {
                getProject().setNewProperty(reponseHeaderPropertyPrefix + header.getName(), header.getValue());
            }
        }

        for (ResponseHeaderNode responseHeader : responseHeaders) {
            Header[] header = response.getHeaders(responseHeader.getName());
            if (header != null && header.length > 0) {
                if (header.length > 1) {
                    getProject().log(
                            header.length + " headers were found matching '" + responseHeader.getName() + "'. The property "
                                    + responseHeader.getProperty() + "' will be set only to the first match", Project.MSG_WARN);
                }
                getProject().setNewProperty(responseHeader.getProperty(), header[0].getValue());
            }
        }

        if (responseFile != null) {
            HttpEntity entity = response.getEntity();
            FileOutputStream out;
            try {
                out = new FileOutputStream(responseFile);
            } catch (FileNotFoundException e) {
                throw new BuildException("The response could not be written into " + responseFile, e);
            }
            try {
                InputStream in = getReponseInputStream(entity);
                try {
                    byte[] buffer = new byte[1024 * 4];
                    int n = 0;
                    while (-1 != (n = in.read(buffer))) {
                        out.write(buffer, 0, n);
                    }
                } catch (IOException e) {
                    throw new BuildException("The response could not be copied", e);
                } finally {
                    FileUtils.close(in);
                }
            } finally {
                FileUtils.close(out);
            }
        } else if (responseProperty != null) {
            HttpEntity entity = response.getEntity();
            String content;
            try {
                content = EntityUtils.toString(entity);
            } catch (IOException e) {
                throw new BuildException("The response could not be read", e);
            }
            getProject().setNewProperty(responseProperty, content);
        }
    }

    private InputStream getReponseInputStream(HttpEntity entity) {
        InputStream in;
        try {
            in = entity.getContent();
        } catch (IllegalStateException e) {
            throw new BuildException("The response could not be read", e);
        } catch (IOException e) {
            throw new BuildException("The response could not be read", e);
        }
        return in;
    }
}
