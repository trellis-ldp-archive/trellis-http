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

import static java.util.Collections.singletonMap;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static javax.ws.rs.core.Response.serverError;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.ACL;
import static org.trellisldp.http.impl.RdfUtils.skolemizeQuads;
import static org.trellisldp.spi.RDFUtils.TRELLIS_PREFIX;
import static org.trellisldp.spi.RDFUtils.auditUpdate;
import static org.trellisldp.spi.RDFUtils.ldpResourceTypes;
import static org.trellisldp.vocabulary.Trellis.PreferAccessControl;
import static org.trellisldp.vocabulary.Trellis.PreferServerManaged;
import static org.trellisldp.vocabulary.Trellis.PreferUserManaged;

import java.io.File;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.apache.commons.rdf.api.Triple;

import org.slf4j.Logger;
import org.trellisldp.api.Binary;
import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.Digest;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.Session;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.RDF;
import org.trellisldp.vocabulary.XSD;

/**
 * The PUT response handler
 *
 * @author acoburn
 */
public class PutHandler extends ContentBearingHandler {

    private static final Logger LOGGER = getLogger(PutHandler.class);

    /**
     * Create a builder for an LDP POST response
     * @param partitions the partitions
     * @param req the LDP request
     * @param entity the entity
     * @param resourceService the resource service
     * @param ioService the serialization service
     * @param constraintService the RDF constraint service
     * @param binaryService the binary service
     */
    public PutHandler(final Map<String, String> partitions, final LdpRequest req, final File entity,
            final ResourceService resourceService, final IOService ioService,
            final ConstraintService constraintService, final BinaryService binaryService) {
        super(partitions, req, entity, resourceService, ioService, constraintService, binaryService);
    }

    private void checkResourceCache(final String identifier, final Resource res) {
        final EntityTag etag;
        final Instant modified;
        final Optional<Instant> binaryModification = res.getBinary().map(Binary::getModified);

        if (binaryModification.isPresent() &&
                !ofNullable(req.getContentType()).flatMap(RDFSyntax::byMediaType).isPresent()) {
            modified = binaryModification.get();
            etag = new EntityTag(md5Hex(modified + identifier));
        } else {
            modified = res.getModified();
            etag = new EntityTag(md5Hex(modified + identifier), true);
        }
        // Check the cache
        checkCache(req.getRequest(), modified, etag);
    }

    private IRI getActiveGraphName() {
        return ACL.equals(req.getExt()) ? PreferAccessControl : PreferUserManaged;
    }

    private IRI getInactiveGraphName() {
        return ACL.equals(req.getExt()) ? PreferUserManaged : PreferAccessControl;
    }

    private Boolean isAclAndNonRdfContent(final Optional<RDFSyntax> syntax) {
        return ACL.equals(req.getExt()) && !syntax.isPresent();
    }

    /**
     * Set the data for a resource
     * @param res the resource
     * @return the response builder
     */
    public ResponseBuilder setResource(final Resource res) {
        final String baseUrl = req.getBaseUrl(partitions);
        final String identifier = baseUrl + req.getPartition() + req.getPath() +
            (ACL.equals(req.getExt()) ? "?ext=acl" : "");

        // Check if this is already deleted
        checkDeleted(res, identifier);

        // Check the cache
        checkResourceCache(identifier, res);

        final Session session = ofNullable(req.getSession()).orElseGet(HttpSession::new);
        final Optional<RDFSyntax> rdfSyntax = ofNullable(req.getContentType()).flatMap(RDFSyntax::byMediaType)
            .filter(SUPPORTED_RDF_TYPES::contains);

        // One cannot put binaries into the ACL graph
        if (isAclAndNonRdfContent(rdfSyntax)) {
            return status(NOT_ACCEPTABLE);
        }

        LOGGER.info("Setting resource as {}", identifier);

        final IRI ldpType = ofNullable(req.getLink()).filter(l -> "type".equals(l.getRel()))
                    .map(Link::getUri).map(URI::toString).filter(l -> l.startsWith(LDP.URI)).map(rdf::createIRI)
                    .filter(l -> !LDP.Resource.equals(l)).orElseGet(res::getInteractionModel);

        // It is not possible to change the LDP type to a type that is not a subclass
        if (!ldpResourceTypes(ldpType).anyMatch(res.getInteractionModel()::equals)) {
            return status(CONFLICT).entity("Cannot change the LDP type to " + ldpType).type(TEXT_PLAIN);
        }

        if (ldpType.equals(LDP.NonRDFSource) && rdfSyntax.isPresent()) {
            LOGGER.warn("Cannot save a NonRDFSource with RDF syntax");
            return status(BAD_REQUEST).entity("Cannot save a NonRDFSource with RDF syntax").type(TEXT_PLAIN);
        }

        final IRI internalId = rdf.createIRI(TRELLIS_PREFIX + req.getPartition() + req.getPath());

        try (final TrellisDataset dataset = TrellisDataset.createDataset()) {
            final IRI graphName = getActiveGraphName();
            final IRI otherGraph = getInactiveGraphName();

            // Add audit quads
            auditUpdate(internalId, session).stream().map(skolemizeQuads(resourceService, baseUrl))
                .forEach(dataset::add);

            // Add LDP type
            dataset.add(rdf.createQuad(PreferServerManaged, internalId, RDF.type, ldpType));

            // Add user-supplied data
            if (nonNull(entity) && rdfSyntax.isPresent()) {
                readEntityIntoDataset(identifier, baseUrl, graphName, rdfSyntax.get(), dataset);

                // Check for any constraints
                checkConstraint(dataset, PreferUserManaged, ldpType, baseUrl, rdfSyntax.get());

            } else if (nonNull(entity)) {
                // Check the expected digest value
                final Digest digest = req.getDigest();
                if (nonNull(digest) && !getDigestForEntity(digest).equals(digest.getDigest())) {
                    return status(BAD_REQUEST);
                }

                final Map<String, String> metadata = singletonMap(CONTENT_TYPE, ofNullable(req.getContentType())
                        .orElse(APPLICATION_OCTET_STREAM));
                final IRI binaryLocation = rdf.createIRI(binaryService.getIdentifierSupplier(req.getPartition()).get());

                // Persist the content
                persistContent(binaryLocation, metadata);

                dataset.add(rdf.createQuad(PreferServerManaged, internalId, DC.hasPart, binaryLocation));
                dataset.add(rdf.createQuad(PreferServerManaged, binaryLocation, DC.format,
                            rdf.createLiteral(ofNullable(req.getContentType()).orElse(APPLICATION_OCTET_STREAM))));
                dataset.add(rdf.createQuad(PreferServerManaged, binaryLocation, DC.extent,
                            rdf.createLiteral(Long.toString(entity.length()), XSD.long_)));
            }

            try (final Stream<? extends Triple> remaining = res.stream(otherGraph)) {
                remaining.map(t -> rdf.createQuad(otherGraph, t.getSubject(), t.getPredicate(), t.getObject()))
                    .forEach(dataset::add);
            }

            if (resourceService.put(internalId, dataset.asDataset())) {
                final ResponseBuilder builder = status(NO_CONTENT);

                ldpResourceTypes(ldpType).map(IRI::getIRIString).forEach(type -> builder.link(type, "type"));
                return builder;
            }
        }

        LOGGER.error("Unable to persist data to location at {}", internalId.getIRIString());
        return serverError().type(TEXT_PLAIN)
            .entity("Unable to persist data. Please consult the logs for more information");
    }
}
