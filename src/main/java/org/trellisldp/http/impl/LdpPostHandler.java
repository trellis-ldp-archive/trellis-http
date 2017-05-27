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
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.status;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.impl.RdfUtils.skolemizeTriples;
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
import org.trellisldp.spi.DatastreamService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.SerializationService;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.PROV;
import org.trellisldp.vocabulary.RDF;
import org.trellisldp.vocabulary.Trellis;

/**
 * The POST response handler
 *
 * @author acoburn
 */
public class LdpPostHandler extends BaseLdpHandler {

    private static final Logger LOGGER = getLogger(LdpPostHandler.class);

    private final DatastreamService datastreamService;
    private final SerializationService serializationService;

    /**
     * Create a builder for an LDP POST response
     * @param resourceService the resource service
     * @param serializationService the serialization service
     * @param datastreamService the datastream service
     */
    public LdpPostHandler(final ResourceService resourceService,
            final SerializationService serializationService, final DatastreamService datastreamService) {
        super(resourceService);
        this.serializationService = serializationService;
        this.datastreamService = datastreamService;
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

        final IRI iri = rdf.createIRI(identifier);
        final IRI bnode = (IRI) resourceService.skolemize(rdf.createBlankNode());
        final Dataset dataset = auditCreation(bnode, session);
        dataset.add(rdf.createQuad(Trellis.PreferAudit, iri, PROV.wasGeneratedBy, bnode));
        dataset.add(rdf.createQuad(Trellis.PreferServerManaged, iri, RDF.type,
                    ofNullable(link).filter(l -> "type".equals(l.getRel()))
                    .map(Link::getUri).map(URI::toString).map(rdf::createIRI).orElse(defaultType)));

        if (nonNull(entity) && rdfSyntax.isPresent()) {
            serializationService.read(entity, identifier, rdfSyntax.get())
                .map(skolemizeTriples(resourceService, baseUrl)).forEach(triple -> {
                    dataset.add(rdf.createQuad(Trellis.PreferUserManaged, triple.getSubject(),
                            triple.getPredicate(), triple.getObject()));
                });
        } else {
            // TODO also handle binary data
        }

        resourceService.put(iri, dataset);

        return status(CREATED);
    }
}
