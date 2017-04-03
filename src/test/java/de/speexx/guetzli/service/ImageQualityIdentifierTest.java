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

import java.net.URI;
import java.net.URL;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 *
 * @author sascha.kohlmann
 */
public class ImageQualityIdentifierTest {
    
    @Disabled
    @Test
    public void fetchQuality() throws Exception {
        final URL url = this.getClass().getResource("/public_domain.jpg");
        final URI uri = url.toURI();
        
        final ImageQualityIdentifier iqi = new ImageQualityIdentifier();
        final int quality = iqi.fetchQuality(Paths.get(uri));
        
        assertEquals(quality, 90);
    }
}
