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

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Disabled;

/**
 *
 * @author sascha.kohlmann
 */
public class ProcessorTest {

    @Disabled
    @Test
    public void transformation() throws Exception {
        final URL sourceUrl = this.getClass().getResource("/public_domain.jpg");
        final URI sourceImgUri = sourceUrl.toURI();
        final Path sourcePath = Paths.get(sourceImgUri);
        
        final Path targetFile = Files.createTempFile("GuetzliTransformationProcessor.", ".jpg");

        try {
            final GuetzliTransformationProcessor p = new GuetzliTransformationProcessor();
            p.transform(sourcePath, targetFile);
            assertTrue(Files.size(targetFile) > 0);
        } finally {
            Files.deleteIfExists(targetFile);
        }
    }
}
