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
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipInputStream;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
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
            InputStream in = getReponseInputStream(entity);
            Charset c = getResponseCharset(entity);
            String content;
            try {
                content = FileUtils.readFully(new InputStreamReader(in, c));
            } catch (IOException e) {
                throw new BuildException("The response could not be read", e);
            } finally {
                FileUtils.close(in);
            }
            getProject().setNewProperty(responseProperty, content);
        }
    }

    private Charset getResponseCharset(HttpEntity entity) {
        Charset c = Charset.defaultCharset();
        if (entity.getContentType() != null) {
            String charset = null;
            HeaderElement[] elements = entity.getContentType().getElements();
            if (elements != null) {
                for (HeaderElement element : elements) {
                    if (element.getName().equalsIgnoreCase("charset")) {
                        charset = element.getValue();
                        break;
                    }
                }
            }
            if (charset != null) {
                try {
                    c = Charset.forName(charset);
                } catch (IllegalCharsetNameException e) {
                    throw new BuildException("Illegal charset in the response " + charset, e);
                } catch (UnsupportedCharsetException e) {
                    throw new BuildException("Unsupported charset in the response " + charset, e);
                }
            }
        }
        return c;
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
        if (entity.getContentEncoding() != null) {
            if ("gzip".equalsIgnoreCase(entity.getContentEncoding().getValue())) {
                try {
                    in = new GZIPInputStream(in);
                } catch (IOException e) {
                    throw new BuildException("The response has a gzip content encoding, but it failed to be read as such", e);
                }
            } else if ("deflate".equalsIgnoreCase(entity.getContentEncoding().getValue())) {
                in = new InflaterInputStream(in, new Inflater(true));
            } else if ("zlib".equalsIgnoreCase(entity.getContentEncoding().getValue())) {
                in = new ZipInputStream(in);
            } else {
                throw new BuildException("The response has an unsupported content encoding: " + entity.getContentEncoding());
            }
        }
        return in;
    }
}
