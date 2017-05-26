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

import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.status;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.InputStream;

import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.slf4j.Logger;
import org.trellisldp.spi.DatastreamService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.SerializationService;

/**
 * The POST response handler
 *
 * @author acoburn
 */
class LdpPostHandler {

    private static final Logger LOGGER = getLogger(LdpPostHandler.class);

    private final DatastreamService datastreamService;
    private final ResourceService resourceService;
    private final SerializationService serializationService;
    private final Request request;
    private final LdpRequest ldpRequest;

    /**
     * Create a builder for an LDP POST response
     * @param resourceService the resource service
     * @param serializationService the serialization service
     * @param datastreamService the datastream service
     * @param request the request
     * @param ldpRequest the ldp request
     */
    protected LdpPostHandler(final ResourceService resourceService,
            final SerializationService serializationService, final DatastreamService datastreamService,
            final Request request, final LdpRequest ldpRequest) {
        this.resourceService = resourceService;
        this.serializationService = serializationService;
        this.datastreamService = datastreamService;
        this.request = request;
        this.ldpRequest = ldpRequest;
    }

    /**
     * Create a new resource
     * @param identifier the identifier
     * @param body the body
     * @return the response builder
     */
    public ResponseBuilder createResource(final String identifier, final InputStream body) {
        LOGGER.info("Creating resource as {}", identifier);
        return status(CREATED);
    }
}
