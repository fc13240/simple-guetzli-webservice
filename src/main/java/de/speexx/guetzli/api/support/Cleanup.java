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
package de.speexx.guetzli.api.support;

import de.speexx.guetzli.service.ImageMetadata;
import de.speexx.guetzli.service.ImageService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.Schedule;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.inject.Inject;

/**
 * Removes uploaded images and transformed images which are older 24 hours.
 * The implementation starts when deploying the project to the application server as a
 * stateless Enterprise Java Bean.
 * @author sascha.kohlmann
 * @see ImageService#delete(java.lang.String) 
 */
@Singleton
@Startup
public class Cleanup {

    private static Logger LOG = Logger.getLogger(Cleanup.class.getSimpleName());
    private static final long MAX_AGE_SECONDS = 60 * 24 * 60;

    @Inject private ImageService imgSrv;

    /**
     * Removes all content ID which are older 24 hours.
     * 
     * <p><strong>Usage:</strong> method call only in CDI context.</p>
     * <p><strong>Configuration:</strong> scheduled to run every 30 minutes.</p>
     */
    @Schedule(second="11", minute="*/30", hour="*", persistent = false)
    public void process() {
        LOG.log(Level.INFO, "guetzli service automated cleanup process started");
        this.imgSrv.getContentIds().forEachRemaining(id -> {
            try {
                final ImageMetadata meta = this.imgSrv.getMetadata(id);
                final LocalDateTime creationData = meta.getCreationDatetime();
                if (checkForDeletion(creationData)) {
                    LOG.log(Level.INFO, "Entry {0} older than {1}s ({3}). Scheduled for deletion.", new Object[] {id, maxAgeSeconds(), creationData});
                    triggerDeletion(id);
                }
            } catch (final IOException ex) {
                LOG.log(Level.WARNING, "Unable to get metadata for ID " + id, ex);
            }
        });
    }

    void triggerDeletion(final String id) {
        assert id != null;

        try {
            this.imgSrv.delete(id);
        } catch (final IllegalStateException | IOException e) {
            LOG.log(Level.WARNING, "Unable to delete ID " + id, e);
        }
    }

    static boolean checkForDeletion(final LocalDateTime creationData) {
        assert creationData != null;
        return LocalDateTime.now().compareTo(creationData.plusSeconds(MAX_AGE_SECONDS)) > 0;
    }
    
    long maxAgeSeconds() {
        return MAX_AGE_SECONDS;
    }
}
