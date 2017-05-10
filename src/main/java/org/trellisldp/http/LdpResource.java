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
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.rdf.api.RDFSyntax.RDFA_HTML;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.HttpConstants.ACCEPT_DATETIME;
import static org.trellisldp.http.HttpConstants.ACCEPT_PATCH;
import static org.trellisldp.http.HttpConstants.ACCEPT_POST;
import static org.trellisldp.http.HttpConstants.APPLICATION_LINK_FORMAT;
import static org.trellisldp.http.HttpConstants.MEMENTO_DATETIME;
import static org.trellisldp.http.HttpConstants.NOT_ACCEPTABLE_ERROR;
import static org.trellisldp.http.HttpConstants.PREFER;
import static org.trellisldp.http.HttpConstants.PREFERENCE_APPLIED;
import static org.trellisldp.http.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.HttpConstants.VARY;
import static org.trellisldp.http.RdfMediaType.APPLICATION_LD_JSON;
import static org.trellisldp.http.RdfMediaType.APPLICATION_N_TRIPLES;
import static org.trellisldp.http.RdfMediaType.APPLICATION_SPARQL_UPDATE;
import static org.trellisldp.http.RdfMediaType.TEXT_TURTLE;
import static org.trellisldp.http.RdfMediaType.VARIANTS;
import static org.trellisldp.spi.ConstraintService.ldpResourceTypes;

import com.codahale.metrics.annotation.Timed;

import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Variant;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.slf4j.Logger;

import org.trellisldp.api.Datastream;
import org.trellisldp.api.Resource;
import org.trellisldp.spi.DatastreamService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.SerializationService;
import org.trellisldp.vocabulary.JSONLD;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.OA;
import org.trellisldp.vocabulary.Trellis;


/**
 * @author acoburn
 */
@Path("{path: .+}")
@Produces({TEXT_TURTLE, APPLICATION_LD_JSON, APPLICATION_N_TRIPLES, APPLICATION_LINK_FORMAT, TEXT_HTML})
public class LdpResource extends BaseLdpResource {

    private static final Logger LOGGER = getLogger(LdpResource.class);

