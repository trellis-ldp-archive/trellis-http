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
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.status;
import static org.trellisldp.http.HttpConstants.APPLICATION_LINK_FORMAT;
import static org.trellisldp.http.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.RdfMediaType.APPLICATION_LD_JSON;
import static org.trellisldp.http.RdfMediaType.APPLICATION_N_TRIPLES;
import static org.trellisldp.http.RdfMediaType.TEXT_TURTLE;
import static org.trellisldp.http.RdfUtils.getProfile;
import static org.trellisldp.http.RdfUtils.getRdfSyntax;
import static org.trellisldp.spi.ConstraintService.ldpResourceTypes;

import com.codahale.metrics.annotation.Timed;

import java.io.InputStream;

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

import org.trellisldp.api.Resource;
import org.trellisldp.spi.DatastreamService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.SerializationService;
import org.trellisldp.spi.Session;
import org.trellisldp.vocabulary.LDP;

/**
 * @author acoburn
 */
@Path("{path: .+}")
@Produces({TEXT_TURTLE, APPLICATION_LD_JSON, APPLICATION_N_TRIPLES, APPLICATION_LINK_FORMAT, TEXT_HTML})
public class LdpResource extends BaseLdpResource {

    protected final ResourceService resourceService;

    protected final SerializationService serializationService;

    protected final DatastreamService datastreamService;

    protected final String baseUrl;

    protected final Session session;

    /**
     * Create a LdpResource
     * @param baseUrl the baseUrl
     * @param resourceService the resource service
     * @param serializationService the serialization service
     * @param datastreamService the datastream service
     */
    public LdpResource(final String baseUrl, final ResourceService resourceService,
            final SerializationService serializationService,
            final DatastreamService datastreamService) {
        super();
        this.baseUrl = baseUrl;
        this.resourceService = resourceService;
        this.serializationService = serializationService;
        this.datastreamService = datastreamService;
        // TODO -- add user session
        this.session = new HttpSession();
    }

    /**
     * Perform a GET operation on an LDP Resource
     * @param path the path
     * @param version the version parameter
     * @param timemap the timemap parameter
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
            @QueryParam("timemap") final Boolean timemap,
            @HeaderParam("Accept-Datetime") final AcceptDatetime datetime,
            @HeaderParam("Prefer") final Prefer prefer,
            @HeaderParam("Want-Digest") final WantDigest digest,
            @HeaderParam("Range") final Range range) {

        if (path.endsWith("/")) {
            return redirectWithoutSlash(path);
        }

        final LdpRequest ldpreq = LdpRequest.builder().withPath(path)
            .withBaseUrl(ofNullable(baseUrl).orElseGet(() -> uriInfo.getBaseUri().toString()))
            .withSyntax(getRdfSyntax(headers.getAcceptableMediaTypes()))
            .withProfile(getProfile(headers.getAcceptableMediaTypes()))
            .withPrefer(prefer).withWantDigest(digest).withRange(range).build();

        final LdpGetHandler getHandler = new LdpGetHandler(resourceService, serializationService, datastreamService,
                request, ldpreq);

        if (nonNull(version)) {
            LOGGER.info("Getting versioned resource: {}", version.toString());
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), version.getInstant())
                .map(getHandler::getRepresentation).orElse(status(NOT_FOUND)).build();

        } else if (nonNull(timemap) && timemap) {
            LOGGER.info("Getting timemap resource");
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path)).map(MementoResource::new)
                .map(res -> res.getTimeMapBuilder(baseUrl + path, ldpreq.getSyntax().orElse(null),
                            serializationService))
                .orElse(status(NOT_FOUND)).build();

        } else if (nonNull(datetime)) {
            LOGGER.info("Getting timegate resource: {}", datetime.getInstant());
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), datetime.getInstant())
                .map(MementoResource::new).map(res -> res.getTimeGateBuilder(baseUrl + path, datetime.getInstant()))
                .orElse(status(NOT_FOUND)).build();
        }

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path))
                .map(getHandler::getRepresentation).orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform a PATCH operation on an LDP Resource
     * @param path the path
     * @param prefer the Prefer header
     * @param body the body
     * @return the response
     */
    @PATCH
    @Timed
    @Consumes("application/sparql-update")
    public Response updateResource(@PathParam("path") final String path,
            @HeaderParam("Prefer") final Prefer prefer, final String body) {

        if (path.endsWith("/")) {
            return redirectWithoutSlash(path);
        }

        final LdpRequest ldpreq = LdpRequest.builder().withPath(path)
            .withBaseUrl(ofNullable(baseUrl).orElseGet(() -> uriInfo.getBaseUri().toString()))
            .withSyntax(getRdfSyntax(headers.getAcceptableMediaTypes()))
            .withProfile(getProfile(headers.getAcceptableMediaTypes()))
            .withPrefer(prefer).withSession(session).withSparqlUpdate(body).build();

        final LdpPatchHandler patchHandler = new LdpPatchHandler(resourceService, serializationService, request,
                ldpreq);

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), MAX)
                .map(patchHandler::updateResource).orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform a DELETE operation on an LDP Resource
     * @param path the path
     * @return the response
     */
    @DELETE
    @Timed
    public Response deleteResource(@PathParam("path") final String path) {

        if (path.endsWith("/")) {
            return redirectWithoutSlash(path);
        }

        final LdpRequest ldpreq = LdpRequest.builder().withPath(path)
            .withBaseUrl(ofNullable(baseUrl).orElseGet(() -> uriInfo.getBaseUri().toString()))
            .withSession(session).build();

        final LdpDeleteHandler deleteHandler = new LdpDeleteHandler(resourceService, request, ldpreq);

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), MAX)
            .map(deleteHandler::deleteResource).orElse(status(NOT_FOUND)).build();
    }

    /**
     * Perform a POST operation on a LDP Resource
     * @param path the path
     * @param link the LDP interaction model
     * @param contentType the content-type
     * @param slug the slug header
     * @param body the body
     * @return the response
     */
    @POST
    @Timed
    public Response createResource(@PathParam("path") final String path,
            @HeaderParam("Link") final Link link,
            @HeaderParam("Content-Type") final String contentType,
            @HeaderParam("Slug") final String slug,
            final InputStream body) {

        if (path.endsWith("/")) {
            return redirectWithoutSlash(path);
        }

        final String fullPath = path + "/" + ofNullable(slug).orElseGet(() -> randomUUID().toString());

        final LdpRequest ldpreq = LdpRequest.builder().withPath(fullPath)
            .withBaseUrl(ofNullable(baseUrl).orElseGet(() -> uriInfo.getBaseUri().toString()))
            .withSession(session).withContentType(contentType).withLink(link).withEntity(body).build();


        final LdpPostHandler postHandler = new LdpPostHandler(resourceService, serializationService, datastreamService,
                ldpreq);

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
     * @param link the LDP interaction model
     * @param contentType the content-type
     * @param body the body
     * @return the response
     */
    @PUT
    @Timed
    public Response setResource(@PathParam("path") final String path,
            @HeaderParam("Link") final Link link,
            @HeaderParam("Content-Type") final String contentType,
            final InputStream body) {

        if (path.endsWith("/")) {
            return redirectWithoutSlash(path);
        }

        return Response.ok().build();
    }
}
