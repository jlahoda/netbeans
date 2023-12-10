/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.remote.ide.fs;

/**
 * XXX: handle exceptions!!
 */
public class FSProtokol {

    public final static class Request {
        public int id;
        public RequestKind kind;
        public String data;

        public Request() {
        }

        public Request(int id, RequestKind kind, String data) {
            this.id = id;
            this.kind = kind;
            this.data = data;
        }
    }

    public final static class WriteData {
        public String path;
        public byte[] data;

        public WriteData() {
        }

        public WriteData(String path, byte[] data) {
            this.path = path;
            this.data = data;
        }
    }

//    public final static class AttributeData {
//        public String path;
//        public String dataName;
//        public byte[] data;
//
//        public AttributeData() {
//        }
//
//        public AttributeData(String path, String dataName, byte[] data) {
//            this.path = path;
//            this.dataName = dataName;
//            this.data = data;
//        }
//
//    }

    public final static class Response {
        public int id;
        public String data;

        public Response() {
        }

        public Response(int id, String data) {
            this.id = id;
            this.data = data;
        }

    }

    public enum RequestKind {
        //List:
        CHILDREN,

        //Info:
        LAST_MODIFIED,
        FOLDER,
        READ_ONLY,
        MIME_TYPE,
        SIZE,
        READ_INPUT, //XXX: should stream
        WRITE_OUTPUT, //XXX: should stream
        LOCK,
        UNLOCK,

        //Attr:
//        READ_ATTRIBUTE,
//        WRITE_ATTRIBUTE,
//        LIST_ATTRIBUTES,
//        RENAME_ATTRIBUTES,
//        DELETE_ATTRIBUTES,
//    public Object readAttribute(String name, String attrName) {
//        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
//    }
//
//    @Override
//    public void writeAttribute(String name, String attrName, Object value) throws IOException {
//        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
//    }
//
//    @Override
//    public Enumeration<String> attributes(String name) {
//        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
//    }
//
//    @Override
//    public void renameAttributes(String oldName, String newName) {
//        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
//    }
//
//    @Override
//    public void deleteAttributes(String name) {
    }
}
