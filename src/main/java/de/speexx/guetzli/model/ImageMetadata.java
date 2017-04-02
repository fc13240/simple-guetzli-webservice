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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 *
 * @author sascha.kohlmann
 */
public final class ImageMetadata {
    
    private static final String SOURCE_NAME_KEY = "source_name";
    private static final String PROCESS_STATUS_KEY = "process_status";
    private static final String SOURCE_QUALITY_KEY = "source_quality";
    private static final String SOURCE_TYPE_KEY = "source_type";
    private static final String SOURCE_SIZE_KEY = "source_size";
    private static final String TARGET_QUALITY_KEY = "target_quality";
    private static final String TARGET_SIZE_KEY = "target_size";
    private static final String ID_KEY = "id";
    private static final String CREATION_DATETIME_KEY = "creation_datetime";
    
    private String id;
    private ProcessStatus status;
    private LocalDateTime creationDatetime = LocalDateTime.now();
    private ImageType sourceType;
    private String sourceName;
    private int sourceQuality;
    private long sourceSize;
    private int targetQuality;
    private long targetSize;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ImageType getSourceType() {
        return sourceType;
    }

    public void setSourceType(ImageType sourceType) {
        this.sourceType = sourceType;
    }

    
    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public ProcessStatus getStatus() {
        return status;
    }

    public void setStatus(ProcessStatus status) {
        this.status = status;
    }

    public int getSourceQuality() {
        return sourceQuality;
    }

    public void setSourceQuality(int sourceQuality) {
        this.sourceQuality = sourceQuality;
    }

    public long getSourceSize() {
        return sourceSize;
    }

    public void setSourceSize(long sourceSize) {
        this.sourceSize = sourceSize;
    }

    public int getTargetQuality() {
        return targetQuality;
    }

    public void setTargetQuality(int targetQuality) {
        this.targetQuality = targetQuality;
    }

    public long getTargetSize() {
        return targetSize;
    }

    public void setTargetSize(long targetSize) {
        this.targetSize = targetSize;
    }

    public LocalDateTime getCreationDatetime() {
        return creationDatetime;
    }

    public void setCreationDatetime(LocalDateTime creationDatetime) {
        this.creationDatetime = creationDatetime;
    }

    @Override
    public String toString() {
        return "ImageMetadata{" + "id=" + id + ", status=" + status + ", creationDatetime=" + creationDatetime + ", sourceType=" + sourceType + ", sourceName=" + sourceName + ", sourceQuality=" + sourceQuality + ", sourceSize=" + sourceSize + ", targetQuality=" + targetQuality + ", targetSize=" + targetSize + '}';
    }

    static Properties toProperties(final ImageMetadata metadata) {
        assert metadata != null;
        assert metadata.getId() != null;
                 
        final Properties p = new Properties();

        p.setProperty(ID_KEY, metadata.getId());
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
        meta.setId(p.getProperty(ID_KEY));
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
