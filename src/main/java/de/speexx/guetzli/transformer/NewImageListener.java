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

import de.speexx.guetzli.model.ImageService;
import de.speexx.guetzli.service.event.ImageEvent;
import de.speexx.guetzli.service.event.NewImage;
import java.util.logging.Logger;
import javax.annotation.Resource;
import javax.ejb.Asynchronous;
import javax.ejb.Stateless;
import javax.enterprise.event.Observes;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.inject.Inject;

/**
 *
 * @author sascha.kohlmann
 */
@Stateless
public class NewImageListener {
    
    private static Logger LOG = Logger.getLogger(NewImageListener.class.getSimpleName());
    
    @Resource private ManagedExecutorService managedExecutorService;
    @Inject private ImageService imgSrv;

    @Asynchronous
    public void newImage(final @NewImage @Observes ImageEvent imageEvent) {
        if (imageEvent == null) {
            return;
        }

        final String id = imageEvent.getImageId();
        this.managedExecutorService.execute(() -> NewImageListener.this.imgSrv.transformToGuetzli(id));
    }
}
