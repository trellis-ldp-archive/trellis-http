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
package org.trellisldp.http;

import static java.util.Date.from;
import static org.slf4j.LoggerFactory.getLogger;

import java.time.Instant;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.slf4j.Logger;

/**
 * HTTP Utility functions
 *
 * @author acoburn
 */
final class HttpUtils {

    private static final Logger LOGGER = getLogger(HttpUtils.class);

    /**
     * Check the request for a cache-related response
     * @param request the request
     * @param modified the modified time
     * @param etag the etag
     * @return the ResponseBuilder, which will be null if there is not a cache-hit
     */
    public static ResponseBuilder checkCache(final Request request, final Instant modified, final EntityTag etag) {
        try {
            return request.evaluatePreconditions(from(modified), etag);
        } catch (final Exception ex) {
            LOGGER.warn("Ignoring cache-related headers: {}", ex.getMessage());
        }
        return null;
    }

    private HttpUtils() {
        // prevent instantiation
    }
}
