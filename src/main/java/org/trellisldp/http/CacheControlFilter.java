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

import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.Priorities.USER;
import static javax.ws.rs.core.HttpHeaders.CACHE_CONTROL;

import java.io.IOException;

import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.CacheControl;

/**
 * @author acoburn
 */
@Priority(USER)
public class CacheControlFilter implements ContainerResponseFilter {

    private final Integer cacheAge;

    /**
     * Create a new CacheControl Decorator
     * @param cacheAge the length of time to cache resources
     */
    public CacheControlFilter(final Integer cacheAge) {
        this.cacheAge = cacheAge;
    }

    @Override
    public void filter(final ContainerRequestContext req, final ContainerResponseContext res) throws IOException {

        if (req.getMethod().equals(GET)) {
            final CacheControl cc = new CacheControl();
            cc.setMaxAge(cacheAge);
            res.getHeaders().add(CACHE_CONTROL, cc);
        }
    }
}
