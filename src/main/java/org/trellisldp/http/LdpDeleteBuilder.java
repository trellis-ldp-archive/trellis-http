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

import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.status;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.HttpConstants.TRELLIS_PREFIX;

import java.util.function.Function;

import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.slf4j.Logger;
import org.trellisldp.api.Resource;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.Trellis;

/**
 * The DELETE response builder
 *
 * @author acoburn
 */
class LdpDeleteBuilder extends LdpResponseBuilder {

    private static final Logger LOGGER = getLogger(LdpDeleteBuilder.class);

    /**
     * Create a builder for an LDP DELETE response
     * @param resourceService the resource service
     */
    protected LdpDeleteBuilder(final ResourceService resourceService) {
        super(resourceService);
    }

    /**
     * Create a builder for an LDP DELETE response
     * @param resourceService the resource service
     * @return the response builder
     */
    public static LdpDeleteBuilder builder(final ResourceService resourceService) {
        return new LdpDeleteBuilder(resourceService);
    }

    @Override
    public Response build(final String path) {
        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path))
            .map(deleteResource(path)).orElse(status(NOT_FOUND)).build();
    }

    private Function<Resource, ResponseBuilder> deleteResource(final String path) {
        return res -> {
            final String identifier = baseUrl + path;
            if (res.getTypes().anyMatch(Trellis.DeletedResource::equals)) {
                return status(GONE).links(MementoResource.getMementoLinks(identifier, res.getMementos())
                        .toArray(Link[]::new));
            }

            LOGGER.debug("Deleting {}", identifier);

            final ResponseBuilder builder = status(NO_CONTENT);

            return builder;
        };
    }
}
