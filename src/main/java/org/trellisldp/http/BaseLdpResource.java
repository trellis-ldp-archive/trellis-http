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
import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.RdfUtils.getInstance;
import static org.trellisldp.http.RdfUtils.toExternalIri;

import java.time.Instant;
import java.util.function.Function;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.rdf.api.BlankNodeOrIRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.slf4j.Logger;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
class BaseLdpResource {

    private static final Logger LOGGER = getLogger(BaseLdpResource.class);

    protected static final RDF rdf = getInstance();

    protected final ResourceService resourceService;

    @Context
    protected UriInfo uriInfo;

    @Context
    protected HttpHeaders headers;

    @Context
    protected Request request;

    protected BaseLdpResource(final ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    protected Response redirectWithoutSlash(final String path) {
        return Response.seeOther(fromUri(stripSlash(path)).build()).build();
    }

    protected static Function<Quad, Quad> unskolemize(final ResourceService svc, final String baseUrl) {
        return quad -> rdf.createQuad(quad.getGraphName().orElse(Trellis.PreferUserManaged),
                    (BlankNodeOrIRI) toExternalIri(svc.unskolemize(quad.getSubject()), baseUrl),
                    quad.getPredicate(), toExternalIri(svc.unskolemize(quad.getObject()), baseUrl));
    }

    private static String stripSlash(final String path) {
        return path.endsWith("/") ? stripSlash(path.substring(0, path.length() - 1)) : path;
    }

    protected Response.ResponseBuilder evaluateCache(final Instant modified, final EntityTag etag) {
        try {
            return request.evaluatePreconditions(from(modified), etag);
        } catch (final Exception ex) {
            LOGGER.warn("Ignoring cache-related headers: {}", ex.getMessage());
        }
        return null;
    }
}
