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

import static java.util.Objects.nonNull;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.HttpUtils.checkCache;
import static org.trellisldp.spi.RDFUtils.auditDeletion;
import static org.trellisldp.spi.RDFUtils.getInstance;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.slf4j.Logger;
import org.trellisldp.api.Resource;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.Session;
import org.trellisldp.vocabulary.PROV;
import org.trellisldp.vocabulary.Trellis;

/**
 * The DELETE response builder
 *
 * @author acoburn
 */
class LdpDeleteHandler {

    private static final RDF rdf = getInstance();
    private static final Logger LOGGER = getLogger(LdpDeleteHandler.class);

    private final ResourceService resourceService;
    private final Request request;
    private final LdpRequest ldpRequest;

    /**
     * Create a builder for an LDP DELETE response
     * @param resourceService the resource service
     * @param request the request
     * @param ldpRequest the LDP request
     */
    public LdpDeleteHandler(final ResourceService resourceService, final Request request, final LdpRequest ldpRequest) {
        this.resourceService = resourceService;
        this.request = request;
        this.ldpRequest = ldpRequest;
    }

    /**
     * Delete the given resource
     * @param res the resource
     * @return a response builder
     */
    public ResponseBuilder deleteResource(final Resource res) {
        final String identifier = ldpRequest.getBaseUrl() + ldpRequest.getPath();
        final Session session = ldpRequest.getSession().orElseThrow(() ->
                new WebApplicationException("Missing Session", BAD_REQUEST));

        if (res.getTypes().anyMatch(Trellis.DeletedResource::equals)) {
            return status(GONE).links(MementoResource.getMementoLinks(identifier, res.getMementos())
                    .toArray(Link[]::new));
        }

        final EntityTag etag = new EntityTag(md5Hex(res.getModified() + identifier));
        final ResponseBuilder cache = checkCache(request, res.getModified(), etag);
        if (nonNull(cache)) {
            return cache;
        }

        LOGGER.debug("Deleting {}", identifier);

        final IRI bnode = (IRI) resourceService.skolemize(rdf.createBlankNode());
        final Dataset dataset = auditDeletion(bnode, session);
        dataset.add(rdf.createQuad(Trellis.PreferAudit, res.getIdentifier(), PROV.wasGeneratedBy, bnode));

        // delete the resource
        resourceService.put(res.getIdentifier(), dataset);

        final ResponseBuilder builder = status(NO_CONTENT);

        return builder;
    }
}
