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

import static java.lang.String.join;
import static java.net.URI.create;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toMap;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.Priorities.AUTHORIZATION;
import static javax.ws.rs.core.HttpHeaders.ALLOW;
import static javax.ws.rs.core.Link.TYPE;
import static javax.ws.rs.core.Link.fromUri;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.serverError;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.UPLOAD_PREFIX;
import static org.trellisldp.http.domain.HttpConstants.UPLOADS;
import static org.trellisldp.http.impl.RdfUtils.skolemizeQuads;
import static org.trellisldp.spi.RDFUtils.TRELLIS_PREFIX;
import static org.trellisldp.spi.RDFUtils.auditCreation;
import static org.trellisldp.spi.RDFUtils.getInstance;
import static org.trellisldp.vocabulary.LDP.Container;
import static org.trellisldp.vocabulary.LDP.NonRDFSource;
import static org.trellisldp.vocabulary.RDF.type;
import static org.trellisldp.vocabulary.Trellis.PreferServerManaged;
import static org.trellisldp.vocabulary.Trellis.multipartUploadService;

import com.codahale.metrics.annotation.Timed;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.annotation.Priority;
import javax.inject.Singleton;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.slf4j.Logger;
import org.trellisldp.http.impl.TrellisDataset;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.XSD;

/**
 * @author acoburn
 */
@PreMatching
@Provider
@Priority(AUTHORIZATION + 10)
@Singleton
@Path(UPLOAD_PREFIX + "{partition}/{id}")
public class MultipartUploader implements ContainerRequestFilter, ContainerResponseFilter {

    private static final RDF rdf = getInstance();

    private static final Logger LOGGER = getLogger(MultipartUploader.class);

    private static final List<String> INVALID_EXT_METHODS = asList("PATCH", "PUT", "DELETE", "GET");

    private static final List<String> READ_METHODS = asList("GET", "HEAD", "OPTIONS");

    private final BinaryService binaryService;

    private final ResourceService resourceService;

    private final Map<String, String> partitions;

    /**
     * Create a multipart uploader object
     * @param resourceService the resource service
     * @param binaryService the binary service
     * @param partitions the partition base URLs
     */
    public MultipartUploader(final ResourceService resourceService, final BinaryService binaryService,
            final Map<String, String> partitions) {
        this.partitions = partitions;
        this.resourceService = resourceService;
        this.binaryService = binaryService;
    }



    @Override
    public void filter(final ContainerRequestContext ctx) throws IOException {
        final List<String> exts = ctx.getUriInfo().getQueryParameters().getOrDefault("ext", emptyList());
        if (exts.contains(UPLOADS)) {
            if (INVALID_EXT_METHODS.contains(ctx.getMethod())) {
                ctx.abortWith(status(METHOD_NOT_ALLOWED).build());
            }

            if (ctx.getMethod().equals("POST")) {
                final String partition = ctx.getUriInfo().getPathSegments().stream().map(PathSegment::getPath)
                    .findFirst().orElseThrow(() -> new WebApplicationException("Missing partition name!"));
                final String path = ctx.getUriInfo().getPath();
                final String baseUrl = partitions.getOrDefault(partition, ctx.getUriInfo().getBaseUri().toString());
                final String contentType = ofNullable(ctx.getMediaType()).map(MediaType::toString)
                    .orElse(APPLICATION_OCTET_STREAM);
                final String identifier = ofNullable(ctx.getHeaderString("Slug"))
                    .orElseGet(resourceService.getIdentifierSupplier());
                if (binaryService.getResolverForPartition(partition)
                        .filter(BinaryService.Resolver::supportsMultipartUpload).isPresent()) {
                    final String uploadId = binaryService.getResolverForPartition(partition)
                        .map(res-> res.initiateUpload(partition, rdf.createIRI(TRELLIS_PREFIX + path + identifier),
                                    contentType))
                        .orElseThrow(() -> new WebApplicationException("Cannot initiate multipart upload",
                                    BAD_REQUEST));
                    ctx.abortWith(status(CREATED).location(create(baseUrl + UPLOAD_PREFIX + partition +
                                "/" + uploadId)).build());
                }
            }
        }
    }

    @Override
    public void filter(final ContainerRequestContext req, final ContainerResponseContext res) throws IOException {
        if (READ_METHODS.contains(req.getMethod())) {

            if (res.getLinks().stream().filter(l -> l.getRel().equals(TYPE)).map(Link::getUri).map(URI::toString)
                .anyMatch(uri -> uri.equals(Container.getIRIString()) || uri.equals(NonRDFSource.getIRIString()))) {

                final String partition = req.getUriInfo().getPathSegments().stream().map(PathSegment::getPath)
                    .findFirst().orElseThrow(() -> new WebApplicationException("Missing partition name!"));
                final String identifier = partitions.getOrDefault(partition, req.getUriInfo().getBaseUri().toString()) +
                    req.getUriInfo().getPath();

                binaryService.getResolverForPartition(partition)
                    .map(BinaryService.Resolver::supportsMultipartUpload).ifPresent(x ->
                        res.getHeaders().add("Link", fromUri(identifier + "?ext=" + UPLOADS)
                            .rel(multipartUploadService.getIRIString()).build()));
            }

            final List<String> exts = req.getUriInfo().getQueryParameters().getOrDefault("ext", emptyList());
            if (exts.contains(UPLOADS)) {
                res.getHeaders().putSingle(ALLOW, join(",", POST, OPTIONS));
            }
        }
    }

