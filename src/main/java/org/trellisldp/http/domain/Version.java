/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.trellisldp.http.domain;

import static java.lang.Long.parseLong;
import static java.time.Instant.ofEpochMilli;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.slf4j.LoggerFactory.getLogger;

import java.time.Instant;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;

import org.slf4j.Logger;

/**
 * A class representing a version URI parameter
 *
 * @author acoburn
 */
public class Version {

    private static final Logger LOGGER = getLogger(Version.class);

    private final Instant time;

    /**
     * Create a Version parameter
     * @param version the version timestamp
     */
    public Version(final String version) {
        time = parse(version).orElseThrow(() ->
                new WebApplicationException(NOT_FOUND));
    }

    /**
     * Retrieve the instant
     * @return the instant
     */
    public Instant getInstant() {
        return time;
    }

    @Override
    public String toString() {
        return time.toString();
    }

    private static Optional<Instant> parse(final String version) {
        if (nonNull(version)) {
            try {
                return of(ofEpochMilli(parseLong(version.trim())));
            } catch (final NumberFormatException ex) {
                LOGGER.error("Unable to parse version string '{}': {}", version, ex.getMessage());
            }
        }
        return empty();
    }
}
