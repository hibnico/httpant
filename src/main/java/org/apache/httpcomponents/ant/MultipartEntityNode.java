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

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.tools.ant.BuildException;

public class MultipartEntityNode extends EntityNode {

    private String mode;

    private String boundary;

    private String charset;

    private List<PartNode> parts = new ArrayList<PartNode>();

    public void setMode(String mode) {
        this.mode = mode;
    }

    public void setBoundary(String boundary) {
        this.boundary = boundary;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public void add(PartNode part) {
        parts.add(part);
    }

    public void addConfiguredFieldPart(FieldPartNode part) {
        add(part);
    }

    public void addConfiguredFilePart(FilePartNode part) {
        add(part);
    }

    @Override
    public MultipartEntity buildHttpEntity() {
        HttpMultipartMode m = HttpMultipartMode.STRICT;
        if (mode != null) {
            try {
                m = HttpMultipartMode.valueOf(mode);
            } catch (IllegalArgumentException e) {
                throw new BuildException("Unsupported mode " + mode);
            }
        }
        Charset c = null;
        if (charset != null) {
            try {
                c = Charset.forName(charset);
            } catch (IllegalCharsetNameException e) {
                throw new BuildException("Incorrect charset name " + charset);
            } catch (UnsupportedCharsetException e) {
                throw new BuildException("Unsupported charset " + charset);
            }
        }
        MultipartEntity entity = new MultipartEntity(m, boundary, c);
        for (PartNode part : parts) {
            entity.addPart(part.getName(), part.buildContentBoby());
        }
        return entity;
    }

}
