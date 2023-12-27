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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.remote.Utils.EndOfInput;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 */
public class StreamMultiplexor {

    private static final Logger LOG = Logger.getLogger(StreamMultiplexor.class.getName());
    private static final int MAX_PACKET_SIZE = 1024;
    //TODO: could be a virtual thread:
    private final RequestProcessor WORKER = new RequestProcessor(StreamMultiplexor.class.getName(), 1, false, false);
    private final InputStream in;
    private final OutputStream out;
    private final Map<Integer, ChannelInputStream> channel2Input = new HashMap<>();

    public StreamMultiplexor(InputStream in, OutputStream out) {
        this.in = in;
        this.out = out;
        WORKER.post(() -> {
            try {
                while (true) {
                    int channel = Utils.readInt(in);
                    int packetSize = Utils.readInt(in);
                    byte[] packet = new byte[packetSize];
                    int readSize = in.readNBytes(packet, 0, packetSize);

                    if (readSize < packetSize) {
                        throw new EndOfInput();
                    }

                    ChannelInputStream input;

                    synchronized (channel2Input) {
                        input = channel2Input.get(channel);
                    }

                    if (input != null) {
                        input.addBuffer(packet);
                    } else {
                        LOG.log(Level.FINE, "Got data for unknown channel: {0}", channel);
                    }
                }
            } catch (EndOfInput ex) {
                //OK
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            synchronized (channel2Input) {
                for (ChannelInputStream cis : channel2Input.values()) {
                    cis.close();
                }
            }
        });
    }

    public Streams getStreamsForChannel(int channelNumber) {
        ChannelInputStream channelIn;

        synchronized (channel2Input) {
            if (channel2Input.containsKey(channelNumber)) {
                throw new IllegalArgumentException("Already provided stream for channel number " + channelNumber);
            }

            channelIn = new ChannelInputStream(channelNumber);

            channel2Input.put(channelNumber, channelIn);
        }

        return new Streams(channelIn, new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                write(new byte[] {(byte) b});
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                send(channelNumber, b, off, len);
            }

            @Override
            public void close() throws IOException {
                channelIn.close();
            }
        });
    }


    private void send(int channel, byte[] data, int offset, int len) throws IOException {
        while (offset < len) {
            int packetSize = Math.min(len - offset, MAX_PACKET_SIZE);

            sendPacket(channel, data, offset, packetSize);

            offset += packetSize;
        }
    }

    private synchronized void sendPacket(int channel, byte[] data, int offset, int len) throws IOException{
        byte[] augmentedData = new byte[len + 8];
        Utils.writeInt(augmentedData, 0, channel);
        Utils.writeInt(augmentedData, 4, len);
        System.arraycopy(data, offset, augmentedData, 8, len);
        out.write(augmentedData);
        out.flush();
    }

    private class ChannelInputStream extends InputStream {

        private final int channelNumber;
        private final List<byte[]> buffers = new ArrayList<>();
        private int offset;
        private boolean closed;

        public ChannelInputStream(int channelNumber) {
            this.channelNumber = channelNumber;
        }

        @Override
        public int read() throws IOException {
            byte[] data = new byte[1];
            int readLen = this.readNBytes(data, 0, 1);

            if (readLen == 0) {
                return -1;
            }

            return data[0];
        }

        @Override
        public synchronized int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }

            while (buffers.isEmpty() && !closed) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    LOG.log(Level.FINE, null, ex);
                }
            }

            if (buffers.isEmpty()) {
                if (!closed) {
                    throw new IllegalStateException("Should either have data, or be closed!");
                }
                return -1;
            }

            int toRead = Math.min(buffers.get(0).length - offset, len);
            System.arraycopy(buffers.get(0), offset, b, off, toRead);
            offset += toRead;
            if (buffers.get(0).length == offset) {
                buffers.remove(0);
                offset = 0;
            }
            return toRead;
        }

        @Override
        public void close() {
            synchronized (channel2Input) {
                channel2Input.remove(channelNumber);
            }
            synchronized (this) {
                closed = true;
                notifyAll();
            }
        }

        private synchronized void addBuffer(byte[] buffer) {
            buffers.add(buffer);
            notifyAll();
        }
    }
}
