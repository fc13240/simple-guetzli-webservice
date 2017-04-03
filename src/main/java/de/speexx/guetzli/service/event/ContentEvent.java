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
package de.speexx.guetzli.service.event;

/**
 * An event for an image containig a content ID.
 * @author sascha.kohlmann
 */
public final class ContentEvent {

    private final String contentId;

    /**
     * Creates a new instance.
     * @param contentId a content ID
     */
    public ContentEvent(final String contentId) {
        this.contentId = contentId;
    }

    public String getContentId() {
        return this.contentId;
    }

    @Override
    public String toString() {
        return "ImageEvent{" + "contentId=" + contentId + '}';
    }
}
