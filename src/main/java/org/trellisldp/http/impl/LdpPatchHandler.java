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
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.serverError;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.rdf.api.RDFSyntax.RDFA_HTML;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.ACL;
import static org.trellisldp.http.domain.HttpConstants.PREFERENCE_APPLIED;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.impl.RdfUtils.getProfile;
import static org.trellisldp.http.impl.RdfUtils.getSyntax;
import static org.trellisldp.http.impl.RdfUtils.skolemizeQuads;
import static org.trellisldp.http.impl.RdfUtils.skolemizeTriples;
import static org.trellisldp.http.impl.RdfUtils.unskolemizeTriples;
import static org.trellisldp.spi.ConstraintService.ldpResourceTypes;
import static org.trellisldp.spi.RDFUtils.auditUpdate;

import java.util.Map;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.slf4j.Logger;

import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.RuntimeRepositoryException;
import org.trellisldp.spi.Session;
import org.trellisldp.vocabulary.JSONLD;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.RDF;
import org.trellisldp.vocabulary.Trellis;

/**
 * The PATCH response builder
 *
 * @author acoburn
 */
public class LdpPatchHandler extends BaseLdpHandler {

    private static final Logger LOGGER = getLogger(LdpPatchHandler.class);

    private final IOService ioService;
    private final ConstraintService constraintService;
    private final String sparqlUpdate;

    /**
     * Create a handler for PATCH operations
     * @param partitions the partitions
     * @param req the LDP request
     * @param sparqlUpdate the sparql update body
     * @param resourceService the resource service
     * @param ioService the serialization service
     * @param constraintService the RDF constraint service
     */
    public LdpPatchHandler(final Map<String, String> partitions, final LdpRequest req,
            final String sparqlUpdate,
            final ResourceService resourceService, final IOService ioService,
            final ConstraintService constraintService) {
        super(partitions, req, resourceService);
        this.ioService = ioService;
        this.constraintService = constraintService;
        this.sparqlUpdate = sparqlUpdate;
    }

    /**
     * Update a resource with Sparql-Update and build an HTTP response
     * @param res the resource
     * @return the Response builder
     */
    public ResponseBuilder updateResource(final Resource res) {
        final String baseUrl = req.getBaseUrl(partitions);
        final String identifier = baseUrl + req.getPartition() + req.getPath();

        if (isNull(sparqlUpdate)) {
            throw new WebApplicationException("Missing Sparql-Update body", BAD_REQUEST);
        }
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

        LOGGER.debug("Updating {} via PATCH", identifier);

        // Update existing graph
        final Graph graph = rdf.createGraph();
        final IRI graphName = ACL.equals(req.getExt()) ? Trellis.PreferAccessControl : Trellis.PreferUserManaged;
        res.stream(graphName).forEach(graph::add);
        try {
            ioService.update(graph, sparqlUpdate, TRELLIS_PREFIX + req.getPartition() + req.getPath());
        } catch (final RuntimeRepositoryException ex) {
            LOGGER.warn(ex.getMessage());
            return status(BAD_REQUEST).type(TEXT_PLAIN).entity("Invalid RDF: " + ex.getMessage());
        }

        final Dataset dataset = rdf.createDataset();
        graph.stream().map(skolemizeTriples(resourceService, baseUrl))
            .map(t -> rdf.createQuad(graphName, t.getSubject(), t.getPredicate(), t.getObject()))
            .forEach(dataset::add);

        // Add audit-related triples
        auditUpdate(res.getIdentifier(), session).stream().map(skolemizeQuads(resourceService, baseUrl))
            .forEach(dataset::add);

        // Add existing LDP type
        dataset.add(rdf.createQuad(Trellis.PreferServerManaged, res.getIdentifier(), RDF.type,
                    res.getInteractionModel()));

        // Check any constraints
        final Optional<String> constraint = dataset.getGraph(graphName)
            .flatMap(g -> constraintService.constrainedBy(res.getInteractionModel(), baseUrl, g))
            .map(IRI::getIRIString);
        if (constraint.isPresent()) {
            return status(BAD_REQUEST).link(constraint.get(), LDP.constrainedBy.getIRIString());
        }

        // Save new dataset
        if (resourceService.put(res.getIdentifier(), dataset)) {

            final ResponseBuilder builder = ok();

            ldpResourceTypes(res.getInteractionModel()).map(IRI::getIRIString)
                .forEach(type -> builder.link(type, "type"));

            if (ofNullable(req.getPrefer()).flatMap(Prefer::getPreference).filter("representation"::equals)
                    .isPresent()) {
                final RDFSyntax syntax = getSyntax(req.getHeaders().getAcceptableMediaTypes(), empty()).get();
                final IRI profile = getProfile(req.getHeaders().getAcceptableMediaTypes(), syntax);
                builder.header(PREFERENCE_APPLIED, "return=representation")
                       .type(syntax.mediaType)
                       .entity(ResourceStreamer.tripleStreamer(ioService,
                            graph.stream().map(unskolemizeTriples(resourceService, baseUrl)),
                            syntax, ofNullable(profile).orElseGet(() ->
                                RDFA_HTML.equals(syntax) ? rdf.createIRI(identifier) : JSONLD.expanded)));
            } else {
                return builder.status(NO_CONTENT);
            }

            return builder;
        }

        LOGGER.error("Unable to persist data to location at {}", res.getIdentifier().getIRIString());
        return serverError().type(TEXT_PLAIN)
            .entity("Unable to persist data. Please consult the logs for more information");
    }
}
