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

import static java.time.Instant.MAX;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.UNSUPPORTED_MEDIA_TYPE;
import static javax.ws.rs.core.Response.status;
import static org.trellisldp.http.domain.HttpConstants.ACL;
import static org.trellisldp.http.domain.HttpConstants.APPLICATION_LINK_FORMAT;
import static org.trellisldp.http.domain.HttpConstants.TIMEMAP;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.domain.Prefer.ofInclude;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_N_TRIPLES;
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE;
import static org.trellisldp.http.impl.RdfUtils.getProfile;
import static org.trellisldp.http.impl.RdfUtils.getRdfSyntax;
import static org.trellisldp.spi.ConstraintService.ldpResourceTypes;

import com.codahale.metrics.annotation.Timed;

import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.RDFSyntax;

import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.AcceptDatetime;
import org.trellisldp.http.domain.PATCH;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.http.domain.Range;
import org.trellisldp.http.domain.Version;
import org.trellisldp.http.domain.WantDigest;
import org.trellisldp.http.impl.LdpDeleteHandler;
import org.trellisldp.http.impl.LdpGetHandler;
import org.trellisldp.http.impl.LdpPatchHandler;
import org.trellisldp.http.impl.LdpPostHandler;
import org.trellisldp.http.impl.LdpPutHandler;
import org.trellisldp.http.impl.MementoResource;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
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

    /**
     * Create a LdpResource
     * @param resourceService the resource service
     * @param ioService the i/o service
     * @param constraintService the RDF constraint enforcing service
     * @param binaryService the datastream service
     * @param partitions a map of partitions for use with custom hostnames
     * @param unsupportedMediaTypes any unsupported media types
     */
    public LdpResource(final ResourceService resourceService,
            final IOService ioService, final ConstraintService constraintService,
            final BinaryService binaryService, final Map<String, String> partitions,
            final Collection<String> unsupportedMediaTypes) {
        super(partitions);
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
     * @param format the format parameter
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
            @QueryParam("format") final String format,
            @HeaderParam("Accept-Datetime") final AcceptDatetime datetime,
            @HeaderParam("Prefer") final Prefer prefer,
            @HeaderParam("Want-Digest") final WantDigest digest,
            @HeaderParam("Range") final Range range) {

        if (path.endsWith("/")) {
            return redirectWithoutSlash(path);
        }

        final RDFSyntax syntax = getRdfSyntax(headers.getAcceptableMediaTypes());
        final String baseUrl = getBaseUrl(path);
        final LdpGetHandler getHandler = new LdpGetHandler(resourceService, ioService, binaryService,
                request);
        getHandler.setPath(path);
        getHandler.setBaseUrl(baseUrl);
        getHandler.setSyntax(syntax);
        getHandler.setProfile(getProfile(headers.getAcceptableMediaTypes()));
        if (ACL.equals(format)) {
            getHandler.setPrefer(ofInclude(Trellis.PreferAccessControl.getIRIString()));
        } else {
            getHandler.setPrefer(prefer);
        }
        getHandler.setWantDigest(digest);
        getHandler.setRange(range);
        getHandler.setAcl(ACL.equals(format));

        // Fetch a versioned resource
        if (nonNull(version)) {
            LOGGER.info("Getting versioned resource: {}", version.toString());
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), version.getInstant())
                .map(getHandler::getRepresentation).orElse(status(NOT_FOUND)).build();

        // Fetch a timemap
        } else if (TIMEMAP.equals(format)) {
            LOGGER.info("Getting timemap resource");
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path)).map(MementoResource::new)
                .map(res -> res.getTimeMapBuilder(baseUrl + path, syntax, ioService))
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
     * Perform a PATCH operation on an LDP Resource
     * @param path the path
     * @param format a format parameter
     * @param prefer the Prefer header
     * @param body the body
     * @return the response
     */
    @PATCH
    @Timed
    @Consumes("application/sparql-update")
    public Response updateResource(@PathParam("path") final String path,
            @QueryParam("format") final String format,
            @HeaderParam("Prefer") final Prefer prefer, final String body) {

        if (path.endsWith("/")) {
            return redirectWithoutSlash(path);
        }

        final LdpPatchHandler patchHandler = new LdpPatchHandler(resourceService, ioService, constraintService,
                request);
        patchHandler.setPath(path);
        patchHandler.setBaseUrl(getBaseUrl(path));
        patchHandler.setSyntax(getRdfSyntax(headers.getAcceptableMediaTypes()));
        patchHandler.setProfile(getProfile(headers.getAcceptableMediaTypes()));
        patchHandler.setPrefer(prefer);
        patchHandler.setSession(session);
        patchHandler.setSparqlUpdate(body);
        patchHandler.setAcl(ACL.equals(format));

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), MAX)
                .map(patchHandler::updateResource).orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform a DELETE operation on an LDP Resource
     * @param format a format parameter
     * @param path the path
     * @return the response
     */
    @DELETE
    @Timed
    public Response deleteResource(@PathParam("path") final String path,
            @QueryParam("format") final String format) {

        if (path.endsWith("/")) {
            return redirectWithoutSlash(path);
        }

        if (nonNull(format)) {
            return status(METHOD_NOT_ALLOWED).build();
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
     * @param format a format parameter
     * @param link the LDP interaction model
     * @param contentType the content-type
     * @param slug the slug header
     * @param body the body
     * @return the response
     */
    @POST
    @Timed
    public Response createResource(@PathParam("path") final String path,
            @QueryParam("format") final String format,
            @HeaderParam("Link") final Link link,
            @HeaderParam("Content-Type") final String contentType,
            @HeaderParam("Slug") final String slug,
            final InputStream body) {

        if (path.endsWith("/")) {
            return redirectWithoutSlash(path);
        }

        if (unsupportedTypes.contains(contentType)) {
            return status(UNSUPPORTED_MEDIA_TYPE).build();
        }

        if (nonNull(format)) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final String fullPath = path + "/" + ofNullable(slug).orElseGet(resourceService.getIdentifierSupplier());

        final LdpPostHandler postHandler = new LdpPostHandler(resourceService, ioService, constraintService,
                binaryService);
        postHandler.setPath(fullPath);
        postHandler.setBaseUrl(getBaseUrl(path));
        postHandler.setSession(session);
        postHandler.setContentType(contentType);
        postHandler.setLink(link);
        postHandler.setEntity(body);

        // First check if this is a container
        if (resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), MAX).map(Resource::getInteractionModel)
                .filter(type -> ldpResourceTypes(type).anyMatch(LDP.Container::equals)).isPresent()) {
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + fullPath), MAX).map(x -> status(CONFLICT))
                .orElseGet(postHandler::createResource).build();
        } else {
            return status(METHOD_NOT_ALLOWED).build();
        }
    }

    /**
     * Perform a PUT operation on a LDP Resource
     * @param path the path
     * @param format the format parameter
     * @param link the LDP interaction model
     * @param contentType the content-type
     * @param body the body
     * @return the response
     */
    @PUT
    @Timed
    public Response setResource(@PathParam("path") final String path,
            @QueryParam("format") final String format,
            @HeaderParam("Link") final Link link,
            @HeaderParam("Content-Type") final String contentType,
            final InputStream body) {

        if (path.endsWith("/")) {
            return redirectWithoutSlash(path);
        }

        if (unsupportedTypes.contains(contentType)) {
            return status(UNSUPPORTED_MEDIA_TYPE).build();
        }

        if (nonNull(format)) {
            return status(METHOD_NOT_ALLOWED).build();
        }

        final LdpPutHandler putHandler = new LdpPutHandler(resourceService, ioService, constraintService,
                binaryService, request);
        putHandler.setPath(path);
        putHandler.setBaseUrl(getBaseUrl(path));
        putHandler.setSession(session);
        putHandler.setContentType(contentType);
        putHandler.setLink(link);
        putHandler.setEntity(body);

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), MAX)
                .map(putHandler::setResource).orElseGet(putHandler::setResource).build();
    }
}
