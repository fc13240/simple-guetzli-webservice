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
package de.speexx.guetzli.transformer;

import de.speexx.guetzli.service.TransformationException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transforms images with the <code>guetzli</code> command line tool JPG files.
 * @author sascha.kohlmann
 */
public final class GuetzliTransformationProcessor {
    
    private static final Logger LOG = Logger.getLogger(GuetzliTransformationProcessor.class.getSimpleName());

    private static final String PATH_ENV_VARIABLE = "PATH";
    private static final String GUETZLI_MAX_MEMORY = "6000";
    private static final String GUETZLI_CMD = "guetzli";
    

    public void transform(final Path source, final Path target) throws TransformationException {
        transform(source, target, 0);
    }

    public void transform(final Path source, final Path target, final int targetQuality) throws TransformationException {
        Objects.requireNonNull(source);
        Objects.requireNonNull(target);

        final ProcessBuilder pb;
        if (targetQuality != 0) {
            pb = new ProcessBuilder(GUETZLI_CMD,
                    "--memlimit", GUETZLI_MAX_MEMORY,
                    "--quality", String.valueOf(targetQuality),
                    source.toString(),
                    target.toString());
        } else {
            pb = new ProcessBuilder(GUETZLI_CMD,
                    "--memlimit", GUETZLI_MAX_MEMORY,
                    source.toString(),
                    target.toString());
        }
        configureProcessBuilder(pb, true, source.getParent());
        
        executeProcess(pb, source, target);
    }

    void executeProcess(final ProcessBuilder pb,
                        final Path source,
                        final Path target) throws UncheckedIOException, TransformationException {
        assert pb != null;
        assert source != null;
        assert target != null;

        try {
            LOG.log(Level.INFO, "Start external process to transform {0} to {1}", new Object[] {source, target});
            final Process p = AccessController.doPrivileged((PrivilegedExceptionAction<Process>) () -> pb.start());
            boolean finished = false;
            for (int i = 0; i < timeoutTries(); i++) {
                try {
                    finished = p.waitFor(timeoutValue(), timeoutUnit());
                    if (finished) {
                        break;
                    }
                } catch (final InterruptedException ex) {
                    LOG.log(Level.WARNING, "Interuped while processing transformation from {0} to {1}", new Object[]{source, target});
                }
            }
            if (!finished) {
                p.destroy();
                throw new TransformationException("Transformation timeout");
            }
            if (p.exitValue() != 0) {
                throw new TransformationException("Transformation failed: " +  p.exitValue());
            }
        } catch (final PrivilegedActionException ex) {
            throw new UncheckedIOException((IOException) ex.getException());
        }
    }
    
    void configureProcessBuilder(final ProcessBuilder pb, final boolean redirectOutput, final Path targetPath) {
        assert pb != null;
        assert targetPath != null;

        final String path = AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getenv(PATH_ENV_VARIABLE));
        final Map<String, String> env = AccessController.doPrivileged((PrivilegedAction<Map<String,String>>) () -> pb.environment());
        env.put(PATH_ENV_VARIABLE, path);

        if (redirectOutput) {
            final File log = new File(targetPath.toFile(), ".guetzli-processor.log");
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(log));
        }
    }
    
    long timeoutValue() {
        return 5;
    }

    TimeUnit timeoutUnit() {
        return TimeUnit.SECONDS;
    }
    
    long timeoutTries() {
        return 180;
    }
}
