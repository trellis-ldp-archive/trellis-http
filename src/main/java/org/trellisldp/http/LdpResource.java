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
import static java.time.Instant.MAX;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.Status.UNSUPPORTED_MEDIA_TYPE;
import static javax.ws.rs.core.Response.status;
import static org.trellisldp.http.domain.HttpConstants.ACL;
import static org.trellisldp.http.domain.HttpConstants.APPLICATION_LINK_FORMAT;
import static org.trellisldp.http.domain.HttpConstants.PART_NUMBER;
import static org.trellisldp.http.domain.HttpConstants.TIMEMAP;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.domain.HttpConstants.UPLOADS;
import static org.trellisldp.http.domain.HttpConstants.UPLOAD_ID;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON_TYPE;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_N_TRIPLES;
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE;
import static org.trellisldp.spi.ConstraintService.ldpResourceTypes;

import com.codahale.metrics.annotation.Timed;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.IRI;

import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.AcceptDatetime;
import org.trellisldp.http.domain.PATCH;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.http.domain.Range;
import org.trellisldp.http.domain.Version;
import org.trellisldp.http.domain.WantDigest;
import org.trellisldp.http.impl.LdpDeleteHandler;
import org.trellisldp.http.impl.LdpGetHandler;
import org.trellisldp.http.impl.LdpOptionsHandler;
import org.trellisldp.http.impl.LdpPatchHandler;
import org.trellisldp.http.impl.LdpPostHandler;
import org.trellisldp.http.impl.LdpPutHandler;
import org.trellisldp.http.impl.MementoResource;
import org.trellisldp.spi.AccessControlService;
import org.trellisldp.spi.AgentService;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.Session;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
@Path("{path: .+}")
@Produces({TEXT_TURTLE, APPLICATION_LD_JSON, APPLICATION_N_TRIPLES, APPLICATION_LINK_FORMAT, TEXT_HTML})
public class LdpResource extends BaseLdpResource {

    protected final ResourceService resourceService;

    protected final IOService ioService;

    protected final BinaryService binaryService;

    protected final ConstraintService constraintService;

    protected final Collection<String> unsupportedTypes;

    private static final String DIGEST = "digest";

    private static final String ALGORITHM = "algorithm";

    /**
     * Create a LdpResource
     * @param resourceService the resource service
     * @param ioService the i/o service
     * @param constraintService the RDF constraint enforcing service
     * @param binaryService the datastream service
     * @param agentService the agent service
     * @param accessService the access control service
     * @param partitions a map of partitions for use with custom hostnames
     * @param authChallenges a list of auth protocols supported
     * @param unsupportedMediaTypes any unsupported media types
     */
    public LdpResource(final ResourceService resourceService, final IOService ioService,
            final ConstraintService constraintService, final BinaryService binaryService,
            final AgentService agentService, final AccessControlService accessService,
            final Map<String, String> partitions, final List<String> authChallenges,
            final Collection<String> unsupportedMediaTypes) {
        super(partitions, authChallenges, agentService, accessService);
        this.resourceService = resourceService;
        this.ioService = ioService;
        this.binaryService = binaryService;
        this.constraintService = constraintService;
        this.unsupportedTypes = unsupportedMediaTypes;
    }

    /**
     * Perform a GET operation on an LDP Resource
     * @param path the path
     * @param version the version parameter
     * @param ext an extension parameter
     * @param uploadId an upload identifier
     * @param datetime the Accept-Datetime header
     * @param prefer the Prefer header
     * @param digest the Want-Digest header
     * @param range the Range header
     * @return the response
     */
    @GET
    @Timed
    public Response getResource(@PathParam("path") final String path,
            @QueryParam("version") final Version version,
            @QueryParam("ext") final String ext,
            @QueryParam("uploadId") final String uploadId,
            @HeaderParam("Accept-Datetime") final AcceptDatetime datetime,
            @HeaderParam("Prefer") final Prefer prefer,
            @HeaderParam("Want-Digest") final WantDigest digest,
            @HeaderParam("Range") final Range range) {

        final List<MediaType> acceptableTypes = headers.getAcceptableMediaTypes();
        final String baseUrl = getBaseUrl(path);

        final Session session = getSession();
        if (ACL.equals(ext)) {
            verifyCanControl(session, path);
        } else {
            verifyCanRead(session, path);
        }

        final LdpGetHandler getHandler = new LdpGetHandler(resourceService, ioService, binaryService,
                request);
        getHandler.setPath(path);
        getHandler.setBaseUrl(baseUrl);
        getHandler.setAcceptableTypes(acceptableTypes);
        if (ACL.equals(ext)) {
            // TODO make this more compact?
            getHandler.setPrefer(new Prefer("return=representation; include=\"" +
                        Trellis.PreferAccessControl.getIRIString() + "\"; omit=\"" +
                        Trellis.PreferUserManaged.getIRIString() + " " +
                        LDP.PreferContainment.getIRIString() + " " +
                        LDP.PreferMembership.getIRIString() + "\""));
            getHandler.setGraphName(Trellis.PreferAccessControl);
        } else {
            getHandler.setPrefer(prefer);
        }
        getHandler.setWantDigest(digest);
        getHandler.setRange(range);

        // Handle any multipart upload requests
        if (nonNull(uploadId)) {
            return binaryService.getResolverForPartition(getPartition(path))
                .filter(BinaryService.Resolver::supportsMultipartUpload)
                .map(resolver -> buildPartsResponseEntity(baseUrl + path + "?" + UPLOAD_ID + "=" + uploadId,
                            resolver.listParts(uploadId)))
                .map(data -> status(OK).type(APPLICATION_LD_JSON_TYPE).entity(data))
                .orElseGet(() -> status(NOT_FOUND)).build();

        // Fetch a versioned resource
        } else if (nonNull(version)) {
            LOGGER.info("Getting versioned resource: {}", version.toString());
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), version.getInstant())
                .map(getHandler::getRepresentation).orElse(status(NOT_FOUND)).build();

