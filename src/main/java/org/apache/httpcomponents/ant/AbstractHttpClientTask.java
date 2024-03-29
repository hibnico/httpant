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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
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

    private SSLNode ssl;

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

    public void addConfiguredHeader(HeaderNode header) {
        add(header);
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

    public void addConfiguredResponseHeader(ResponseHeaderNode responseHeader) {
        add(responseHeader);
    }

    public void add(CredentialNode credential) {
        if (this.credential != null) {
            throw new BuildException("Only one credential is allowed");
        }
        if (credential.getUsername() == null) {
            throw new BuildException("Missing attribute 'username' on credential");
        }
        if (credential.getPassword() == null) {
            throw new BuildException("Missing attribute 'password' on credential");
        }
        this.credential = credential;
    }

    public void addConfiguredCredential(CredentialNode credential) {
        add(credential);
    }

    public void add(SSLNode ssl) {
        if (this.ssl != null) {
            throw new BuildException("Only one ssl setup is allowed");
        }
        if (ssl.getTruststoreFile() == null) {
            throw new BuildException("Missing attribute 'truststoreFile' on ssl setup");
        }
        if (ssl.getTruststorePassword() == null) {
            throw new BuildException("Missing attribute 'truststorePassword' on ssl setup");
        }
        if (ssl.getKeystoreFile() != null && ssl.getKeystorePassword() == null) {
            throw new BuildException("Missing attribute 'keystorePassword' on ssl setup");
        }
        if (ssl.getKeystoreFile() == null && ssl.getKeystorePassword() != null) {
            throw new BuildException("Missing attribute 'keystoreFile' on ssl setup");
        }
        this.ssl = ssl;
    }

    public void addConfiguredSSL(SSLNode ssl) {
        add(ssl);
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

        DefaultHttpClient client;
        if (ssl != null) {
            SchemeRegistry schemeRegistry = new SchemeRegistry();
            String algorithm = SSLSocketFactory.TLS;
            log("Loading trustore " + ssl.getTruststoreFile(), Project.MSG_VERBOSE);
            KeyStore truststore = loadKeyStore("truststore", ssl.getTruststoreFile(), ssl.getTruststorePassword());
            KeyStore keystore = null;
            if (ssl.getKeystoreFile() != null) {
                log("Loading keystore " + ssl.getKeystoreFile(), Project.MSG_VERBOSE);
                keystore = loadKeyStore("keystore", ssl.getKeystoreFile(), ssl.getKeystorePassword());
            }
            SecureRandom secureRandom = null;
            TrustStrategy trustStrategy = null;
            X509HostnameVerifier x509HostnameVerifier = SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER;
            SSLSocketFactory lSchemeSocketFactory;
            try {
                lSchemeSocketFactory = new SSLSocketFactory(algorithm, keystore, ssl.getKeystorePassword(), truststore, secureRandom, trustStrategy,
                        x509HostnameVerifier);
            } catch (KeyManagementException e) {
                throw new BuildException("The SSL factory could not be setup", e);
            } catch (UnrecoverableKeyException e) {
                throw new BuildException("The SSL factory could not be setup", e);
            } catch (NoSuchAlgorithmException e) {
                throw new BuildException("The SSL factory could not be setup", e);
            } catch (KeyStoreException e) {
                throw new BuildException("The SSL factory could not be setup", e);
            }
            log("Registring SSL factory", Project.MSG_VERBOSE);
            schemeRegistry.register(new Scheme("https", 443, lSchemeSocketFactory));
            HttpParams httpParams = new BasicHttpParams();
            client = new DefaultHttpClient(new BasicClientConnectionManager(schemeRegistry), httpParams);
        } else {
            client = new DefaultHttpClient();
        }

        URI u;
        try {
            u = new URI(uri);
        } catch (URISyntaxException e) {
            throw new BuildException("Incorrect URI '" + uri + "'", e);
        }

        if (credential != null) {
            log("Basic Authetication: username=" + credential.getUsername() + " password=" + credential.getPassword().replaceAll(".", "*"),
                    Project.MSG_VERBOSE);
            client.getCredentialsProvider().setCredentials(new AuthScope(u.getHost(), u.getPort()),
                    new UsernamePasswordCredentials(credential.getUsername(), credential.getPassword()));
        }

        HttpUriRequest request = buildRequest(u);

        log("Sending " + request.getMethod() + " to " + request.getURI(), Project.MSG_INFO);

        if (!headers.isEmpty()) {
            log("With headers:", Project.MSG_VERBOSE);
            for (HeaderNode header : headers) {
                log("    " + header.getName() + ": " + header.getValue(), Project.MSG_VERBOSE);
                request.addHeader(header.getName(), header.getValue());
            }
        }

        HttpResponse response;
        try {
            try {
                response = client.execute(request);
            } catch (ClientProtocolException e) {
                throw new BuildException("HTTP error on request for '" + uri + "'", e);
            } catch (IOException e) {
                throw new BuildException("I/O error on request for '" + uri + "'", e);
            }
            log("Response: " + response.getStatusLine(), Project.MSG_INFO);

            if (statusProperty != null) {
                getProject().setNewProperty(statusProperty, Integer.toString(response.getStatusLine().getStatusCode()));
            }
            if (statusReasonProperty != null) {
                getProject().setNewProperty(statusReasonProperty, response.getStatusLine().getReasonPhrase());
            }

            log("Response headers: ", Project.MSG_VERBOSE);
            for (Header header : response.getAllHeaders()) {
                log("    " + header.getName() + ": " + header.getValue(), Project.MSG_VERBOSE);
                if (reponseHeaderPropertyPrefix != null) {
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

            HttpEntity entity = response.getEntity();
            if (entity == null) {
                if (responseFile != null) {
                    log("No response body, nothing written into " + responseFile, Project.MSG_VERBOSE);
                    try {
                        responseFile.createNewFile();
                    } catch (IOException e) {
                        throw new BuildException("The response file '" + responseFile + "' could not be created", e);
                    }
                } else if (responseProperty != null) {
                    log("No response body, property " + responseProperty + " not set", Project.MSG_VERBOSE);
                }
            } else if (responseFile != null) {
                log("Response body written into " + responseFile, Project.MSG_VERBOSE);
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
                log("Response body written into property " + responseProperty, Project.MSG_VERBOSE);
                String content;
                try {
                    content = EntityUtils.toString(entity);
                } catch (IOException e) {
                    throw new BuildException("The response could not be read", e);
                }
                String[] lines = content.split("\n");
                log("---- Response body ----", Project.MSG_VERBOSE);
                for (String line : lines) {
                    log(line, Project.MSG_VERBOSE);
                }
                log("---- EOF ----", Project.MSG_VERBOSE);
                getProject().setNewProperty(responseProperty, content);
            } else {
                if (entity != null) {
                    String content;
                    try {
                        content = EntityUtils.toString(entity);
                    } catch (IOException e) {
                        throw new BuildException("The response could not be read", e);
                    }
                    String[] lines = content.split("\n");
                    log("---- Response body ----", Project.MSG_VERBOSE);
                    for (String line : lines) {
                        log(line, Project.MSG_VERBOSE);
                    }
                    log("---- EOF ----", Project.MSG_VERBOSE);
                }
            }

            if (expectedStatus != null && expectedStatus != response.getStatusLine().getStatusCode()) {
                throw new BuildException("Expecting " + expectedStatus + " but received " + response.getStatusLine().getStatusCode());
            }

        } finally {
            client.getConnectionManager().shutdown();
        }
    }

    private KeyStore loadKeyStore(String name, File file, String password) {
        KeyStore keystore;
        try {
            keystore = KeyStore.getInstance(KeyStore.getDefaultType());
        } catch (KeyStoreException e) {
            throw new BuildException("Error while creating the " + name, e);
        }
        FileInputStream in;
        try {
            in = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            throw new BuildException("The " + name + " file '" + file + "' could not be found", e);
        }
        try {
            keystore.load(in, password.toCharArray());
        } catch (NoSuchAlgorithmException e) {
            throw new BuildException("The " + name + " could not be opened", e);
        } catch (CertificateException e) {
            throw new BuildException("The " + name + " could not be opened", e);
        } catch (IOException e) {
            throw new BuildException("The " + name + " could not be opened", e);
        } finally {
            FileUtils.close(in);
        }
        return keystore;
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
