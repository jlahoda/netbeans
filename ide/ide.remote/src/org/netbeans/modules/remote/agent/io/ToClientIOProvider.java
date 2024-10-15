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
package org.netbeans.modules.remote.agent.io;

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.netbeans.api.io.Hyperlink;
import org.netbeans.api.io.OutputColor;
import org.netbeans.api.io.ShowOperation;
import org.netbeans.modules.remote.AsynchronousConnection;
import org.netbeans.modules.remote.StreamMultiplexor;
import org.netbeans.modules.remote.Streams;
import org.netbeans.modules.remote.ide.fs.io.IOHandler.GetIORequest;
import org.netbeans.modules.remote.ide.fs.io.IOHandler.IOTask;
import org.netbeans.modules.remote.ide.fs.io.IOHandler.IORequest;
import org.netbeans.spi.io.InputOutputProvider;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

public final class ToClientIOProvider implements InputOutputProvider<Integer, PrintWriter, Void, Void> {

    private final StreamMultiplexor main;
    private final AsynchronousConnection.Sender ioHandler;
    private final Map<Integer, Streams> io2IOStreams = new HashMap<>(); //TODO: clear!!

    public ToClientIOProvider(Streams mainChannel) {
        this.main = new StreamMultiplexor(mainChannel.in(), mainChannel.out());
        Streams ioControl = main.getStreamsForChannel(0);
        this.ioHandler = new AsynchronousConnection.Sender(ioControl.in(), ioControl.out());
    }

    @Override
    public String getId() {
        return "ToClientIOProvider";
    }

    @Override
    public Integer getIO(String name, boolean newIO, Lookup lookup) {
        try {
            int channel = ioHandler.sendAndReceive(IOTask.GET_IO, new GetIORequest(name, newIO), Integer.class).get();
            Streams ioStreams = main.getStreamsForChannel(channel);
            io2IOStreams.put(channel, ioStreams);
            return channel;
        } catch (IOException | InterruptedException | ExecutionException ex) {
            Exceptions.printStackTrace(ex);
            return -1;
        }
    }

    @Override
    public Reader getIn(Integer io) {
        return new CharArrayReader(new char[0]); //TODO: support input
    }

    @Override
    public PrintWriter getOut(Integer io) {
        OutputStreamWriter sink = new OutputStreamWriter(io2IOStreams.get(io).out());
        Writer flushingWriter = new Writer() {
            //TODO: BridgingOutputWriter does not auto-flush
            @Override
            public void write(char[] cbuf, int off, int len) throws IOException {
                sink.write(cbuf, off, len);
                sink.flush();
            }

            @Override
            public void flush() throws IOException {
                sink.flush();
            }

            @Override
            public void close() throws IOException {
                sink.close();
            }
        };
        return new PrintWriter(flushingWriter);
    }

    @Override
    public PrintWriter getErr(Integer io) {
        //            return new PrintWriter(io2IOStreams.get(io).out()); //TODO: separate out and err!
        return getOut(io); //TODO: separate out and err!
    }

    @Override
    public void print(Integer io, PrintWriter writer, String text, Hyperlink link, OutputColor color, boolean printLineEnd) {
        writer.print(text);
        if (printLineEnd) {
            writer.println();
        }
    }

    @Override
    public Lookup getIOLookup(Integer io) {
        return Lookup.EMPTY;
    }

    @Override
    public void resetIO(Integer io) {
        try {
            ioHandler.sendAndReceive(IOTask.RESET, new IORequest(io), Integer.class).get();
        } catch (IOException | ExecutionException |InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public void showIO(Integer io, Set<ShowOperation> operations) {
        try {
            ioHandler.sendAndReceive(IOTask.SHOW, new IORequest(io, operations.stream().map(o -> o.name()).toList()), Integer.class).get();
        } catch (IOException | ExecutionException |InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public void closeIO(Integer io) {
        try {
            ioHandler.sendAndReceive(IOTask.CLOSE, new IORequest(io), Integer.class).get();
        } catch (IOException | ExecutionException |InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        }
    }

    @Override
    public boolean isIOClosed(Integer io) {
        try {
            return ioHandler.sendAndReceive(IOTask.IS_CLOSED, new IORequest(io), Boolean.class).get();
        } catch (IOException | ExecutionException |InterruptedException ex) {
            Exceptions.printStackTrace(ex);
            return false;
        }
    }

    @Override
    public Void getCurrentPosition(Integer io, PrintWriter writer) {
        return null;
    }

    @Override
    public void scrollTo(Integer io, PrintWriter writer, Void position) {
    }

    @Override
    public Void startFold(Integer io, PrintWriter writer, boolean expanded) {
        return null;
    }

    @Override
    public void endFold(Integer io, PrintWriter writer, Void fold) {
    }

    @Override
    public void setFoldExpanded(Integer io, PrintWriter writer, Void fold, boolean expanded) {
    }

    @Override
    public String getIODescription(Integer io) {
        return "Remote IO";
    }

    @Override
    public void setIODescription(Integer io, String description) {
    }

}