        // Fetch a timemap
        } else if (TIMEMAP.equals(ext)) {
            LOGGER.info("Getting timemap resource");
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path)).map(MementoResource::new)
                .map(res -> res.getTimeMapBuilder(baseUrl + path, acceptableTypes, ioService))
                .orElse(status(NOT_FOUND)).build();

        // Fetch a timegate
        } else if (nonNull(datetime)) {
            LOGGER.info("Getting timegate resource: {}", datetime.getInstant());
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), datetime.getInstant())
                .map(MementoResource::new).map(res -> res.getTimeGateBuilder(baseUrl + path, datetime.getInstant()))
                .orElse(status(NOT_FOUND)).build();
        }

        // Fetch the current state of the resource
        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path))
                .map(getHandler::getRepresentation).orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform an OPTIONS operation on an LDP Resource
     * @param path the path
     * @param version the version parameter
     * @param ext an extension parameter
     * @param uploadId the upload identifier
     * @param partNumber the part number
     * @return the response
     */
    @OPTIONS
    @Timed
    public Response options(@PathParam("path") final String path,
            @QueryParam("version") final Version version,
            @QueryParam("ext") final String ext,
            @QueryParam("uploadId") final String uploadId,
            @QueryParam("partNumber") final Integer partNumber) {

        final Session session = getSession();
        if (ACL.equals(ext)) {
            verifyCanControl(session, path);
        } else {
            verifyCanRead(session, path);
        }

        // Short-circuit any multipart handling if support isn't available
        if (nonNull(uploadId) || UPLOADS.equals(ext)) {
            if (!binaryService.getResolverForPartition(getPartition(path))
                    .filter(BinaryService.Resolver::supportsMultipartUpload).isPresent()) {
                return status(NOT_FOUND).build();
            }
        }

        final LdpOptionsHandler optionsHandler = new LdpOptionsHandler(resourceService);
        optionsHandler.setPath(path);
        optionsHandler.setBaseUrl(getBaseUrl(path));
        optionsHandler.setMultipartUploadState(getUploadState(ext, uploadId, partNumber));

        if (ACL.equals(ext)) {
            optionsHandler.setGraphName(Trellis.PreferAccessControl);
        }

        if (nonNull(version)) {
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), version.getInstant())
                .map(optionsHandler::ldpOptions).orElse(status(NOT_FOUND)).build();

        } else if (TIMEMAP.equals(ext)) {
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), MAX)
                .map(optionsHandler::ldpOptions).orElse(status(NOT_FOUND)).build();
        }

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path))
            .map(optionsHandler::ldpOptions).orElse(status(NOT_FOUND)).build();
    }


    /**
     * Perform a PATCH operation on an LDP Resource
     * @param path the path
     * @param version a version parameter
     * @param ext an extension parameter
     * @param prefer the Prefer header
     * @param body the body
     * @return the response
     */
    @PATCH
    @Timed
    @Consumes("application/sparql-update")
    public Response updateResource(@PathParam("path") final String path,
            @QueryParam("version") final String version,
            @QueryParam("ext") final String ext,
            @HeaderParam("Prefer") final Prefer prefer, final String body) {

        final Session session = getSession();
        if (ACL.equals(ext)) {
            verifyCanControl(session, path);
        } else {
            verifyCanWrite(session, path);
        }

        if (nonNull(version) || UPLOADS.equals(ext)) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final LdpPatchHandler patchHandler = new LdpPatchHandler(resourceService, ioService, constraintService,
                request);
        patchHandler.setPath(path);
        patchHandler.setBaseUrl(getBaseUrl(path));
        patchHandler.setAcceptableTypes(headers.getAcceptableMediaTypes());
        patchHandler.setPrefer(prefer);
        patchHandler.setSession(session);
        patchHandler.setSparqlUpdate(body);
        if (ACL.equals(ext)) {
            patchHandler.setGraphName(Trellis.PreferAccessControl);
        }

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), MAX)
                .map(patchHandler::updateResource).orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform a DELETE operation on an LDP Resource
     * @param path the path
     * @param ext an extension parameter
     * @param uploadId the upload id
     * @param version a version parameter
     * @return the response
     */
    @DELETE
    @Timed
    public Response deleteResource(@PathParam("path") final String path,
            @QueryParam("ext") final String ext,
            @QueryParam("uploadId") final String uploadId,
            @QueryParam("version") final String version) {

        final Session session = getSession();
        verifyCanWrite(session, path);

        if (nonNull(ext) || nonNull(version)) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        // Multipart upload handler
        if (nonNull(uploadId)) {
            return binaryService.getResolverForPartition(getPartition(path))
                .filter(BinaryService.Resolver::supportsMultipartUpload).map(resolver -> {
                    resolver.abortUpload(uploadId);
                    return status(NO_CONTENT);
                }).orElseGet(() -> status(NOT_FOUND)).build();
        }

        final LdpDeleteHandler deleteHandler = new LdpDeleteHandler(resourceService, request);
        deleteHandler.setPath(path);
        deleteHandler.setBaseUrl(getBaseUrl(path));
        deleteHandler.setSession(session);

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), MAX)
            .map(deleteHandler::deleteResource).orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform a POST operation on a LDP Resource
     * @param path the path
     * @param version a version parameter
     * @param ext an extension parameter
     * @param uploadId an upload identifier
     * @param link the LDP interaction model
     * @param contentType the content-type
     * @param slug the slug header
     * @param body the body
     * @return the response
     */
    @POST
    @Timed
    public Response createResource(@PathParam("path") final String path,
            @QueryParam("version") final String version,
            @QueryParam("ext") final String ext,
            @QueryParam("uploadId") final String uploadId,
            @HeaderParam("Link") final Link link,
            @HeaderParam("Content-Type") final String contentType,
            @HeaderParam("Slug") final String slug,
            final InputStream body) {

        final Session session = getSession();
        verifyCanAppend(session, path);

        if (unsupportedTypes.contains(contentType)) {
            return status(UNSUPPORTED_MEDIA_TYPE).build();
        }

        final String baseUrl = getBaseUrl(path);

        if (nonNull(uploadId) && !binaryService.getResolverForPartition(getPartition(path))
                    .filter(BinaryService.Resolver::supportsMultipartUpload).isPresent()) {
            return status(NOT_FOUND).build();
        }

        final String fullPath = path + "/" + ofNullable(slug).orElseGet(resourceService.getIdentifierSupplier());

        // Multipart upload handler
        if (UPLOADS.equals(ext)) {
            return binaryService.getResolverForPartition(getPartition(path))
                .filter(BinaryService.Resolver::supportsMultipartUpload)
                .map(resolver -> resolver.initiateUpload(getPartition(path), rdf.createIRI(TRELLIS_PREFIX + fullPath),
                            contentType))
                .map(id -> status(CREATED).location(create(baseUrl + path + "?" + UPLOAD_ID + "=" + id))
                        .link(Trellis.BinaryUploadService.getIRIString(), "type")
                        .link(baseUrl + path + "?" + UPLOAD_ID + "=" + id + "&" + PART_NUMBER + "=1", "first"))
                .orElseGet(() -> status(NOT_FOUND)).build();
        }

        if (nonNull(ext) || nonNull(version)) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final LdpPostHandler postHandler = new LdpPostHandler(resourceService, ioService, constraintService,
                binaryService);
        postHandler.setPath(fullPath);
        postHandler.setBaseUrl(baseUrl);
        postHandler.setSession(session);
        postHandler.setContentType(contentType);
        postHandler.setLink(link);
        postHandler.setEntity(body);
        postHandler.setUploadId(uploadId);

        // First check if this is a container
        final Optional<Resource> parent = resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), MAX);
        if (parent.isPresent()) {
            final Optional<IRI> ixModel = parent.map(Resource::getInteractionModel);
            if (ixModel.filter(type -> ldpResourceTypes(type).anyMatch(LDP.Container::equals)).isPresent()) {
                return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + fullPath), MAX).map(x -> status(CONFLICT))
                    .orElseGet(postHandler::createResource).build();
            } else if (ixModel.filter(LDP.NonRDFSource::equals).isPresent() && nonNull(uploadId)) {
                postHandler.setPath(path);
                return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), MAX)
                    .map(res -> postHandler.createResource())
                    .orElseGet(() -> status(NOT_FOUND)).build();
            } else if (ixModel.filter(LDP.Resource::equals).isPresent() &&
                    parent.get().getTypes().anyMatch(Trellis.DeletedResource::equals)) {
                return status(GONE).build();
            }
            return status(METHOD_NOT_ALLOWED).build();
        }
        return status(NOT_FOUND).build();
    }

    /**
     * Perform a PUT operation on a LDP Resource
     * @param path the path
     * @param version the version parameter
     * @param ext an extension parameter
     * @param uploadId an upload identifier
     * @param partNumber the part number for an upload session
     * @param link the LDP interaction model
     * @param contentType the content-type
     * @param body the body
     * @return the response
     */
    @PUT
    @Timed
    public Response setResource(@PathParam("path") final String path,
            @QueryParam("version") final Version version,
            @QueryParam("ext") final String ext,
            @QueryParam("uploadId") final String uploadId,
            @QueryParam("partNumber") final Integer partNumber,
            @HeaderParam("Link") final Link link,
            @HeaderParam("Content-Type") final String contentType,
            final InputStream body) {

        final Session session = getSession();
        verifyCanWrite(session, path);

        if (unsupportedTypes.contains(contentType)) {
            return status(UNSUPPORTED_MEDIA_TYPE).build();
        }

        if (nonNull(ext) || nonNull(version)) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final String baseUrl = getBaseUrl(path);

        // TODO -- move this into LdpPutHandler
        if (nonNull(uploadId) || nonNull(partNumber)) {
            return binaryService.getResolverForPartition(getPartition(path))
                .filter(BinaryService.Resolver::supportsMultipartUpload)
                .filter(x -> nonNull(uploadId)).filter(x -> nonNull(partNumber))
                .map(resolver -> resolver.uploadPart(uploadId, partNumber, body))
                .map(hash -> status(ACCEPTED).type(APPLICATION_LD_JSON_TYPE)
                        .link(Trellis.BinaryUploadService.getIRIString(), "type")
                        .link(baseUrl + path + "?" + UPLOAD_ID + "=" + uploadId + "&" + PART_NUMBER + "=1", "first")
                        .link(baseUrl + path + "?" + UPLOAD_ID + "=" + uploadId + "&" + PART_NUMBER + "=" +
                            (partNumber + 1), "next")
                        .link(baseUrl + path + "?" + UPLOAD_ID + "=" + uploadId, "last")
                        .entity(buildUploadResponseEntity(hash, "md5", partNumber)))
                .orElseGet(() -> status(NOT_FOUND)).build();
        }

        final LdpPutHandler putHandler = new LdpPutHandler(resourceService, ioService, constraintService,
                binaryService, request);
        putHandler.setPath(path);
        putHandler.setBaseUrl(baseUrl);
        putHandler.setSession(getSession());
        putHandler.setContentType(contentType);
        putHandler.setLink(link);
        putHandler.setEntity(body);

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), MAX)
                .map(putHandler::setResource).orElseGet(putHandler::setResource).build();
    }

    private String buildUploadResponseEntity(final String hash, final String algorithm,
            final Integer partNumber) {
        final Map<String, Object> data = new HashMap<>();
        data.put(DIGEST, hash);
        data.put(PART_NUMBER, partNumber);
        data.put(ALGORITHM, algorithm);
        data.put("@context", getBaseContext());
        return convertToJson(data);
    }

    private Map<String, String> getBaseContext() {
        final Map<String, String> context = new HashMap<>();
        context.put(PART_NUMBER, "http://purl.org/dc/terms/identifier");
        context.put(DIGEST, "http://www.loc.gov/premis/rdf/v1#hasMessageDigest");
        context.put(ALGORITHM, "http://www.loc.gov/premis/rdf/v1#hasMessageDigestAlgorithm");
        return context;
    }

    private String buildPartsResponseEntity(final String identifier, final Stream<Map.Entry<Integer, String>> parts) {
        final List<Object> contexts = new ArrayList<>();
        contexts.add("https://www.w3.org/ns/activitystreams");
        contexts.add(getBaseContext());

        final Map<String, Object> data = new HashMap<>();
        data.put("@context", contexts);
        data.put("id", identifier);
        data.put("type", "Collection");
        data.put("name", "Multipart Upload");
        data.put("items", parts.map(part -> {
            final Map<String, Object> d = new HashMap<>();
            d.put(PART_NUMBER, part.getKey());
            d.put(DIGEST, part.getValue());
            d.put(ALGORITHM, "md5");
            return d;
        }).collect(toList()));
        return convertToJson(data);
    }
}
