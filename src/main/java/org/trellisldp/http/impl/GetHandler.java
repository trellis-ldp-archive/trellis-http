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
import static java.util.Collections.singletonList;
import static java.util.Date.from;
import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.of;
import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.HEAD;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;
import static javax.ws.rs.core.HttpHeaders.ALLOW;
import static javax.ws.rs.core.HttpHeaders.VARY;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.ok;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.api.RDFUtils.ldpResourceTypes;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_PATCH;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_POST;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_RANGES;
import static org.trellisldp.http.domain.HttpConstants.ACL;
import static org.trellisldp.http.domain.HttpConstants.DIGEST;
import static org.trellisldp.http.domain.HttpConstants.MEMENTO_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.PATCH;
import static org.trellisldp.http.domain.HttpConstants.PREFER;
import static org.trellisldp.http.domain.HttpConstants.PREFERENCE_APPLIED;
import static org.trellisldp.http.domain.HttpConstants.RANGE;
import static org.trellisldp.http.domain.HttpConstants.WANT_DIGEST;
import static org.trellisldp.http.domain.Prefer.PREFER_MINIMAL;
import static org.trellisldp.http.domain.Prefer.PREFER_REPRESENTATION;
import static org.trellisldp.http.domain.Prefer.PREFER_RETURN;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_SPARQL_UPDATE;
import static org.trellisldp.http.domain.RdfMediaType.MEDIA_TYPES;
import static org.trellisldp.http.impl.RdfUtils.filterWithPrefer;
import static org.trellisldp.http.impl.RdfUtils.getDefaultProfile;
import static org.trellisldp.http.impl.RdfUtils.getProfile;
import static org.trellisldp.http.impl.RdfUtils.getSyntax;
import static org.trellisldp.http.impl.RdfUtils.unskolemizeQuads;
import static org.trellisldp.vocabulary.OA.annotationService;
import static org.trellisldp.vocabulary.Trellis.PreferAccessControl;
import static org.trellisldp.vocabulary.Trellis.PreferUserManaged;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDFSyntax;
import org.slf4j.Logger;

import org.trellisldp.api.Binary;
import org.trellisldp.api.BinaryService;
import org.trellisldp.api.IOService;
import org.trellisldp.api.Resource;
import org.trellisldp.api.ResourceService;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.http.domain.WantDigest;
import org.trellisldp.vocabulary.LDP;

/**
 * The GET response builder
 *
 * @author acoburn
 */
public class GetHandler extends BaseLdpHandler {

    private static final Logger LOGGER = getLogger(GetHandler.class);

    private final IOService ioService;
    private final BinaryService binaryService;

    /**
     * A GET response builder
     * @param partitions the partitions
     * @param req the LDP request
     * @param resourceService the resource service
     * @param ioService the serialization service
     * @param binaryService the binary service
     */
    public GetHandler(final Map<String, String> partitions, final LdpRequest req,
            final ResourceService resourceService, final IOService ioService,
            final BinaryService binaryService) {
        super(partitions, req, resourceService);
        this.ioService = ioService;
        this.binaryService = binaryService;
    }

    /**
     * Build the representation for the given resource
     * @param res the resource
     * @return the response builder
     */
    public ResponseBuilder getRepresentation(final Resource res) {
        final String identifier = req.getBaseUrl(partitions) + req.getPartition() + req.getPath();

        // Check if this is already deleted
        checkDeleted(res, identifier);

        LOGGER.debug("Acceptable media types: {}", req.getHeaders().getAcceptableMediaTypes());
        final Optional<RDFSyntax> syntax = getSyntax(req.getHeaders().getAcceptableMediaTypes(), res.getBinary()
                .map(b -> b.getMimeType().orElse(APPLICATION_OCTET_STREAM)));

        if (ACL.equals(req.getExt()) && !res.hasAcl()) {
            throw new NotFoundException();
        }

        final ResponseBuilder builder = basicGetResponseBuilder(res, syntax);

        // Add NonRDFSource-related "describe*" link headers
        res.getBinary().ifPresent(ds -> {
            if (syntax.isPresent()) {
                builder.link(identifier + "#description", "canonical").link(identifier, "describes");
            } else {
                builder.link(identifier, "canonical").link(identifier + "#description", "describedby")
                    .type(ds.getMimeType().orElse(APPLICATION_OCTET_STREAM));
            }
        });

        // Only show memento links for the user-managed graph (not ACL)
        if (!ACL.equals(req.getExt())) {
            builder.link(identifier, "original timegate")
                .links(MementoResource.getMementoLinks(identifier, res.getMementos()).toArray(Link[]::new));
        }

        // NonRDFSources responses (strong ETags, etc)
        if (res.getBinary().isPresent() && !syntax.isPresent()) {
            return getLdpNr(identifier, res, builder);
        }

        // RDFSource responses (weak ETags, etc)
        final RDFSyntax s = syntax.orElse(TURTLE);
        final IRI profile = getProfile(req.getHeaders().getAcceptableMediaTypes(), s);
        return getLdpRs(identifier, res, builder, s, profile);
    }

