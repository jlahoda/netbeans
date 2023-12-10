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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author lahvac
 */
public class Utils {

    public static void write(OutputStream out, String data) throws IOException {
        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        byte[] augmentedBytes = new byte[bytes.length + 4];
        System.arraycopy(bytes, 0, augmentedBytes, 4, bytes.length);
        augmentedBytes[0] = (byte) ((bytes.length >> 24) & 0xFF);
        augmentedBytes[1] = (byte) ((bytes.length >> 16) & 0xFF);
        augmentedBytes[2] = (byte) ((bytes.length >>  8) & 0xFF);
        augmentedBytes[3] = (byte) ((bytes.length >>  0) & 0xFF);
        out.write(augmentedBytes);
        out.flush();
    }

    public static String read(InputStream in) throws IOException {
        int len = (in.read() << 24) +
                  (in.read() << 16) +
                  (in.read() <<  8) +
                  (in.read() <<  0);
        byte[] data = in.readNBytes(len);
        return new String(data, StandardCharsets.UTF_8);
    }
}
