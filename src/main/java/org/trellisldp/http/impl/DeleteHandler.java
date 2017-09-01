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
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.serverError;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.ACL;
import static org.trellisldp.http.impl.RdfUtils.skolemizeQuads;
import static org.trellisldp.spi.RDFUtils.auditDeletion;
import static org.trellisldp.spi.RDFUtils.ldpResourceTypes;
import static org.trellisldp.vocabulary.LDP.Container;
import static org.trellisldp.vocabulary.LDP.PreferContainment;
import static org.trellisldp.vocabulary.LDP.constrainedBy;
import static org.trellisldp.vocabulary.Trellis.PreferUserManaged;
import static org.trellisldp.vocabulary.Trellis.UnsupportedRecursiveDelete;

import java.util.Map;
import java.util.stream.Stream;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.Triple;
import org.slf4j.Logger;
import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.Session;

/**
 * The DELETE response builder
 *
 * @author acoburn
 */
public class DeleteHandler extends BaseLdpHandler {

    private static final Logger LOGGER = getLogger(DeleteHandler.class);

    /**
     * Create a builder for an LDP DELETE response
     * @param partitions the partitions
     * @param req the LDP request
     * @param resourceService the resource service
     */
    public DeleteHandler(final Map<String, String> partitions, final LdpRequest req,
            final ResourceService resourceService) {
        super(partitions, req, resourceService);
    }

    /**
     * Delete the given resource
     * @param res the resource
     * @return a response builder
     */
    public ResponseBuilder deleteResource(final Resource res) {
        final String baseUrl = req.getBaseUrl(partitions);
        final String identifier = baseUrl + req.getPartition() + req.getPath();

        final Session session = ofNullable(req.getSession()).orElseGet(HttpSession::new);

        // Check if this is already deleted
        checkDeleted(res, identifier);

        // Check the cache
        final EntityTag etag = new EntityTag(md5Hex(res.getModified() + identifier));
        checkCache(req.getRequest(), res.getModified(), etag);

        // If this is a container, are there contained resources?
        if (ldpResourceTypes(res.getInteractionModel()).anyMatch(Container::equals)) {
            try (final Stream<Triple> contains = res.stream(PreferContainment)) {
                if (contains.findAny().isPresent()) {
                    return status(CONFLICT).link(UnsupportedRecursiveDelete.getIRIString(),
                            constrainedBy.getIRIString());
                }
            }
        }

        LOGGER.debug("Deleting {}", identifier);

        try (final TrellisDataset dataset = TrellisDataset.createDataset()) {

            // Add the audit quads
            auditDeletion(res.getIdentifier(), session).stream().map(skolemizeQuads(resourceService, baseUrl))
                .forEach(dataset::add);

            // When deleting just the ACL graph, keep the user managed triples in tact
            if (ACL.equals(req.getExt())) {
                try (final Stream<Triple> triples = res.stream(PreferUserManaged)) {
                    triples.map(t -> rdf.createQuad(PreferUserManaged, t.getSubject(), t.getPredicate(), t.getObject()))
                        .forEach(dataset::add);
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
