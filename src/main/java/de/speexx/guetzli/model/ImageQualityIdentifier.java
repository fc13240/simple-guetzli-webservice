/*
 * A simple wrapper for Googles guetzli JPEG compressor.
 * Copyright (C) 2017 Sascha Kohlmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.speexx.guetzli.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author sascha.kohlmann
 */
final class ImageQualityIdentifier {
    
    private static Logger LOG = Logger.getLogger(ImageQualityIdentifier.class.getSimpleName());

    private static final String PATH_ENV_VARIABLE = "PATH";

    public int fetchQuality(final Path sourcePath) throws IOException {
        final String path = sourcePath.toString();

        LOG.log(Level.INFO, "Fetch quality level for {0}", path);
        final ProcessBuilder pb = new ProcessBuilder("identify", "-format", "%Q", path);
        configureProcessBuilder(pb, false);
        final Process p;
        try {
            p = AccessController.doPrivileged((PrivilegedExceptionAction<Process>) () -> pb.start());
        } catch (final PrivilegedActionException ex) {
            throw new UncheckedIOException((IOException)ex.getException());
        }
                
        try (final BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            final String level = in.readLine();
            try {
                final boolean finished = p.waitFor(5, TimeUnit.SECONDS);
                if (!finished) {
                    p.destroy();
                }
                final int exitStatus = p.exitValue();
                if (exitStatus != 0) {
                    throw new IllegalStateException("Problems fetching quality level for " + sourcePath);
                }
            } catch (final InterruptedException ex) {
                throw new TransformationException(ex);
            }
            try {
                return Integer.parseInt(level);
            } catch (final NumberFormatException e) {
                throw new TransformationException(e); 
            }
        }
    }

    void configureProcessBuilder(final ProcessBuilder pb, final boolean redirectOutput) {
        assert pb != null;

        final String path = AccessController.doPrivileged(new PrivilegedAction<String>() {
            @Override
            public String run() {
                return System.getenv(PATH_ENV_VARIABLE);
            }
        });
        final Map<String, String> env = AccessController.doPrivileged((PrivilegedAction<Map<String,String>>) () -> pb.environment());
        env.put(PATH_ENV_VARIABLE, path);

        if (redirectOutput) {
            final File log = new File(userHome() + File.separator + ".guetzli-processor.log");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
        }
    }

    String userHome() {
        return AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getProperty("user.home"));
    }
}
