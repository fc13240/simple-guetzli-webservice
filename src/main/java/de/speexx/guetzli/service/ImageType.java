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

/**
 * Describes the type of an image.
 * @author sascha.kohlmann
 */
public enum ImageType {

    /** The type of JPEG images. */
    JPG("jpg", "image/jpeg"),
    /** The type of PNG images. */
    PNG("png", "image/png");
    
    private final String postfix;
    private final String mimeType;
    
    ImageType(final String postfix, final String mime) {
        assert postfix != null;
        assert mime != null;
        this.postfix = postfix;
        this.mimeType = mime;
    }

    /**
     * Returns the file postfix. E.g. for JPEG images the value is <code>jpg</code>.
     * @return the postfix. Always lower case.
     */
    public String getPostfix() {
        return this.postfix;
    }

    /**
     * The mime type (media type) of the image.
     * @return the mime type. Always lower case.
     */
    public String getMimeType() {
        return this.mimeType;
    }
}
