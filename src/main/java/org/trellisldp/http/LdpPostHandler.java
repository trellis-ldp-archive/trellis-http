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

import static org.slf4j.LoggerFactory.getLogger;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.trellisldp.spi.DatastreamService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.SerializationService;

/**
 * The POST response handler
 *
 * @author acoburn
 */
class LdpPostHandler extends LdpResponseHandler {

    private static final Logger LOGGER = getLogger(LdpPostHandler.class);

    private final DatastreamService datastreamService;
    private final SerializationService serializationService;

    protected LdpPostHandler(final ResourceService resourceService,
            final SerializationService serializationService, final DatastreamService datastreamService) {
        super(resourceService);
        this.serializationService = serializationService;
        this.datastreamService = datastreamService;
    }

    /**
     * Create a builder for an LDP POST response
     * @param resourceService the resource service
     * @param serializationService the serialization service
     * @param datastreamService the datastream service
     * @return the response builder
     */
    public static LdpPostHandler builder(final ResourceService resourceService,
            final SerializationService serializationService,
            final DatastreamService datastreamService) {
        return new LdpPostHandler(resourceService, serializationService, datastreamService);
    }

    @Override
    public Response build(final String path) {
        return Response.ok().build();
    }

}
