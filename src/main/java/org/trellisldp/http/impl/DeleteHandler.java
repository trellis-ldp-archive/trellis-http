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

import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.serverError;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.ACL;
import static org.trellisldp.http.impl.RdfUtils.skolemizeQuads;
import static org.trellisldp.vocabulary.Trellis.PreferUserManaged;

import java.util.stream.Stream;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.Triple;
import org.slf4j.Logger;
import org.trellisldp.api.Resource;
import org.trellisldp.api.ResourceService;
import org.trellisldp.api.Session;
import org.trellisldp.http.domain.LdpRequest;

/**
 * The DELETE response builder
 *
 * @author acoburn
 */
public class DeleteHandler extends BaseLdpHandler {

    private static final Logger LOGGER = getLogger(DeleteHandler.class);

    /**
     * Create a builder for an LDP DELETE response
     * @param baseUrl the base URL
     * @param req the LDP request
     * @param resourceService the resource service
     */
    public DeleteHandler(final String baseUrl, final LdpRequest req, final ResourceService resourceService) {
        super(baseUrl, req, resourceService);
    }

    /**
     * Delete the given resource
     * @param res the resource
     * @return a response builder
     */
    public ResponseBuilder deleteResource(final Resource res) {
        final String baseUrl = getBaseUrl();
        final String identifier = baseUrl + req.getPartition() + req.getPath();

        final Session session = ofNullable(req.getSession()).orElseGet(HttpSession::new);

        // Check if this is already deleted
        checkDeleted(res, identifier);

        // Check the cache
        final EntityTag etag = new EntityTag(md5Hex(res.getModified() + identifier));
        checkCache(req.getRequest(), res.getModified(), etag);

        LOGGER.debug("Deleting {}", identifier);

        try (final TrellisDataset dataset = TrellisDataset.createDataset()) {

            // Add the audit quads
            audit.ifPresent(svc -> svc.deletion(res.getIdentifier(), session).stream()
                    .map(skolemizeQuads(resourceService, baseUrl)).forEachOrdered(dataset::add));

            // When deleting just the ACL graph, keep the user managed triples in tact
            if (ACL.equals(req.getExt())) {
                try (final Stream<? extends Triple> triples = res.stream(PreferUserManaged)) {
                    triples.map(t -> rdf.createQuad(PreferUserManaged, t.getSubject(), t.getPredicate(), t.getObject()))
                        .forEachOrdered(dataset::add);
                }
            }

            // delete the resource
            if (resourceService.put(res.getIdentifier(), dataset.asDataset())) {
                return status(NO_CONTENT);
            }
        }

        LOGGER.error("Unable to delete resource at {}", res.getIdentifier());
        return serverError().entity("Unable to delete resource. Please consult the logs for more information");
    }
}