    private ResponseBuilder getLdpRs(final String identifier, final Resource res, final ResponseBuilder builder,
            final RDFSyntax syntax, final IRI profile) {

        // Check for a cache hit
        final EntityTag etag = new EntityTag(md5Hex(res.getModified() + identifier), true);
        checkCache(req.getRequest(), res.getModified(), etag);

        builder.tag(etag);
        if (res.isMemento()) {
            builder.header(ALLOW, join(",", GET, HEAD, OPTIONS));
        } else if (ACL.equals(req.getExt())) {
            builder.header(ALLOW, join(",", GET, HEAD, OPTIONS, PATCH));
        } else if (res.getInteractionModel().equals(LDP.RDFSource)) {
            builder.header(ALLOW, join(",", GET, HEAD, OPTIONS, PATCH, PUT, DELETE));
        } else {
            builder.header(ALLOW, join(",", GET, HEAD, OPTIONS, PATCH, PUT, DELETE, POST));
        }

        final Prefer prefer = ACL.equals(req.getExt()) ?
            new Prefer(PREFER_REPRESENTATION, singletonList(PreferAccessControl.getIRIString()),
                    of(PreferUserManaged, LDP.PreferContainment, LDP.PreferMembership).map(IRI::getIRIString)
                        .collect(toList()), null, null, null) : req.getPrefer();

        ofNullable(prefer).ifPresent(p -> builder.header(PREFERENCE_APPLIED, PREFER_RETURN + "=" + p.getPreference()
                    .orElse(PREFER_REPRESENTATION)));


        if (ofNullable(prefer).flatMap(Prefer::getPreference).filter(PREFER_MINIMAL::equals).isPresent()) {
            return builder.status(NO_CONTENT);
        }

        // Short circuit HEAD requests
        if (HEAD.equals(req.getRequest().getMethod())) {
            return builder;
        }

        // Stream the rdf content
        final StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(final OutputStream out) throws IOException {
                try (final Stream<? extends Quad> stream = res.stream()) {
                    ioService.write(stream.filter(filterWithPrefer(prefer))
                        .map(unskolemizeQuads(resourceService)).map(Quad::asTriple), out,
                        syntax, ofNullable(profile).orElseGet(() -> getDefaultProfile(syntax, identifier)));
                }
            }
        };
        return builder.entity(stream);
    }

    private ResponseBuilder getLdpNr(final String identifier, final Resource res, final ResponseBuilder builder) {

        final Instant mod = res.getBinary().map(Binary::getModified).orElseThrow(() ->
                new WebApplicationException("Could not access binary metadata for " + res.getIdentifier()));
        final EntityTag etag = new EntityTag(md5Hex(mod + identifier + "BINARY"));
        checkCache(req.getRequest(), mod, etag);

        // Set last-modified to be the binary's last-modified value
        builder.lastModified(from(mod));

        final IRI dsid = res.getBinary().map(Binary::getIdentifier).orElseThrow(() ->
                new WebApplicationException("Could not access binary metadata for " + res.getIdentifier()));

        builder.header(VARY, RANGE).header(VARY, WANT_DIGEST).header(ACCEPT_RANGES, "bytes").tag(etag);

        if (res.isMemento()) {
            builder.header(ALLOW, join(",", GET, HEAD, OPTIONS));
        } else {
            builder.header(ALLOW, join(",", GET, HEAD, OPTIONS, PUT, DELETE));
        }

        // Add instance digests, if Requested and supported
        ofNullable(req.getWantDigest()).map(WantDigest::getAlgorithms).ifPresent(algs ->
                algs.stream().filter(binaryService.supportedAlgorithms()::contains).findFirst().ifPresent(alg ->
                    getBinaryDigest(dsid, alg).ifPresent(digest -> builder.header(DIGEST, digest))));

        // Stream the binary content
        final StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(final OutputStream out) throws IOException {
                // TODO -- with JDK 9 use InputStream::transferTo instead of IOUtils::copy
                try (final InputStream binary = binaryService.getContent(req.getPartition(), dsid).orElseThrow(() ->
                        new IOException("Could not retrieve content from " + dsid))) {
                    if (isNull(req.getRange())) {
                        IOUtils.copy(binary, out);
                    } else {
                        // Range Requests
                        final long skipped = binary.skip(req.getRange().getFrom());
                        if (skipped < req.getRange().getFrom()) {
                            LOGGER.warn("Trying to skip more data available in the input stream! {}, {}",
                                    skipped, req.getRange().getFrom());
                        }
                        try (final InputStream sliced = new BoundedInputStream(binary,
                                    req.getRange().getTo() - req.getRange().getFrom())) {
                            IOUtils.copy(sliced, out);
                        }
                    }
                } catch (final IOException ex) {
                    throw new WebApplicationException("Error processing binary content: " +
                            ex.getMessage());
                }
            }
        };

        return builder.entity(stream);
    }

    private Optional<String> getBinaryDigest(final IRI dsid, final String algorithm) {
        final Optional<InputStream> b = binaryService.getContent(req.getPartition(), dsid);
        try (final InputStream is = b.orElseThrow(() -> new WebApplicationException("Couldn't fetch binary content"))) {
            return binaryService.digest(algorithm, is);
        } catch (final IOException ex) {
            LOGGER.error("Error computing digest on content: {}", ex.getMessage());
            throw new WebApplicationException("Error handling binary content: " + ex.getMessage());
        }
    }

    private ResponseBuilder basicGetResponseBuilder(final Resource res, final Optional<RDFSyntax> syntax) {
        final ResponseBuilder builder = ok();

        // Standard HTTP Headers
        builder.lastModified(from(res.getModified()));

        final IRI model;

        if (isNull(req.getExt())) {
            syntax.ifPresent(s -> {
                builder.header(VARY, PREFER);
                builder.type(s.mediaType);
            });

            model = res.getBinary().isPresent() && syntax.isPresent() ? LDP.RDFSource : res.getInteractionModel();
            // Link headers from User data
            res.getTypes().forEach(type -> builder.link(type.getIRIString(), "type"));
            res.getInbox().map(IRI::getIRIString).ifPresent(inbox -> builder.link(inbox, "inbox"));
            res.getAnnotationService().map(IRI::getIRIString).ifPresent(svc ->
                    builder.link(svc, annotationService.getIRIString()));
        } else {
            model = LDP.RDFSource;
        }

        // Add LDP-required headers
        ldpResourceTypes(model).forEach(type -> {
            builder.link(type.getIRIString(), "type");
            // Mementos don't accept POST or PATCH
            if (LDP.Container.equals(type) && !res.isMemento()) {
                builder.header(ACCEPT_POST, MEDIA_TYPES.stream()
                        .map(mt -> mt.getType() + "/" + mt.getSubtype())
                        // text/html is excluded
                        .filter(mt -> !TEXT_HTML.equals(mt)).collect(joining(",")));
            } else if (LDP.RDFSource.equals(type) && !res.isMemento()) {
                builder.header(ACCEPT_PATCH, APPLICATION_SPARQL_UPDATE);
            }
        });

        // Memento-related headers
        if (res.isMemento()) {
            builder.header(MEMENTO_DATETIME, from(res.getModified()));
        } else {
            builder.header(VARY, ACCEPT_DATETIME);
        }

        return builder;
    }
}
