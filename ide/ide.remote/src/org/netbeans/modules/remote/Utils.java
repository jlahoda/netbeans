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
package org.netbeans.modules.remote;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author lahvac
 */
public class Utils {

    public static final Gson gson = new Gson();

    public static void write(OutputStream out, String data) throws IOException {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] augmentedBytes = new byte[bytes.length + 4];
        System.arraycopy(bytes, 0, augmentedBytes, 4, bytes.length);
        writeInt(augmentedBytes, 0, bytes.length);
        out.write(augmentedBytes);
        out.flush();
    }

    public static void writeInt(byte[] data, int offset, int value) {
        data[offset + 0] = (byte) ((value >> 24) & 0xFF);
        data[offset + 1] = (byte) ((value >> 16) & 0xFF);
        data[offset + 2] = (byte) ((value >>  8) & 0xFF);
        data[offset + 3] = (byte) ((value >>  0) & 0xFF);
    }

    public static String read(InputStream in) throws IOException {
        int len = readInt(in);
        byte[] data = in.readNBytes(len);
        return new String(data, StandardCharsets.UTF_8);
    }

    public static int readInt(InputStream in) throws IOException {
        return ((in.read()  & 0xFF) << 24) +
               ((in.read()  & 0xFF) << 16) +
               ((in.read()  & 0xFF) <<  8) +
               ((in.read()  & 0xFF) <<  0);
    }

}