    private final String baseUrl;
    private final SerializationService serializationService;
    private final DatastreamService datastreamService;

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
        super(resourceService);
        this.baseUrl = baseUrl;
        this.serializationService = serializationService;
        this.datastreamService = datastreamService;
    }

    /**
     * Perform a GET operation on an LDP Resource
     * @param path the path
     * @return the response
     */
    @GET
    @Timed
    public Response getResource(@PathParam("path") final String path) {
        if (path.endsWith("/")) {
            return redirectWithoutSlash(path);
        }

        final String urlPrefix = ofNullable(baseUrl).orElseGet(() -> uriInfo.getBaseUri().toString());
        final String identifier = urlPrefix + path;
        final Optional<RDFSyntax> syntax = getRdfSyntax(headers.getAcceptableMediaTypes());
        final Optional<Instant> acceptDatetime = MementoResource.getAcceptDatetime(headers);
        final Optional<Instant> version = MementoResource.getVersionParam(uriInfo);
        final Optional<IRI> profile = getProfile(headers.getAcceptableMediaTypes());
        final Boolean timemap = MementoResource.getTimeMapParam(uriInfo);

        final Optional<Resource> resource;
        if (version.isPresent()) {
            LOGGER.info("Getting versioned resource: {}", version.get().toString());
            resource = resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), version.get());

        } else if (timemap) {
            // Short-circuit
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path)).map(MementoResource::new)
                .map(res -> res.getTimeMapBuilder(identifier, syntax, serializationService))
                .orElse(status(NOT_FOUND)).build();

        } else if (acceptDatetime.isPresent()) {
            // Short-circuit
            return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path), acceptDatetime.get())
                .map(MementoResource::new).map(res -> res.getTimeGateBuilder(identifier, acceptDatetime.get()))
                .orElse(status(NOT_FOUND)).build();

        } else {
            resource = resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path));
        }

        return resource.map(res -> {
            if (res.getTypes().anyMatch(Trellis.DeletedResource::equals)) {
                return status(GONE).links(MementoResource.getMementoLinks(identifier, res.getMementos())
                        .toArray(Link[]::new));
            }

            final Response.ResponseBuilder builder = ok();

            // Standard HTTP Headers
            builder.lastModified(from(res.getModified())).variants(VARIANTS).header(VARY, PREFER);
            syntax.map(s -> s.mediaType).ifPresent(builder::type);

            // Add LDP-required headers
            final IRI model = res.getDatastream().isPresent() && syntax.isPresent() ?
                    LDP.RDFSource : res.getInteractionModel();
            ldpResourceTypes(model).forEach(type -> {
                builder.link(type.getIRIString(), "type");
                // Mementos don't accept POST or PATCH
                if (LDP.Container.equals(type) && !res.isMemento()) {
                    builder.header(ACCEPT_POST, VARIANTS.stream().map(Variant::getMediaType)
                            .map(mt -> mt.getType() + "/" + mt.getSubtype()).collect(joining(",")));
                } else if (LDP.RDFSource.equals(type) && !res.isMemento()) {
                    builder.header(ACCEPT_PATCH, APPLICATION_SPARQL_UPDATE);
                }
            });

            // Add NonRDFSource-related "describe*" link headers
            res.getDatastream().ifPresent(ds -> {
                // TODO -- add digest support
                //final Optional<String> wantDigest = ofNullable(headers.getRequestHeaders().getFirst(WANT_DIGEST));
                if (syntax.isPresent()) {
                    // TODO make this identifier opaque
                    builder.link(identifier + "#description", "canonical").link(identifier, "describes");
                } else {
                    builder.link(identifier, "canonical").link(identifier + "#description", "describedby")
                        .type(ds.getMimeType().orElse(APPLICATION_OCTET_STREAM));
                }
            });

            // Link headers from User data
            res.getTypes().map(IRI::getIRIString).forEach(type -> builder.link(type, "type"));
            res.getInbox().map(IRI::getIRIString).ifPresent(inbox -> builder.link(inbox, "inbox"));
            res.getAnnotationService().map(IRI::getIRIString).ifPresent(svc ->
                    builder.link(svc, OA.annotationService.getIRIString()));

            // Memento-related headers
            if (res.isMemento()) {
                builder.header(MEMENTO_DATETIME, from(res.getModified()));
            } else {
                builder.header(VARY, ACCEPT_DATETIME);
            }
            builder.link(identifier, "original timegate")
                .links(MementoResource.getMementoLinks(identifier, res.getMementos()).toArray(Link[]::new));

            // NonRDFSources responses (strong ETags, etc)
            if (res.getDatastream().isPresent() && !syntax.isPresent()) {
                final IRI dsid = res.getDatastream().map(Datastream::getIdentifier).get();
                final InputStream datastream = datastreamService.getResolver(dsid).flatMap(svc -> svc.getContent(dsid))
                    .orElseThrow(() ->
                        new WebApplicationException("Could not load datastream resolver for " + dsid.getIRIString()));
                builder.tag(md5Hex(res.getDatastream().map(Datastream::getModified).get() + identifier))
                    .entity(datastream);

            // RDFSource responses (weak ETags, etc)
            } else if (syntax.isPresent()) {
                final Prefer prefer = new Prefer(ofNullable(headers.getRequestHeaders().getFirst(PREFER)).orElse(""));
                builder.header(PREFERENCE_APPLIED, "return=" + prefer.getPreference().orElse("representation"))
                    .tag(new EntityTag(md5Hex(res.getModified() + identifier + syntax.map(RDFSyntax::toString)
                                    .orElse("")), true));

                if (prefer.getPreference().filter("minimal"::equals).isPresent()) {
                    builder.status(NO_CONTENT);
                } else {
                    builder.entity(new ResourceStreamer(serializationService,
                                res.stream().filter(filterWithPrefer(prefer))
                                .map(unskolemize(resourceService, urlPrefix)),
                                syntax.get(), profile.orElseGet(() ->
                                    RDFA_HTML.equals(syntax.get()) ? rdf.createIRI(identifier) : JSONLD.expanded)));
                }

            // Other responses (typically, a request for application/link-format on an LDPR)
            } else {
                return status(NOT_ACCEPTABLE).type(APPLICATION_JSON).entity(NOT_ACCEPTABLE_ERROR);
            }

            // TODO add acl header, if in effect
            // TODO check cache control headers
            // TODO add support for instance digests
            // TODO add support for range requests

            return builder;
        }).orElse(status(NOT_FOUND)).build();
    }
}
