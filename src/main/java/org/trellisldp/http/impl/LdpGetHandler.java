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

import static java.lang.String.join;
import static java.util.Date.from;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.joining;
import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.HEAD;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;
import static javax.ws.rs.core.HttpHeaders.ALLOW;
import static javax.ws.rs.core.HttpHeaders.VARY;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.rdf.api.RDFSyntax.RDFA_HTML;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_PATCH;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_POST;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_RANGES;
import static org.trellisldp.http.domain.HttpConstants.DIGEST;
import static org.trellisldp.http.domain.HttpConstants.MEMENTO_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.NOT_ACCEPTABLE_ERROR;
import static org.trellisldp.http.domain.HttpConstants.PREFER;
import static org.trellisldp.http.domain.HttpConstants.PREFERENCE_APPLIED;
import static org.trellisldp.http.domain.HttpConstants.RANGE;
import static org.trellisldp.http.domain.HttpConstants.WANT_DIGEST;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_SPARQL_UPDATE;
import static org.trellisldp.http.domain.RdfMediaType.VARIANTS;
import static org.trellisldp.http.impl.HttpUtils.checkCache;
import static org.trellisldp.http.impl.RdfUtils.filterWithPrefer;
import static org.trellisldp.http.impl.RdfUtils.unskolemizeQuads;
import static org.trellisldp.spi.ConstraintService.ldpResourceTypes;
import static org.trellisldp.spi.RDFUtils.getInstance;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Variant;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.slf4j.Logger;

import org.trellisldp.api.Datastream;
import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.http.domain.Range;
import org.trellisldp.http.domain.WantDigest;
import org.trellisldp.spi.DatastreamService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.SerializationService;
import org.trellisldp.vocabulary.JSONLD;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.OA;
import org.trellisldp.vocabulary.Trellis;

/**
 * The GET response builder
 *
 * @author acoburn
 */
public class LdpGetHandler extends BaseLdpHandler {

    private static final int cacheAge = 86400;

    private static final Logger LOGGER = getLogger(LdpGetHandler.class);

    private final SerializationService serializationService;
    private final DatastreamService datastreamService;
    private final Request request;
    private final LdpRequest ldpRequest;

    /**
     * A GET response builder
     * @param resourceService the resource service
     * @param serializationService the serialization service
     * @param datastreamService the datastream service
     * @param request the HTTP request
     * @param ldpRequest the LDP-related header values
     */
    public LdpGetHandler(final ResourceService resourceService, final SerializationService serializationService,
            final DatastreamService datastreamService, final Request request, final LdpRequest ldpRequest) {
        super(resourceService);
        this.serializationService = serializationService;
        this.datastreamService = datastreamService;
        this.request = request;
        this.ldpRequest = ldpRequest;
    }

    /**
     * Build the representation for the given resource
     * @param res the resource
     * @return the response builder
     */
    public ResponseBuilder getRepresentation(final Resource res) {
        final String identifier = ldpRequest.getBaseUrl() + ldpRequest.getPath();
        if (res.getTypes().anyMatch(Trellis.DeletedResource::equals)) {
            return status(GONE).links(MementoResource.getMementoLinks(identifier, res.getMementos())
                    .toArray(Link[]::new));
        }

        // TODO add acl header, if in effect
        final ResponseBuilder builder = basicGetResponseBuilder(res, ldpRequest.getSyntax());

        // Add NonRDFSource-related "describe*" link headers
        res.getDatastream().ifPresent(ds -> {
            if (ldpRequest.getSyntax().isPresent()) {
                builder.link(identifier + "#description", "canonical").link(identifier, "describes");
            } else {
                builder.link(identifier, "canonical").link(identifier + "#description", "describedby")
                    .type(ds.getMimeType().orElse(APPLICATION_OCTET_STREAM));
            }
        });

        builder.link(identifier, "original timegate")
            .links(MementoResource.getMementoLinks(identifier, res.getMementos()).toArray(Link[]::new));

        // NonRDFSources responses (strong ETags, etc)
        if (res.getDatastream().isPresent() && !ldpRequest.getSyntax().isPresent()) {
            return getLdpNr(identifier, res, builder);

        // RDFSource responses (weak ETags, etc)
        } else if (ldpRequest.getSyntax().isPresent()) {
            return getLdpRs(identifier, res, builder);
        }
        // Other responses (typically, a request for application/link-format on an LDPR)
        return status(NOT_ACCEPTABLE).type(APPLICATION_JSON).entity(NOT_ACCEPTABLE_ERROR);
    }

