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
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
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

import org.trellisldp.api.Blob;
import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.http.domain.Range;
import org.trellisldp.http.domain.WantDigest;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.IOService;
import org.trellisldp.vocabulary.JSONLD;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.OA;

/**
 * The GET response builder
 *
 * @author acoburn
 */
public class LdpGetHandler extends BaseLdpHandler {

    private static final int cacheAge = 86400;

    private static final Logger LOGGER = getLogger(LdpGetHandler.class);

    private final IOService ioService;
    private final BinaryService binaryService;
    private final Request request;

    private Range range = null;
    private WantDigest digest = null;

    /**
     * A GET response builder
     * @param resourceService the resource service
     * @param ioService the serialization service
     * @param binaryService the binary service
     * @param request the HTTP request
     */
    public LdpGetHandler(final ResourceService resourceService, final IOService ioService,
            final BinaryService binaryService, final Request request) {
        super(resourceService);
        this.ioService = ioService;
        this.binaryService = binaryService;
        this.request = request;
    }

    /**
     * Set the WantDigest value
     * @param digest the digest
     */
    public void setWantDigest(final WantDigest digest) {
        this.digest = digest;
    }

    /**
     * Set the Range value
     * @param range the range
     */
    public void setRange(final Range range) {
        this.range = range;
    }

    /**
     * Build the representation for the given resource
     * @param res the resource
     * @return the response builder
     */
    public ResponseBuilder getRepresentation(final Resource res) {
        final String identifier = baseUrl + path;

        // Check if this is already deleted
        final ResponseBuilder deleted = checkDeleted(res, identifier);
        if (nonNull(deleted)) {
            return deleted;
        }

        // TODO add acl header, if in effect
        final ResponseBuilder builder = basicGetResponseBuilder(res, ofNullable(syntax));

        // Add NonRDFSource-related "describe*" link headers
        res.getBlob().ifPresent(ds -> {
            if (nonNull(syntax)) {
                builder.link(identifier + "#description", "canonical").link(identifier, "describes");
            } else {
                builder.link(identifier, "canonical").link(identifier + "#description", "describedby")
                    .type(ds.getMimeType().orElse(APPLICATION_OCTET_STREAM));
            }
        });

        builder.link(identifier, "original timegate")
            .links(MementoResource.getMementoLinks(identifier, res.getMementos()).toArray(Link[]::new));

        // NonRDFSources responses (strong ETags, etc)
        if (res.getBlob().isPresent() && isNull(syntax)) {
            return getLdpNr(identifier, res, builder);

        // RDFSource responses (weak ETags, etc)
        } else if (nonNull(syntax)) {
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
        ofNullable(prefer).ifPresent(p ->
                builder.header(PREFERENCE_APPLIED, "return=" + p.getPreference().orElse("representation")));

        if (ofNullable(prefer).flatMap(Prefer::getPreference).filter("minimal"::equals).isPresent()) {
            return builder.status(NO_CONTENT);
        } else {
            return builder.entity(ResourceStreamer.quadStreamer(ioService,
                        res.stream().filter(filterWithPrefer(prefer))
                        .map(unskolemizeQuads(resourceService, baseUrl)),
                        syntax, ofNullable(profile).orElseGet(() ->
                            RDFA_HTML.equals(syntax) ? getInstance().createIRI(identifier) : JSONLD.expanded)));
        }
    }

    private ResponseBuilder getLdpNr(final String identifier, final Resource res, final ResponseBuilder builder) {
        final Instant mod = res.getBlob().map(Blob::getModified).get();
        final EntityTag etag = new EntityTag(md5Hex(mod + identifier));
        final ResponseBuilder cacheBuilder = checkCache(request, mod, etag);
        if (nonNull(cacheBuilder)) {
            return cacheBuilder;
        }

        final IRI dsid = res.getBlob().map(Blob::getIdentifier).get();
        final InputStream binary = binaryService.getContent(dsid).orElseThrow(() ->
                new WebApplicationException("Could not load binary resolver for " + dsid.getIRIString()));
        builder.header(VARY, RANGE).header(VARY, WANT_DIGEST).header(ACCEPT_RANGES, "bytes")
            .header(ALLOW, join(",", GET, HEAD, OPTIONS, PUT, DELETE)).tag(etag);

        // Add instance digests, if Requested and supported
        ofNullable(digest).map(WantDigest::getAlgorithms).ifPresent(algs ->
                algs.stream().filter(binaryService.supportedAlgorithms()::contains).findFirst()
                .ifPresent(alg -> binaryService.getContent(dsid)
                    .map(is -> binaryService.hexDigest(alg, is))
                    .ifPresent(d -> builder.header(DIGEST, d))));

        // Range Requests
        if (nonNull(range)) {
            try {
                final long skipped = binary.skip(range.getFrom());
                if (skipped < range.getFrom()) {
                    LOGGER.warn("Trying to skip more data available in the input stream! {}, {}",
                            skipped, range.getFrom());
                }
            } catch (final IOException ex) {
                LOGGER.error("Error seeking through binary: {}", ex.getMessage());
                return status(BAD_REQUEST).entity(ex.getMessage());
            }
            return builder.entity(new BoundedInputStream(binary, range.getTo() - range.getFrom()));
        }
        return builder.entity(binary);
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
        final IRI model = res.getBlob().isPresent() && syntax.isPresent() ?
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
