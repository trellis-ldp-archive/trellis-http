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

import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.apache.commons.codec.digest.DigestUtils.getDigest;
import static org.apache.commons.codec.digest.DigestUtils.updateDigest;
import static org.trellisldp.http.impl.RdfUtils.skolemizeTriples;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Map;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.WebApplicationException;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;

import org.trellisldp.http.domain.Digest;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.LDP;

/**
 * A common base class for PUT/POST requests
 *
 * @author acoburn
 */
class ContentBearingHandler extends BaseLdpHandler {

    protected final BinaryService binaryService;
    protected final ConstraintService constraintService;
    protected final IOService ioService;
    protected final File entity;

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
    protected ContentBearingHandler(final Map<String, String> partitions, final LdpRequest req, final File entity,
            final ResourceService resourceService, final IOService ioService,
            final ConstraintService constraintService, final BinaryService binaryService) {

        super(partitions, req, resourceService);
        this.constraintService = constraintService;
        this.binaryService = binaryService;
        this.ioService = ioService;
        this.entity = entity;
    }

    protected void readEntityIntoDataset(final String identifier, final String baseUrl, final IRI graphName,
            final RDFSyntax syntax, final TrellisDataset dataset) {
        try (final InputStream input = new FileInputStream(entity)) {
            ioService.read(input, identifier, syntax)
                .map(skolemizeTriples(resourceService, baseUrl))
                .map(triple -> rdf.createQuad(graphName, triple.getSubject(), triple.getPredicate(),
                            triple.getObject()))
                .forEach(dataset::add);
        } catch (final IOException ex) {
            throw new WebApplicationException("Error processing input", ex);
        }
    }

    protected void checkConstraint(final TrellisDataset dataset, final IRI graphName, final IRI type,
            final String baseUrl, final RDFSyntax syntax) {
        dataset.getGraph(graphName).flatMap(g -> constraintService.constrainedBy(type, baseUrl, g)).ifPresent(v -> {
            throw new WebApplicationException(status(CONFLICT)
                .entity(ResourceStreamer.tripleStreamer(ioService, v.getTriples().stream(), syntax))
                .link(v.getConstraint().getIRIString(), LDP.constrainedBy.getIRIString()).build());
        });
    }

    protected String getDigestForEntity(final Digest digest) {
        try (final InputStream input = new FileInputStream(entity)) {
            return encodeBase64String(updateDigest(getDigest(digest.getAlgorithm()), input).digest());
        } catch (final IllegalArgumentException ex) {
            throw new BadRequestException("Invalid algorithm provided for digest. " + digest.getAlgorithm() +
                    " is not supported: " + ex.getMessage());
        } catch (final IOException ex) {
            throw new WebApplicationException("Error computing checksum on input", ex);
        }
    }

    protected void persistContent(final IRI contentLocation, final Map<String, String> metadata) {
        try (final InputStream input = new FileInputStream(entity)) {
            binaryService.setContent(req.getPartition(), contentLocation, input, metadata);
        } catch (final IOException ex) {
            throw new WebApplicationException(ex);
        }
    }
}