    /**
     * Get a list of the uploads
     * @param partition the partition
     * @param id the upload id
     * @return a response
     *
     * <p>Note: the response structure will be like this:</p>
     * <pre>{
     *   "1": "somehash",
     *   "2": "otherhash",
     *   "3": "anotherhash"
     * }</pre>
     */
    @GET
    @Timed
    @Produces("application/json")
    public String listUploads(@PathParam("partition") final String partition,
            @PathParam("id") final String id) {
        final JsonObjectBuilder builder = Json.createObjectBuilder();

        binaryService.getResolverForPartition(partition)
            .filter(BinaryService.Resolver::supportsMultipartUpload)
            .filter(res -> res.uploadSessionExists(id))
            .map(res -> res.listParts(id))
            .orElseThrow(NotFoundException::new)
            .forEach(x -> builder.add(x.getKey().toString(), x.getValue()));

        return builder.build().toString();
    }

    /**
     * Create a binary from a collection of uploaded parts
     * @param partition the partition
     * @param id the identifier
     * @param input the input value
     * @return a response
     *
     * <p>Note: The structure should be like this:</p>
     * <pre>{
     *   "1": "somehash",
     *   "2": "otherhash",
     *   "3": "anotherhash"
     * }</pre>
     */
    @POST
    @Timed
    @Consumes("application/json")
    public Response createBinary(@PathParam("partition") final String partition,
            @PathParam("id") final String id, final InputStream input) {

        final JsonReader reader = Json.createReader(input);
        final JsonObject obj = reader.readObject();
        reader.close();
        final Map<Integer, String> partDigests = obj.keySet().stream()
            .collect(toMap(Integer::parseInt, obj::getString));

        return binaryService.getResolverForPartition(partition)
            .filter(BinaryService.Resolver::supportsMultipartUpload)
            .filter(svc -> svc.uploadSessionExists(id))
            .map(svc -> svc.completeUpload(id, partDigests))
            .map(upload -> {
                try (final TrellisDataset dataset = TrellisDataset.createDataset()) {
                    final IRI identifier = rdf.createIRI(TRELLIS_PREFIX + upload.getPath());

                    // Add Audit quads
                    auditCreation(identifier, upload.getSession()).stream()
                        .map(skolemizeQuads(resourceService, upload.getBaseUrl())).forEach(dataset::add);
                    dataset.add(rdf.createQuad(PreferServerManaged, identifier, type, NonRDFSource));
                    dataset.add(rdf.createQuad(PreferServerManaged, identifier, DC.hasPart,
                                upload.getBinary().getIdentifier()));
                    dataset.add(rdf.createQuad(PreferServerManaged, upload.getBinary().getIdentifier(), DC.format,
                                rdf.createLiteral(upload.getBinary().getMimeType().orElse(APPLICATION_OCTET_STREAM))));
                    upload.getBinary().getSize().ifPresent(size -> dataset.add(rdf.createQuad(PreferServerManaged,
                                    upload.getBinary().getIdentifier(), DC.extent,
                                    rdf.createLiteral(size.toString(), XSD.long_))));

                    if (resourceService.put(identifier, dataset.asDataset())) {
                        return created(create(upload.getBaseUrl() + upload.getPath())).build();
                    }
                }
                LOGGER.error("Could not persist data");
                return serverError().entity("Could not persist data internally").build();
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
     *
     *  <p>Note: the response will be a json structure, such as:</p>
     *  <pre>{
     *    "digest": "a-hash"
     *  }</pre>
     */
    @PUT
    @Timed
    @Path("{partNumber}")
    @Produces("application/json")
    public String uploadPart(@PathParam("partition") final String partition,
            @PathParam("id") final String id,
            @PathParam("partNumber") final Integer partNumber,
            final InputStream part) {

        final JsonObjectBuilder builder = Json.createObjectBuilder();

        final String digest = binaryService.getResolverForPartition(partition)
            .filter(BinaryService.Resolver::supportsMultipartUpload)
            .filter(res -> res.uploadSessionExists(id))
            .map(res -> res.uploadPart(id, partNumber, part))
            .orElseThrow(NotFoundException::new);

        return builder.add("digest", digest).build().toString();
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
