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

import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static org.trellisldp.http.HttpConstants.APPLICATION_LINK_FORMAT;
import static org.trellisldp.http.RdfMediaType.APPLICATION_LD_JSON;
import static org.trellisldp.http.RdfMediaType.APPLICATION_N_TRIPLES;
import static org.trellisldp.http.RdfMediaType.TEXT_TURTLE;
import static org.trellisldp.http.RdfUtils.getProfile;
import static org.trellisldp.http.RdfUtils.getRdfSyntax;

import com.codahale.metrics.annotation.Timed;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.trellisldp.spi.DatastreamService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.SerializationService;
import org.trellisldp.spi.Session;

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

        return LdpGetBuilder.builder(resourceService, serializationService, datastreamService)
            .withBaseUrl(ofNullable(baseUrl).orElseGet(() -> uriInfo.getBaseUri().toString()))
            .withSyntax(getRdfSyntax(headers.getAcceptableMediaTypes()))
            .withVersion(version).withTimemap(timemap).withPrefer(prefer)
            .withProfile(getProfile(headers.getAcceptableMediaTypes()))
            .withCacheEvaluator(cacheEvaluator).withDatetime(datetime)
            .withWantDigest(digest).withRange(range).build(path);
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

        return LdpPatchBuilder.builder(resourceService, serializationService)
            .withBaseUrl(ofNullable(baseUrl).orElseGet(() -> uriInfo.getBaseUri().toString()))
            .withSyntax(getRdfSyntax(headers.getAcceptableMediaTypes()))
            .withPrefer(prefer).withProfile(getProfile(headers.getAcceptableMediaTypes()))
            .withSession(session)
            .withCacheEvaluator(cacheEvaluator).withSparqlUpdate(body).build(path);
    }
}
