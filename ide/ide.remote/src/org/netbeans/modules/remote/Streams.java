package org.netbeans.modules.remote;

import java.io.InputStream;
import java.io.OutputStream;


/**
 *
 */
public record Streams(InputStream in, OutputStream out) {
}
