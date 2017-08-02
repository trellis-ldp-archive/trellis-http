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

import static java.net.URI.create;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.serverError;
import static javax.ws.rs.core.Response.status;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.impl.RdfUtils.skolemizeQuads;
import static org.trellisldp.http.impl.RdfUtils.skolemizeTriples;
import static org.trellisldp.spi.ConstraintService.ldpResourceTypes;
import static org.trellisldp.spi.RDFUtils.auditCreation;

import java.net.URI;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;

import org.slf4j.Logger;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.RDF;
import org.trellisldp.vocabulary.Trellis;

/**
 * The POST response handler
 *
 * @author acoburn
 */
public class LdpPostHandler extends BaseLdpHandler {

    private static final Logger LOGGER = getLogger(LdpPostHandler.class);

    private final BinaryService binaryService;
    private final ConstraintService constraintService;
    private final IOService ioService;

    /**
     * Create a builder for an LDP POST response
     * @param resourceService the resource service
     * @param ioService the serialization service
     * @param constraintService the RDF constraint service
     * @param binaryService the datastream service
     */
    public LdpPostHandler(final ResourceService resourceService, final IOService ioService,
            final ConstraintService constraintService, final BinaryService binaryService) {
        super(resourceService);
        this.ioService = ioService;
        this.binaryService = binaryService;
        this.constraintService = constraintService;
    }

    /**
     * Create a new resource
     * @return the response builder
     */
    public ResponseBuilder createResource() {
        final String identifier = baseUrl + path;
        LOGGER.info("Creating resource as {}", identifier);

        if (isNull(session)) {
            throw new WebApplicationException("Missing Session", BAD_REQUEST);
        }

        final Optional<RDFSyntax> rdfSyntax = ofNullable(contentType).flatMap(RDFSyntax::byMediaType)
            .filter(SUPPORTED_RDF_TYPES::contains);

        final IRI defaultType = nonNull(contentType) && !rdfSyntax.isPresent() ? LDP.NonRDFSource : LDP.RDFSource;
        final IRI iri = rdf.createIRI(TRELLIS_PREFIX + path);

        final Dataset dataset = rdf.createDataset();

        // Add Audit quads
        auditCreation(iri, session).stream().map(skolemizeQuads(resourceService, baseUrl)).forEach(dataset::add);

        // Add LDP type
        final IRI ldpType = ofNullable(link).filter(l -> "type".equals(l.getRel()))
                    .map(Link::getUri).map(URI::toString).map(rdf::createIRI).orElse(defaultType);

        dataset.add(rdf.createQuad(Trellis.PreferServerManaged, iri, RDF.type, ldpType));

        // Add user-supplied data
        if (nonNull(entity) && rdfSyntax.isPresent()) {
            ioService.read(entity, identifier, rdfSyntax.get())
                .map(skolemizeTriples(resourceService, baseUrl)).forEach(triple -> {
                    dataset.add(rdf.createQuad(Trellis.PreferUserManaged, triple.getSubject(),
                            triple.getPredicate(), triple.getObject()));
                });
            final Optional<String> constraint = dataset.getGraph(Trellis.PreferUserManaged)
                .flatMap(g -> constraintService.constrainedBy(ldpType, baseUrl, g)).map(IRI::getIRIString);
            if (constraint.isPresent()) {
                return status(BAD_REQUEST).link(constraint.get(), LDP.constrainedBy.getIRIString());
            }
        } else if (nonNull(entity)) {
            final String partition = getPartition(path);
            final IRI binaryLocation = rdf.createIRI(binaryService.getIdentifierSupplier(partition).get());
            binaryService.setContent(partition, binaryLocation, entity);
            dataset.add(rdf.createQuad(Trellis.PreferServerManaged, iri, DC.hasPart, binaryLocation));
            dataset.add(rdf.createQuad(Trellis.PreferServerManaged, binaryLocation, DC.format,
                        rdf.createLiteral(ofNullable(contentType).orElse(APPLICATION_OCTET_STREAM))));
        }

        if (resourceService.put(iri, dataset)) {
            final ResponseBuilder builder = status(CREATED).location(create(identifier));

            // Add LDP types
            ldpResourceTypes(ldpType).map(IRI::getIRIString)
                .forEach(type -> builder.link(type, "type"));

            return builder;
        } else {
            LOGGER.error("Unable to persist data to location at {}", iri.getIRIString());
            return serverError().type(TEXT_PLAIN)
                .entity("Unable to persist data. Please consult the logs for more information");
        }
    }
}
