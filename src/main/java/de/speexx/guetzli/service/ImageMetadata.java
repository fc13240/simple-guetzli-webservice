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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Bean container for the meta data of the image transformation. 
 * @author sascha.kohlmann
 */
public final class ImageMetadata {
    
    private static final String SOURCE_NAME_KEY = "source.name";
    private static final String PROCESS_STATUS_KEY = "process.status";
    private static final String SOURCE_QUALITY_KEY = "source.quality";
    private static final String SOURCE_TYPE_KEY = "source.type";
    private static final String SOURCE_SIZE_KEY = "source.size";
    private static final String TARGET_QUALITY_KEY = "target.quality";
    private static final String TARGET_SIZE_KEY = "target.size";
    private static final String ID_KEY = "contentId";
    private static final String CREATION_DATETIME_KEY = "stored.datetime";
    
    private String contentId;
    private ProcessStatus status;
    private LocalDateTime creationDatetime = LocalDateTime.now();
    private ImageType sourceType;
    private String sourceName;
    private int sourceQuality;
    private long sourceSize;
    private int targetQuality;
    private long targetSize;

    /**
     * Returns the ID of the image to transform.
     * @return the ID. Never {@code null}.
     */
    public String getContentId() {
        return contentId;
    }

    void setContentId(String contentId) {
        this.contentId = contentId;
    }

    /**
     * Mime type of the image to tansform with {@literal guetzli}.
     * @return source image type. Never {@code null}.
     */
    public ImageType getSourceType() {
        return sourceType;
    }

    void setSourceType(ImageType sourceType) {
        this.sourceType = sourceType;
    }

    /**
     * Name of the uploaded source image if given.
     * @return the source image name. Can be {@code null}.
     */
    public String getSourceName() {
        return sourceName;
    }

    void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    /**
     * The process status of the source image of the given ID.
     * @return the process status. Never {@link null}.
     */
    public ProcessStatus getStatus() {
        return status;
    }

    void setStatus(ProcessStatus status) {
        this.status = status;
    }

    /**
     * The quality level of the source image.
     * @return the qualiy level between 0 and 100.
     */
    public int getSourceQuality() {
        return sourceQuality;
    }

    void setSourceQuality(int sourceQuality) {
        this.sourceQuality = sourceQuality;
    }

    /**
     * The size of the source image.
     * @return the size between 1 and {@link Long#MAX_VALUE}
     */
    public long getSourceSize() {
        return sourceSize;
    }

    void setSourceSize(long sourceSize) {
        this.sourceSize = sourceSize;
    }

    /**
     * The quality level of the target image.
     * @return the qualiy level between 0 and 100.
     */
    public int getTargetQuality() {
        return targetQuality;
    }

    void setTargetQuality(int targetQuality) {
        this.targetQuality = targetQuality;
    }

    /**
     * The size of the target image.
     * @return the size between 1 and {@link Long#MAX_VALUE}
     */
    public long getTargetSize() {
        return targetSize;
    }

    void setTargetSize(long targetSize) {
        this.targetSize = targetSize;
    }

    /**
     * The creation time when stored the source image.
     * @return the source stored image date time. Never {@code null}.
     */
    public LocalDateTime getCreationDatetime() {
        return creationDatetime;
    }

    void setCreationDatetime(LocalDateTime creationDatetime) {
        this.creationDatetime = creationDatetime;
    }

    @Override
    public String toString() {
        return "ImageMetadata{" + "contentId=" + contentId + ", status=" + status + ", creationDatetime=" + creationDatetime + ", sourceType=" + sourceType + ", sourceName=" + sourceName + ", sourceQuality=" + sourceQuality + ", sourceSize=" + sourceSize + ", targetQuality=" + targetQuality + ", targetSize=" + targetSize + '}';
    }

    static Properties toProperties(final ImageMetadata metadata) {
        assert metadata != null;
        assert metadata.getContentId() != null;
                 
        final Properties p = new Properties();

        p.setProperty(ID_KEY, metadata.getContentId());
        if (metadata.getSourceName() != null) {
            p.setProperty(SOURCE_NAME_KEY, metadata.getSourceName());
        }
        p.setProperty(SOURCE_TYPE_KEY, String.valueOf(metadata.getSourceType().name()));
        p.setProperty(SOURCE_QUALITY_KEY, String.valueOf(metadata.getSourceQuality()));
        p.setProperty(SOURCE_SIZE_KEY, String.valueOf(metadata.getSourceSize()));
        p.setProperty(TARGET_QUALITY_KEY, String.valueOf(metadata.getTargetQuality()));
        p.setProperty(TARGET_SIZE_KEY, String.valueOf(metadata.getTargetSize()));
        p.setProperty(PROCESS_STATUS_KEY, metadata.getStatus().name());
        p.setProperty(CREATION_DATETIME_KEY, DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(metadata.getCreationDatetime()));
        
        return p;
    }
    
    static ImageMetadata toMeta(final Properties p) {
        assert p != null;
        assert p.getProperty(ID_KEY) != null;
        
        final ImageMetadata meta = new ImageMetadata();
        meta.setContentId(p.getProperty(ID_KEY));
        if (p.containsKey(SOURCE_NAME_KEY)) {
            meta.setSourceName(p.getProperty(SOURCE_NAME_KEY));
        }
        if (p.containsKey(PROCESS_STATUS_KEY)) {
            meta.setStatus(ProcessStatus.valueOf(p.getProperty(PROCESS_STATUS_KEY)));
        }
        if (p.containsKey(SOURCE_TYPE_KEY)) {
            meta.setSourceType(ImageType.valueOf(p.getProperty(SOURCE_TYPE_KEY)));
        }
        if (p.containsKey(SOURCE_QUALITY_KEY)) {
            meta.setSourceQuality(Integer.parseInt(p.getProperty(SOURCE_QUALITY_KEY)));
        }
        if (p.containsKey(SOURCE_SIZE_KEY)) {
            meta.setSourceSize(Long.parseLong(p.getProperty(SOURCE_SIZE_KEY)));
        }
        if (p.containsKey(TARGET_QUALITY_KEY)) {
            meta.setTargetQuality(Integer.parseInt(p.getProperty(TARGET_QUALITY_KEY)));
        }
        if (p.containsKey(TARGET_SIZE_KEY)) {
            meta.setTargetSize(Long.parseLong(p.getProperty(TARGET_SIZE_KEY)));
        }
        if (p.containsKey(CREATION_DATETIME_KEY)) {
            meta.setCreationDatetime(LocalDateTime.parse(p.getProperty(CREATION_DATETIME_KEY), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        
        return meta;
    }
}
