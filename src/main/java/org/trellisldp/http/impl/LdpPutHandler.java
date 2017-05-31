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
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.impl.RdfUtils.skolemizeQuads;
import static org.trellisldp.http.impl.RdfUtils.skolemizeTriples;
import static org.trellisldp.spi.RDFUtils.auditUpdate;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;

import org.slf4j.Logger;
import org.trellisldp.api.Blob;
import org.trellisldp.api.Resource;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.RDF;
import org.trellisldp.vocabulary.Trellis;

/**
 * The PUT response handler
 *
 * @author acoburn
 */
public class LdpPutHandler extends BaseLdpHandler {

    private static final Logger LOGGER = getLogger(LdpPutHandler.class);

    private final BinaryService binaryService;
    private final IOService ioService;
    private final Request request;

    /**
     * Create a builder for an LDP POST response
     * @param resourceService the resource service
     * @param ioService the serialization service
     * @param binaryService the binary service
     * @param request the request
     */
    public LdpPutHandler(final ResourceService resourceService, final IOService ioService,
            final BinaryService binaryService, final Request request) {
        super(resourceService);
        this.ioService = ioService;
        this.binaryService = binaryService;
        this.request = request;
    }

    /**
     * Set the data for a resource
     * @param res the resource
     * @return the response builder
     */
    public ResponseBuilder setResource(final Resource res) {
        final String identifier = baseUrl + path;
        final EntityTag etag;
        final Instant modified;

        if (res.getBlob().isPresent() &&
                !ofNullable(contentType).flatMap(RDFSyntax::byMediaType).isPresent()) {
            modified = res.getBlob().map(Blob::getModified).get();
            etag = new EntityTag(md5Hex(modified + identifier));
        } else {
            modified = res.getModified();
            etag = new EntityTag(md5Hex(modified + identifier), true);
        }

        // Check the cache
        final ResponseBuilder cache = checkCache(request, modified, etag);
        if (nonNull(cache)) {
            return cache;
        }
        return setResource();
    }

    /**
     * Set the data for a resource
     * @return the response builder
     */
    public ResponseBuilder setResource() {
        final String identifier = baseUrl + path;
        if (isNull(session)) {
            throw new WebApplicationException("Missing Session", BAD_REQUEST);
        }

        final Optional<RDFSyntax> rdfSyntax = ofNullable(contentType).flatMap(RDFSyntax::byMediaType)
            .filter(SUPPORTED_RDF_TYPES::contains);

        LOGGER.info("Setting resource as {}", identifier);

        final IRI defaultType = nonNull(contentType) && !rdfSyntax.isPresent() ? LDP.NonRDFSource : LDP.RDFSource;

        final IRI iri = rdf.createIRI(identifier);
        final Dataset dataset = rdf.createDataset();

        // Add audit quads
        auditUpdate(iri, session).stream().map(skolemizeQuads(resourceService, baseUrl)).forEach(dataset::add);

        // Add LDP type
        dataset.add(rdf.createQuad(Trellis.PreferServerManaged, iri, RDF.type,
                    ofNullable(link).filter(l -> "type".equals(l.getRel()))
                    .map(Link::getUri).map(URI::toString).map(rdf::createIRI).orElse(defaultType)));

        // Add user-supplied data
        if (nonNull(entity) && rdfSyntax.isPresent()) {
            ioService.read(entity, identifier, rdfSyntax.get())
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
