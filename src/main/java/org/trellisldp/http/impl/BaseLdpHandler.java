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
package org.trellisldp.http.impl;

import static java.util.Arrays.asList;
import static java.util.Date.from;
import static java.util.Objects.nonNull;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.rdf.api.RDFSyntax.JSONLD;
import static org.apache.commons.rdf.api.RDFSyntax.NTRIPLES;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.spi.RDFUtils.getInstance;
import static org.trellisldp.vocabulary.LDP.Resource;
import static org.trellisldp.vocabulary.Trellis.DeletedResource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFSyntax;
import org.slf4j.Logger;
import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.spi.ResourceService;

/**
 * @author acoburn
 */
public class BaseLdpHandler {

    private static final Logger LOGGER = getLogger(BaseLdpHandler.class);

    protected static final RDF rdf = getInstance();

    protected static final List<RDFSyntax> SUPPORTED_RDF_TYPES = asList(TURTLE, JSONLD, NTRIPLES);

    protected final Map<String, String> partitions;
    protected final LdpRequest req;
    protected final ResourceService resourceService;

    /**
     * A base class for response handling
     * @param partitions the partitions
     * @param req the LDP request
     * @param resourceService the resource service
     */
    public BaseLdpHandler(final Map<String, String> partitions, final LdpRequest req,
            final ResourceService resourceService) {
        this.partitions = partitions;
        this.req = req;
        this.resourceService = resourceService;
    }

    /**
     * Check if this is a deleted resource, and if so return an appropriate response
     * @param res the resource
     * @param identifier the identifier
     * @throws WebApplicationException a 410 Gone exception
     */
    protected static void checkDeleted(final Resource res, final String identifier) {
       if (Resource.equals(res.getInteractionModel()) && res.getTypes().contains(DeletedResource)) {
            throw new WebApplicationException(status(GONE)
                    .links(MementoResource.getMementoLinks(identifier, res.getMementos())
                    .toArray(Link[]::new)).build());
        }
    }

    /**
     * Check the request for a cache-related response
     * @param request the request
     * @param modified the modified time
     * @param etag the etag
     * @throws WebApplicationException either a 412 Precondition Failed or a 304 Not Modified, depending on the context.
     */
    protected static void checkCache(final Request request, final Instant modified, final EntityTag etag) {
        try {
            final ResponseBuilder builder = request.evaluatePreconditions(from(modified), etag);
            if (nonNull(builder)) {
                throw new WebApplicationException(builder.build());
            }
        } catch (final IllegalArgumentException ex) {
            LOGGER.warn("Ignoring cache-related headers: {}", ex.getMessage());
        }
    }
}
