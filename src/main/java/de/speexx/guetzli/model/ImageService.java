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

import de.speexx.guetzli.service.UncheckedIOException;
import de.speexx.guetzli.transformer.GuetzliTransformationProcessor;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.nio.file.StandardOpenOption.READ;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author sascha.kohlmann
 */
public final class ImageService {
    
    private static Logger LOG = Logger.getLogger(ImageService.class.getSimpleName());

    private static final Semaphore EXEC_COUNTER = new Semaphore(2);
    private static final String META_FILE = "meta";
    
    public String newImage(final InputStream in, final long size, final ImageType type, final String name) throws IOException {
        final String targetDirName = UUID.randomUUID().toString().replace("-", "");
        LOG.log(Level.INFO, "Receive new image. ID {0}", targetDirName);
        final String fileName = createSourceFileName(type);
        Path targetFile = createImagePath(targetDirName, fileName);
        LOG.log(Level.INFO, "Target path: {0}", targetFile);
        
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Long>) () -> Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING));
            int quality = fetchQualityLevel(targetFile);
            final ImageMetadata meta = createAndStoreMetadata(targetDirName, name, size, type, quality);
            LOG.log(Level.INFO, "Stored new image for ", meta);
        } catch (final PrivilegedActionException ex) {
            LOG.log(Level.WARNING, "Failed to store new image for ID {0}", targetDirName);
            throw new IOException(ex.getException());
        }
        
        return targetDirName;
    }

    int fetchQualityLevel(final Path targetFile) throws IOException {
        final ImageQualityIdentifier qIdentifier = new ImageQualityIdentifier();
        return qIdentifier.fetchQuality(targetFile);
    }

    ImageMetadata createAndStoreMetadata(final String targetDirName,
                                         final String name,
                                         final long size,
                                         final ImageType type,
                                         final int qualityLevel) throws IOException {
        assert targetDirName != null;
        assert name != null;
        assert size >= 0;
        assert type != null;

        final ImageMetadata meta = new ImageMetadata();
        meta.setId(targetDirName);
        if (name != null && name.trim().length() != 0) {
            meta.setSourceName(name);
        }
        meta.setSourceSize(size);
        meta.setStatus(ProcessStatus.stored);
        meta.setSourceType(type);
        meta.setSourceQuality(qualityLevel);
        
        storeMetadata(meta);
        
        return meta;
    }
    
    public void transformToGuetzli(final String id) throws TransformationException {
        try {
            LOG.log(Level.INFO, "Start guetzli transformation for ID {0}", id);
            final ImageMetadata meta = getMetadata(id);
            meta.setStatus(ProcessStatus.waiting);
            storeMetadata(meta);

            final GuetzliTransformationProcessor processor = new GuetzliTransformationProcessor();
            final Path sourcePath = createSourceImagePath(meta.getSourceType(), id);
            final Path targetPath = createTargetImagePath(id);

            EXEC_COUNTER.acquire();
            try {
                meta.setStatus(ProcessStatus.transforming);
                storeMetadata(meta);
                processor.transform(sourcePath, targetPath);
                LOG.log(Level.INFO, "Finished guetzli transformation for ID {0}", id);
            } finally {
                EXEC_COUNTER.release();
            }

            final ImageQualityIdentifier qIdentifer = new ImageQualityIdentifier();
            meta.setTargetQuality(qIdentifer.fetchQuality(targetPath));
            meta.setStatus(ProcessStatus.transformed);
            meta.setTargetSize(Files.size(targetPath));
            storeMetadata(meta);
            LOG.log(Level.INFO, "Finialized transformation for ", meta);
            
        } catch (final Exception e) {
            LOG.log(Level.WARNING, "Failing guetzli transformation for ID " + id, e);
            try {
                final ImageMetadata meta = getMetadata(id);
                meta.setStatus(ProcessStatus.failed);
                storeMetadata(meta);
            } catch (final IOException ex) {
                throw new TransformationException(ex);
            }
            throw new TransformationException(e);
        }
    }
    
    public InputStream getSourceImage(final String id) throws IOException {
        final ImageMetadata meta = getMetadata(id);
        final Path targetFile = createSourceImagePath(meta.getSourceType(), id);
        return fetchImageInputStream(targetFile);
    }

    public InputStream getTargetImage(final String id) throws IOException {
        final Path targetFile = createTargetImagePath(id);
        return fetchImageInputStream(targetFile);
    }

    InputStream fetchImageInputStream(final Path imagePath) throws IOException {
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<InputStream>) () -> Files.newInputStream(imagePath, READ));
        } catch (final PrivilegedActionException ex) {
            final Exception cause = ex.getException();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new AccessDeniedException("Access denied for image wih ID " + imagePath);
        }
    }

    Path createSourceImagePath(final ImageType type, final String id) {
        final String fileName = createSourceFileName(type);
        final Path targetFile = createImagePath(id, fileName);
        return targetFile;
    }

    Path createImagePath(final String id, final String fileName) {
        assert id != null;
        assert fileName != null;

        final Path basePath = basePath();
        final Path targetDir = targetDirectory(basePath, id);
        return Paths.get(targetDir.toString(), fileName);
    }
    
    Path createTargetImagePath(final String id) {
        final String fileName = targetName();
        return createImagePath(id, fileName);
    }
    
    String targetName() {
        return "target.jpg";
    }
    
    public ImageMetadata getMetadata(final String id) throws IOException {
        Objects.requireNonNull(id);

        final Path metaFile = createMetaPath(id);
        try (final BufferedReader reader = AccessController.doPrivileged((PrivilegedExceptionAction<BufferedReader>) ()
                    -> Files.newBufferedReader(metaFile, Charset.forName("UTF-8")));) {
            final Properties p = new Properties();
            p.load(reader);
            return ImageMetadata.toMeta(p);
        } catch (final PrivilegedActionException ex) {
            final Exception cause = ex.getException();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IllegalStateException();
        }
    }
    
    public void delete(final String id) {
        
    }

    void storeMetadata(final ImageMetadata meta) throws IOException {
        assert meta != null;
        assert meta.getId() != null;
        
        final String id = meta.getId();
        final Path metaFile = createMetaPath(id);
        try (final BufferedWriter writer = AccessController.doPrivileged((PrivilegedExceptionAction<BufferedWriter>) ()
                    -> Files.newBufferedWriter(metaFile, Charset.forName("UTF-8"), CREATE, WRITE));) {
            final Properties p = ImageMetadata.toProperties(meta);
            p.store(writer, "");
        } catch (final PrivilegedActionException ex) {
            LOG.log(Level.WARNING, "Unable to store metadata: {0}", meta);
            final Exception cause = ex.getException();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IllegalStateException();
        }
    }
    
    Path createMetaPath(final String id) {
        assert id != null;
        final Path baseDir = basePath();
        final Path targetDir = targetDirectory(baseDir, id);
        return Paths.get(targetDir.toString(), META_FILE);
    }

    Path targetDirectory(final Path basePath, final String dirName) {
        assert basePath != null;
        assert dirName != null;

        final Path dir = Paths.get(basePath.toString(), dirName);
        return pathExists(dir);
    }

    String createSourceFileName(final ImageType type) {
        assert type != null;
        return "source." + type.getPostfix();
    }
    
    Path basePath() {
        final String basePath = AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getProperty("guetzli.service.storage"));
        if (basePath != null) {
            return pathExists(Paths.get(basePath));
        }
        return basePathInHomeDirectory();
    }

    Path basePathInHomeDirectory() {
        final String userHome = userHome();
        final Path homePath = Paths.get(userHome, ".guetzli-data");
        return pathExists(homePath);
    }

    Path pathExists(final Path path) throws UncheckedIOException {
        assert path != null;

        if (!Files.exists(path)) {
            try {
                return AccessController.doPrivileged((PrivilegedExceptionAction<Path>) () -> {
                    return Files.createDirectories(path);
                });
            } catch (final PrivilegedActionException e) {
                throw new UncheckedIOException(e.getException());
            }
        }
        return path;
    }

    String userHome() {
        return AccessController.doPrivileged((PrivilegedAction<String>) () -> System.getProperty("user.home"));
    }
}