    private ResponseBuilder getLdpRs(final String identifier, final Resource res, final ResponseBuilder builder) {
        final EntityTag etag = new EntityTag(md5Hex(res.getModified() + identifier), true);
        final ResponseBuilder cacheBuilder = checkCache(request, res.getModified(), etag);
        if (nonNull(cacheBuilder)) {
            return cacheBuilder;
        }
        builder.tag(etag);
        if (res.getInteractionModel().equals(LDP.RDFSource)) {
            builder.header(ALLOW, join(",", GET, HEAD, OPTIONS, PUT, DELETE, "PATCH"));
        } else {
            builder.header(ALLOW, join(",", GET, HEAD, OPTIONS, PUT, POST, DELETE, "PATCH"));
        }
        ldpRequest.getPrefer().ifPresent(p ->
                builder.header(PREFERENCE_APPLIED, "return=" + p.getPreference().orElse("representation")));

        if (ldpRequest.getPrefer().flatMap(Prefer::getPreference).filter("minimal"::equals).isPresent()) {
            return builder.status(NO_CONTENT);
        } else {
            final RDFSyntax syntax = ldpRequest.getSyntax().get();
            return builder.entity(ResourceStreamer.quadStreamer(serializationService,
                        res.stream().filter(filterWithPrefer(ldpRequest.getPrefer().orElse(null)))
                        .map(unskolemizeQuads(resourceService, ldpRequest.getBaseUrl())),
                        syntax, ldpRequest.getProfile().orElseGet(() ->
                            RDFA_HTML.equals(syntax) ? getInstance().createIRI(identifier) : JSONLD.expanded)));
        }
    }

    private ResponseBuilder getLdpNr(final String identifier, final Resource res, final ResponseBuilder builder) {
        final Instant mod = res.getDatastream().map(Datastream::getModified).get();
        final EntityTag etag = new EntityTag(md5Hex(mod + identifier));
        final ResponseBuilder cacheBuilder = checkCache(request, mod, etag);
        if (nonNull(cacheBuilder)) {
            return cacheBuilder;
        }

        final IRI dsid = res.getDatastream().map(Datastream::getIdentifier).get();
        final InputStream datastream = datastreamService.getContent(dsid).orElseThrow(() ->
                new WebApplicationException("Could not load datastream resolver for " + dsid.getIRIString()));
        builder.header(VARY, RANGE).header(VARY, WANT_DIGEST).header(ACCEPT_RANGES, "bytes")
            .header(ALLOW, join(",", GET, HEAD, OPTIONS, PUT, DELETE)).tag(etag);

        // Add instance digests, if ldpRequested and supported
        ldpRequest.getDigest().map(WantDigest::getAlgorithms).ifPresent(algs ->
                algs.stream().filter(datastreamService.supportedAlgorithms()::contains).findFirst()
                .ifPresent(alg -> datastreamService.getContent(dsid)
                    .map(is -> datastreamService.hexDigest(alg, is))
                    .ifPresent(d -> builder.header(DIGEST, d))));

        // Range ldpRequests
        if (ldpRequest.getRange().isPresent()) {
            final Range range = ldpRequest.getRange().get();
            try {
                final long skipped = datastream.skip(range.getFrom());
                if (skipped < range.getFrom()) {
                    LOGGER.warn("Trying to skip more data available in the input stream! {}, {}",
                            skipped, range.getFrom());
                }
            } catch (final IOException ex) {
                LOGGER.error("Error seeking through datastream: {}", ex.getMessage());
                return status(BAD_REQUEST).entity(ex.getMessage());
            }
            return builder.entity(new BoundedInputStream(datastream, range.getTo() - range.getFrom()));
        }
        return builder.entity(datastream);
    }

    private static ResponseBuilder basicGetResponseBuilder(final Resource res, final Optional<RDFSyntax> syntax) {
        final ResponseBuilder builder = ok();

        final CacheControl cc = new CacheControl();
        cc.setMaxAge(cacheAge);

        // Standard HTTP Headers
        builder.lastModified(from(res.getModified())).variants(VARIANTS).header(VARY, PREFER);
        if (syntax.isPresent()) {
            builder.type(syntax.get().mediaType);
        }

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

        return builder.cacheControl(cc);
    }
}