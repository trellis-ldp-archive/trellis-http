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

import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.serverError;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.impl.RdfUtils.skolemizeQuads;
import static org.trellisldp.spi.RDFUtils.auditDeletion;

import java.util.Map;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.Dataset;
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

        final Session session = ofNullable(req.getSession()).orElse(new HttpSession());

        // Check if this is already deleted
        final ResponseBuilder deleted = checkDeleted(res, identifier);
        if (nonNull(deleted)) {
            return deleted;
        }

        // Check the cache
        final EntityTag etag = new EntityTag(md5Hex(res.getModified() + identifier));
        final ResponseBuilder cache = checkCache(req.getRequest(), res.getModified(), etag);
        if (nonNull(cache)) {
            return cache;
        }

        LOGGER.debug("Deleting {}", identifier);

        try (final Dataset dataset = rdf.createDataset()) {
            // Add the audit quads
            auditDeletion(res.getIdentifier(), session).stream().map(skolemizeQuads(resourceService, baseUrl))
                .forEach(dataset::add);

            // delete the resource
            if (resourceService.put(res.getIdentifier(), dataset)) {
                return status(NO_CONTENT);
            }
        } catch (final Exception ex) {
            LOGGER.error("Error handling dataset: {}", ex.getMessage());
        }

        LOGGER.error("Unable to delete resource at {}", res.getIdentifier());
        return serverError().entity("Unable to delete resource. Please consult the logs for more information");
    }
}
