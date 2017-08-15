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

import static java.net.URI.create;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.ok;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.impl.RdfUtils.skolemizeQuads;
import static org.trellisldp.spi.RDFUtils.auditCreation;
import static org.trellisldp.spi.RDFUtils.getInstance;
import static org.trellisldp.vocabulary.RDF.type;

import com.codahale.metrics.annotation.Timed;

import java.io.InputStream;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;
import org.trellisldp.vocabulary.XSD;

/**
 * @author acoburn
 */
@Path("upload/{partition}/{id}")
public class MultipartUploader {

    private final BinaryService binaryService;

    private final ResourceService resourceService;

    private static final RDF rdf = getInstance();

    /**
     * Create a multipart uploader object
     * @param resourceService the resource service
     * @param binaryService the binary service
     */
    public MultipartUploader(final ResourceService resourceService, final BinaryService binaryService) {
        this.resourceService = resourceService;
        this.binaryService = binaryService;
    }

    /**
     * Get a list of the uploads
     * @param partition the partition
     * @param id the upload id
     * @return a response
     */
    @GET
    @Timed
    @Produces("application/json")
    public Response listUploads(@PathParam("partition") final String partition,
            @PathParam("id") final String id) {
        return binaryService.getResolverForPartition(partition)
            .filter(BinaryService.Resolver::supportsMultipartUpload)
            .filter(res -> res.uploadSessionExists(id))
            .map(res -> res.listParts(id).collect(toList()))
            // TODO make this list into an actual JSON response
            .map(parts -> ok().entity(parts.toString()).build())
            .orElseThrow(NotFoundException::new);
    }

    /**
     * Create a binary from a collection of uploaded parts
     * @param partition the partition
     * @param id the identifier
     * @param input the input value
     * @return a response
     */
    @POST
    @Timed
    @Consumes("application/json")
    public Response createBinary(@PathParam("partition") final String partition,
            @PathParam("id") final String id, final InputStream input) {

        // TODO -- read input into this map
        final Map<Integer, String> partDigests = emptyMap();
        return binaryService.getResolverForPartition(partition)
            .filter(BinaryService.Resolver::supportsMultipartUpload)
            .filter(svc -> svc.uploadSessionExists(id))
            .map(svc -> svc.completeUpload(id, partDigests))
            .map(upload -> {
                final Dataset dataset = rdf.createDataset();
                final IRI identifier = rdf.createIRI(TRELLIS_PREFIX + upload.path);

                // Add Audit quads
                auditCreation(identifier, upload.session).stream().map(skolemizeQuads(resourceService, upload.baseUrl))
                    .forEach(dataset::add);
                dataset.add(rdf.createQuad(Trellis.PreferServerManaged, identifier, type, LDP.NonRDFSource));
                dataset.add(rdf.createQuad(Trellis.PreferServerManaged, identifier, DC.hasPart,
                            upload.binary.getIdentifier()));
                dataset.add(rdf.createQuad(Trellis.PreferServerManaged, upload.binary.getIdentifier(), DC.format,
                            rdf.createLiteral(upload.binary.getMimeType().orElse(APPLICATION_OCTET_STREAM))));
                upload.binary.getSize().ifPresent(size -> dataset.add(rdf.createQuad(Trellis.PreferServerManaged,
                                upload.binary.getIdentifier(), DC.extent, rdf.createLiteral(size.toString(),
                                    XSD.long_))));

                resourceService.put(identifier, dataset);
                return created(create(upload.baseUrl + upload.path)).build();
            })
            .orElseThrow(NotFoundException::new);
    }

    /**
     *  Add a segment of a binary
     *  @param partition the partition
     *  @param id the upload session identifier
     *  @param partNumber the part number
     *  @param part the input stream
     *  @return a response
     */
    @PUT
    @Timed
    @Path("{partNumber}")
    @Produces("application/json")
    public String uploadPart(@PathParam("partition") final String partition,
            @PathParam("id") final String id,
            @PathParam("partNumber") final Integer partNumber,
            final InputStream part) {

        return binaryService.getResolverForPartition(partition)
            .filter(BinaryService.Resolver::supportsMultipartUpload)
            .filter(res -> res.uploadSessionExists(id))
            .map(res -> res.uploadPart(id, partNumber, part))
            .orElseThrow(NotFoundException::new);
    }

    /**
     * Abort an upload process
     * @param partition the partition
     * @param id the upload identifier
     */
    @DELETE
    @Timed
    public void abortUpload(@PathParam("partition") final String partition,
            @PathParam("id") final String id) {

        binaryService.getResolverForPartition(partition)
            .filter(BinaryService.Resolver::supportsMultipartUpload)
            .filter(res -> res.uploadSessionExists(id))
            .orElseThrow(NotFoundException::new)
            .abortUpload(id);
    }
}
