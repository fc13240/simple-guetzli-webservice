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
package de.speexx.guetzli.service;

import de.speexx.guetzli.io.DeleteDirectoryVisitor;
import de.speexx.guetzli.transformer.GuetzliTransformationProcessor;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
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
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Main service to handle the <code>guetzli</code> transformation.
 * <p>A new image is stored in the filesystem. The base storage path can be configured with system property key
 * <code>guetzli.service.storage</code>. Images are stored in a sub directory of the base directory where the
 * name of the directory is the content ID.</p>
 *
 * @author sascha.kohlmann
 */
public final class ImageService {
    
    private static Logger LOG = Logger.getLogger(ImageService.class.getSimpleName());

    private static boolean basePathLogged = false;

    private static final Semaphore EXEC_COUNTER = new Semaphore(2);
    private static final String META_FILE = "meta";
    
    /**
     * Stores a new image.
     * @param in a stream containing the raw imae data
     * @param size the size of the image
     * @param type the type of the image
     * @param name the optional name of the image. Can be {@code null}.
     * @return the content ID of the stored image
     * @throws IOException if and only if it is not possible to store the image.
     */
    public String newImage(final InputStream in, final long size, final ImageType type, final String name) throws IOException {
        final String targetDirName = UUID.randomUUID().toString().replace("-", "");
        LOG.log(Level.INFO, "Receive new image. ID {0}", targetDirName);
        final String fileName = createSourceFileName(type);
        Path targetFile = createImagePath(targetDirName, fileName);
        
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Long>) () -> Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING));
            int quality = fetchQualityLevel(targetFile, type);
            final ImageMetadata meta = createAndStoreMetadata(targetDirName, name, size, type, quality);
            LOG.log(Level.INFO, "Stored new image for {0}", meta);
        } catch (final PrivilegedActionException ex) {
            LOG.log(Level.WARNING, "Failed to store new image for content ID {0}", targetDirName);
            throw new IOException(ex.getException());
        }
        
        return targetDirName;
    }

    int fetchQualityLevel(final Path targetFile, final ImageType type) throws IOException {
        if (type == ImageType.JPG) {
            final ImageQualityIdentifier qIdentifier = new ImageQualityIdentifier();
            return qIdentifier.fetchQuality(targetFile);
        }
        return 100; // PNGs always have 100 quality
    }

    ImageMetadata createAndStoreMetadata(final String targetDirName,
                                         final String name,
                                         final long size,
                                         final ImageType type,
                                         final int qualityLevel) throws IOException {
        assert targetDirName != null;
        assert size >= 0;
        assert type != null;

        final ImageMetadata meta = new ImageMetadata();
        meta.setContentId(targetDirName);
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
    
    /**
     * Starts the transormation process of a
     * {@linkplain #newImage(java.io.InputStream, long, de.speexx.guetzli.service.ImageType, java.lang.String) new image}.
     * 
     * @param contentId the ID of the content to transform
     * @throws TransformationException if and only if it is not possible to start the transformation process.
     */
    public void transformToGuetzli(final String contentId) throws TransformationException {
        try {
            LOG.log(Level.INFO, "Start guetzli transformation for content ID {0}", contentId);
            final ImageMetadata meta = getMetadata(contentId);
            if (meta.getStatus() != ProcessStatus.stored) {
                return; // don't start the process twice.
            }
            meta.setStatus(ProcessStatus.waiting);
            storeMetadata(meta);

            final GuetzliTransformationProcessor processor = new GuetzliTransformationProcessor();
            final Path sourcePath = createSourceImagePath(meta.getSourceType(), contentId);
            final Path targetPath = createTargetImagePath(contentId);

            EXEC_COUNTER.acquire();
            try {
                meta.setStatus(ProcessStatus.transforming);
                storeMetadata(meta);
                processor.transform(sourcePath, targetPath);
                LOG.log(Level.INFO, "Finished guetzli transformation for content ID {0}", contentId);
            } finally {
                EXEC_COUNTER.release();
            }

            final ImageQualityIdentifier qIdentifer = new ImageQualityIdentifier();
            meta.setTargetQuality(qIdentifer.fetchQuality(targetPath));
            meta.setStatus(ProcessStatus.transformed);
            meta.setTargetSize(Files.size(targetPath));
            storeMetadata(meta);
            LOG.log(Level.INFO, "Finialized transformation for {0}", meta);
            
        } catch (final Exception e) {
            LOG.log(Level.WARNING, "Failing guetzli transformation for content ID " + contentId, e);
            try {
                final ImageMetadata meta = getMetadata(contentId);
                meta.setStatus(ProcessStatus.failed);
                storeMetadata(meta);
            } catch (final IOException ex) {
                throw new TransformationException(ex);
            }
            throw new TransformationException(e);
        }
    }
    
    /**
     * Returns a stream to read the source image raw data for the given content ID.
     * @param contentId content ID to fetch the source image raw data for.
     * @return the source image raw data stream. Never {@code null}.
     * @throws IOException if and only if a problem occurs during getting the source image raw data.
     * @throws FileNotFoundException if and only if there is no source image for the given content ID.
     * @throws NullPointerException if no content ID is given.
     */
    public InputStream getSourceImage(final String contentId) throws IOException, FileNotFoundException {
        Objects.requireNonNull(contentId);
        final ImageMetadata meta = getMetadata(contentId);
        final Path targetFile = createSourceImagePath(meta.getSourceType(), contentId);
        return fetchImageInputStream(targetFile);
    }

    /**
     * Returns a stream to read the target image raw data for the given content ID. The
     * {@link ProcessStatus} must be {@linkplain ProcessStatus#transformed transformed}.
     * @param contentId content ID to fetch the target image raw data for.
     * @return the target image raw data stream. Never {@code null}.
     * @throws IOException if and only if a problem occurs during getting the target image raw data.
     * @throws FileNotFoundException if and only if there is no target image for the given content ID.
     * @throws NullPointerException if no content ID is given.
     */
    public InputStream getTargetImage(final String contentId) throws IOException, FileNotFoundException {
        Objects.requireNonNull(contentId);
        final Path targetFile = createTargetImagePath(contentId);
        return fetchImageInputStream(targetFile);
    }

    InputStream fetchImageInputStream(final Path imagePath) throws IOException, FileNotFoundException {
        assert imagePath != null;
        try {
            return AccessController.doPrivileged((PrivilegedExceptionAction<InputStream>) () -> Files.newInputStream(imagePath, READ));
        } catch (final PrivilegedActionException ex) {
            final Exception cause = ex.getException();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new AccessDeniedException("Access denied for image wih content ID " + imagePath);
        }
    }

    Path createSourceImagePath(final ImageType type, final String contentId) {
        final String fileName = createSourceFileName(type);
        final Path targetFile = createImagePath(contentId, fileName);
        return targetFile;
    }

    Path createImagePath(final String contentId, final String fileName) {
        assert contentId != null;
        assert fileName != null;

        final Path basePath = basePath();
        final Path targetDir = targetDirectory(basePath, contentId);
        return Paths.get(targetDir.toString(), fileName);
    }
    
    Path createTargetImagePath(final String contentId) {
        final String fileName = targetName();
        return createImagePath(contentId, fileName);
    }
    
    String targetName() {
        return "target.jpg";
    }

    /**
     * Returns the meta data for the given content ID.
     * @param contentId the content ID to fetch the meta data for.
     * @return the meta data for the content ID. Never {@code null}.
     * @throws IOException if and only if it is not possible to fetch the meta data.
     * @throws FileNotFoundException if and only if there is no meta data for the given content ID.
     * @throws NullPointerException if no content ID is given.
     */
    public ImageMetadata getMetadata(final String contentId) throws IOException, FileNotFoundException {
        Objects.requireNonNull(contentId);
        
        final Path metaFile = createMetaPath(contentId);
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
    
    /**
     * Deletes all data for the given content ID. 
     * @param contentId the content ID to delete
     * @throws IOException if and only if it is not possible to delete the data for the given content ID.
     */
    public void delete(final String contentId) throws IOException {
        if (contentId == null) {
            return;
        }
        
        final Path targetDir = targetDirectory(basePath(), contentId);
        LOG.log(Level.INFO, "Delete ID {0} in directory {1}", new Object[] {contentId, targetDir});
        try {
            AccessController.doPrivileged((PrivilegedExceptionAction<Path>) () -> Files.walkFileTree(targetDir, new DeleteDirectoryVisitor()));
        } catch (final PrivilegedActionException ex) {
            final Exception cause = ex.getException();
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IllegalStateException(ex);
        }
        
    }
    
    /**
     * Returns an iterator over all available content IDs.
     * @return never {@code null}
     */
    public Iterator<String> getContentIds() {
        try {
            final Path base = basePath();
            final Set<String> ids = AccessController.doPrivileged((PrivilegedExceptionAction<Set<String>>) ()
                    -> Files.list(base)
                            .filter(path -> Files.isDirectory(path))
                            .map(path -> path.getFileName().toString())
                            .collect(Collectors.toSet()));
            return ids.iterator();
        } catch (final PrivilegedActionException ex) {
            LOG.log(Level.WARNING, "Unable to get content IDs.", ex.getException());
            return Collections.emptyIterator();
        }
    }

    void storeMetadata(final ImageMetadata meta) throws IOException {
        assert meta != null;
        assert meta.getContentId() != null;
        
        final String id = meta.getContentId();
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
    
    Path createMetaPath(final String contentId) {
        assert contentId != null;
        final Path baseDir = basePath();
        final Path targetDir = targetDirectory(baseDir, contentId);
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
        final String basePath = AccessController.doPrivileged((PrivilegedAction<String>) () -> new GetProperty("guetzli.service.storage").run());
        if (basePath != null) {
            logBasePath(Paths.get(basePath));
            return pathExists(Paths.get(basePath));
        }
        final Path homePath = basePathInHomeDirectory();
        logBasePath(homePath);
        return homePath;
    }
    
    static void logBasePath(final Path basePath) {
        if (!basePathLogged) {
            LOG.log(Level.INFO, "guetzli-service storage base path: {0}", basePath);
            basePathLogged = true;
        }
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
                throw new UncheckedIOException((IOException) e.getException());
            }
        }
        return path;
    }

    String userHome() {
        return AccessController.doPrivileged((PrivilegedAction<String>) () -> new GetProperty("user.home").run());
    }
}
