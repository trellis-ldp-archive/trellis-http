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

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.rdf.api.RDFSyntax.RDFA_HTML;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.impl.RdfUtils.skolemizeTriples;
import static org.trellisldp.http.impl.RdfUtils.unskolemizeTriples;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.slf4j.Logger;

import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.spi.RDFUtils;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.SerializationService;
import org.trellisldp.vocabulary.JSONLD;
import org.trellisldp.vocabulary.PROV;
import org.trellisldp.vocabulary.RDF;
import org.trellisldp.vocabulary.Trellis;

/**
 * The PATCH response builder
 *
 * @author acoburn
 */
public class LdpPatchHandler extends BaseLdpHandler {

    private static final Logger LOGGER = getLogger(LdpPatchHandler.class);

    private final SerializationService serializationService;
    private final Request request;

    private String sparqlUpdate = null;

    /**
     * Create a handler for PATCH operations
     * @param resourceService the resource service
     * @param serializationService the serialization service
     * @param request the HTTP request
     */
    public LdpPatchHandler(final ResourceService resourceService, final SerializationService serializationService,
            final Request request) {
        super(resourceService);
        this.serializationService = serializationService;
        this.request = request;
    }

    /**
     * Set the sparql update body
     * @param sparqlUpdate the sparql update command
     */
    public void setSparqlUpdate(final String sparqlUpdate) {
        this.sparqlUpdate = sparqlUpdate;
    }

    /**
     * Update a resource with Sparql-Update and build an HTTP response
     * @param res the resource
     * @return the Response builder
     */
    public ResponseBuilder updateResource(final Resource res) {
        final String identifier = baseUrl + path;
        if (isNull(sparqlUpdate)) {
            throw new WebApplicationException("Missing Sparql-Update body", BAD_REQUEST);
        }
        if (isNull(session)) {
            throw new WebApplicationException("Missing Session", BAD_REQUEST);
        }

        // Check if this is already deleted
        final ResponseBuilder deleted = checkDeleted(res, identifier);
        if (nonNull(deleted)) {
            return deleted;
        }

        // Check the cache
        final EntityTag etag = new EntityTag(md5Hex(res.getModified() + identifier));
        final ResponseBuilder cache = checkCache(request, res.getModified(), etag);
        if (nonNull(cache)) {
            return cache;
        }

        LOGGER.debug("Updating {} via PATCH", identifier);

        final Graph graph = rdf.createGraph();
        res.stream(Trellis.PreferUserManaged).forEach(graph::add);
        try {
            serializationService.update(graph, sparqlUpdate, TRELLIS_PREFIX + path);
            // TODO change this to a more specific (RepositoryRuntime) Exception
        } catch (final RuntimeException ex) {
            LOGGER.warn(ex.getMessage());
            return status(BAD_REQUEST).type(TEXT_PLAIN).entity("Invalid RDF: " + ex.getMessage());
        }

        // TODO -- validate this w/ the constraint service
        // constraintService.constrainedBy(res.getInteractionModel(), graph);
        final IRI bnode = (IRI) resourceService.skolemize(rdf.createBlankNode());
        final Dataset dataset = rdf.createDataset();
        graph.stream().map(skolemizeTriples(resourceService, baseUrl)).map(t ->
                rdf.createQuad(Trellis.PreferUserManaged, t.getSubject(), t.getPredicate(), t.getObject()))
            .forEach(dataset::add);

        dataset.add(Trellis.PreferAudit, res.getIdentifier(), PROV.wasGeneratedBy, bnode);
        dataset.add(Trellis.PreferServerManaged, res.getIdentifier(), RDF.type, res.getInteractionModel());
        RDFUtils.auditUpdate(bnode, session).stream().forEach(dataset::add);

        // Save new dataset
        resourceService.put(res.getIdentifier(), dataset);

        final ResponseBuilder builder = ok();

        if (ofNullable(prefer).flatMap(Prefer::getPreference).filter("representation"::equals).isPresent()) {
            builder.entity(ResourceStreamer.tripleStreamer(serializationService,
                        graph.stream().map(unskolemizeTriples(resourceService, baseUrl)),
                        syntax, ofNullable(profile).orElseGet(() ->
                            RDFA_HTML.equals(syntax) ? rdf.createIRI(identifier) : JSONLD.expanded)));
        } else {
            builder.status(NO_CONTENT);
        }

        // Set no-cache directive
        final CacheControl cc = new CacheControl();
        cc.setNoCache(true);
        builder.cacheControl(cc);

        return builder;
    }

}
