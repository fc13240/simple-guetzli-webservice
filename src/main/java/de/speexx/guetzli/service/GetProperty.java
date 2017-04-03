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

import java.security.PrivilegedAction;
import java.util.Objects;

/**
 * Simple support to secure getting values from {@link System#getProperty(java.lang.String)}.
 * @author sascha.kohlmann
 */
final class GetProperty implements PrivilegedAction<String> {

    private final String propertyName;

    /**
     * Creates a new instance with the name of the property to fetch.
     * @param propertyName the name of the property.
     * @throws NullPointerException if and only if <em>propertyName</em> is {@literal null}.
     */
    public GetProperty(final String propertyName) {
        this.propertyName = Objects.requireNonNull(propertyName);
    }

    /**
     * Returns the {@linkplain System#getProperty(java.lang.String) System property}.
     * @return the value of the property of {@literal null}.
     */
    @Override
    public String run() {
        return System.getProperty(this.propertyName);
    }
}
