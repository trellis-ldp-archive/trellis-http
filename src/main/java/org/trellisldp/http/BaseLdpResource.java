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

import static java.util.Date.from;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.RdfUtils.getInstance;
import static org.trellisldp.http.RdfUtils.getRdfSyntax;
import static org.trellisldp.http.RdfUtils.toExternalIri;

import com.codahale.metrics.annotation.Timed;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.rdf.api.BlankNodeOrIRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFSyntax;
import org.slf4j.Logger;
import org.trellisldp.api.Resource;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.SerializationService;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
abstract class BaseLdpResource {

    private static final Logger LOGGER = getLogger(BaseLdpResource.class);

    protected static final RDF rdf = getInstance();

    protected final ResourceService resourceService;

    protected final SerializationService serializationService;

    protected final String baseUrl;

    @Context
    protected UriInfo uriInfo;

    @Context
    protected HttpHeaders headers;

    @Context
    protected Request request;

    protected BaseLdpResource(final String baseUrl, final ResourceService resourceService,
            final SerializationService serializationService) {
        this.baseUrl = baseUrl;
        this.resourceService = resourceService;
        this.serializationService = serializationService;
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
    public Response getResource(@PathParam("path") final String path, @QueryParam("version") final Version version,
            @QueryParam("timemap") final Boolean timemap,
            @HeaderParam("Accept-Datetime") final AcceptDatetime datetime,
            @HeaderParam("Prefer") final Prefer prefer, @HeaderParam("Want-Digest") final WantDigest digest,
            @HeaderParam("Range") final Range range) {
        if (path.endsWith("/")) {
            return redirectWithoutSlash(path);
        }

        final String identifier = ofNullable(baseUrl).orElseGet(() -> uriInfo.getBaseUri().toString()) + path;
        final Optional<RDFSyntax> syntax = getRdfSyntax(headers.getAcceptableMediaTypes());

        if (nonNull(version)) {
            LOGGER.info("Getting versioned resource: {}", version.toString());
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), version.getInstant())
                    .map(getRepresentation(identifier, syntax, prefer, digest, range))
                    .orElse(status(NOT_FOUND)).build();

        } else if (nonNull(timemap) && timemap) {
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path)).map(MementoResource::new)
                .map(res -> res.getTimeMapBuilder(identifier, syntax, serializationService))
                .orElse(status(NOT_FOUND)).build();

        } else if (nonNull(datetime)) {
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), datetime.getInstant())
                .map(MementoResource::new).map(res -> res.getTimeGateBuilder(identifier, datetime.getInstant()))
                .orElse(status(NOT_FOUND)).build();
        }

        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path))
                .map(getRepresentation(identifier, syntax, prefer, digest, range)).orElse(status(NOT_FOUND)).build();
    }

    protected abstract Function<Resource, ResponseBuilder> getRepresentation(final String identifier,
            final Optional<RDFSyntax> syntax, final Prefer prefer, final WantDigest digest,
            final Range range);

    protected static Function<Quad, Quad> unskolemize(final ResourceService svc, final String baseUrl) {
        return quad -> rdf.createQuad(quad.getGraphName().orElse(Trellis.PreferUserManaged),
                    (BlankNodeOrIRI) toExternalIri(svc.unskolemize(quad.getSubject()), baseUrl),
                    quad.getPredicate(), toExternalIri(svc.unskolemize(quad.getObject()), baseUrl));
    }

    protected Response.ResponseBuilder evaluateCache(final Instant modified, final EntityTag etag) {
        try {
            return request.evaluatePreconditions(from(modified), etag);
        } catch (final Exception ex) {
            LOGGER.warn("Ignoring cache-related headers: {}", ex.getMessage());
        }
        return null;
    }

    private Response redirectWithoutSlash(final String path) {
        return Response.seeOther(fromUri(stripSlash(path)).build()).build();
    }

    private static String stripSlash(final String path) {
        return path.endsWith("/") ? stripSlash(path.substring(0, path.length() - 1)) : path;
    }
}
