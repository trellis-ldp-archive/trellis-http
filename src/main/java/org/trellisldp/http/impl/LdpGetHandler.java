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
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
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
import static org.trellisldp.http.domain.HttpConstants.ACL;
import static org.trellisldp.http.domain.HttpConstants.DIGEST;
import static org.trellisldp.http.domain.HttpConstants.MEMENTO_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.PATCH;
import static org.trellisldp.http.domain.HttpConstants.PREFER;
import static org.trellisldp.http.domain.HttpConstants.PREFERENCE_APPLIED;
import static org.trellisldp.http.domain.HttpConstants.RANGE;
import static org.trellisldp.http.domain.HttpConstants.UPLOADS;
import static org.trellisldp.http.domain.HttpConstants.WANT_DIGEST;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_SPARQL_UPDATE;
import static org.trellisldp.http.domain.RdfMediaType.VARIANTS;
import static org.trellisldp.http.impl.RdfUtils.filterWithPrefer;
import static org.trellisldp.http.impl.RdfUtils.getProfile;
import static org.trellisldp.http.impl.RdfUtils.getSyntax;
import static org.trellisldp.http.impl.RdfUtils.unskolemizeQuads;
import static org.trellisldp.spi.ConstraintService.ldpResourceTypes;
import static org.trellisldp.spi.RDFUtils.getInstance;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Variant;

import org.apache.commons.io.input.BoundedInputStream;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.slf4j.Logger;

import org.trellisldp.api.Binary;
import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.http.domain.WantDigest;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
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

    private static final Logger LOGGER = getLogger(LdpGetHandler.class);

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
    public LdpGetHandler(final Map<String, String> partitions, final LdpRequest req,
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
        final ResponseBuilder deleted = checkDeleted(res, identifier);
        if (nonNull(deleted)) {
            return deleted;
        }

        final Optional<RDFSyntax> syntax = getSyntax(req.getHeaders().getAcceptableMediaTypes(), res.getBinary()
                .map(b -> b.getMimeType().orElse(APPLICATION_OCTET_STREAM)));

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
        final IRI profile = getProfile(req.getHeaders().getAcceptableMediaTypes(), syntax.get());
        return getLdpRs(identifier, res, builder, syntax.get(), profile);
    }

    private ResponseBuilder getLdpRs(final String identifier, final Resource res, final ResponseBuilder builder,
            final RDFSyntax syntax, final IRI profile) {

        // Check for a cache hit
        final EntityTag etag = new EntityTag(md5Hex(res.getModified() + identifier), true);
        final ResponseBuilder cacheBuilder = checkCache(req.getRequest(), res.getModified(), etag);
        if (nonNull(cacheBuilder)) {
            return cacheBuilder;
        }

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
            new Prefer("return=representation; include=\"" + Trellis.PreferAccessControl.getIRIString() + "\"; " +
                    "omit=\"" + Trellis.PreferUserManaged.getIRIString() + " " +
                        LDP.PreferContainment.getIRIString() + " " +
                        LDP.PreferMembership.getIRIString() + "\"") : req.getPrefer();

        ofNullable(prefer).ifPresent(p ->
                builder.header(PREFERENCE_APPLIED, "return=" + p.getPreference().orElse("representation")));

        // Add upload service headers, if relevant
        if (!LDP.RDFSource.equals(res.getInteractionModel())) {
            binaryService.getResolverForPartition(req.getPartition())
                .map(BinaryService.Resolver::supportsMultipartUpload).ifPresent(x ->
                    builder.link(identifier + "?ext=" + UPLOADS, Trellis.multipartUploadService.getIRIString()));
        }

        if (ofNullable(prefer).flatMap(Prefer::getPreference).filter("minimal"::equals).isPresent()) {
            return builder.status(NO_CONTENT);
        } else {
            return builder.entity(ResourceStreamer.quadStreamer(ioService,
                        res.stream().filter(filterWithPrefer(prefer))
                        .map(unskolemizeQuads(resourceService, req.getBaseUrl(partitions))),
                        syntax, ofNullable(profile).orElseGet(() ->
                            RDFA_HTML.equals(syntax) ? getInstance().createIRI(identifier) : JSONLD.expanded)));
        }
    }

    private ResponseBuilder getLdpNr(final String identifier, final Resource res, final ResponseBuilder builder) {
        final Instant mod = res.getBinary().map(Binary::getModified).get();
        final EntityTag etag = new EntityTag(md5Hex(mod + identifier));
        final ResponseBuilder cacheBuilder = checkCache(req.getRequest(), mod, etag);
        if (nonNull(cacheBuilder)) {
            return cacheBuilder;
        }

        // Set last-modified to be the binary's last-modified value
        builder.lastModified(from(mod));

        final IRI dsid = res.getBinary().map(Binary::getIdentifier).get();
        final InputStream binary = binaryService.getContent(req.getPartition(), dsid).orElseThrow(() ->
                new WebApplicationException("Could not load binary resolver for " + dsid.getIRIString()));
        builder.header(VARY, RANGE).header(VARY, WANT_DIGEST).header(ACCEPT_RANGES, "bytes").tag(etag);

        if (res.isMemento()) {
            builder.header(ALLOW, join(",", GET, HEAD, OPTIONS));
        } else {
            builder.header(ALLOW, join(",", GET, HEAD, OPTIONS, PUT, DELETE));
        }

        // Add upload service headers, if relevant
        binaryService.getResolver(dsid).filter(BinaryService.Resolver::supportsMultipartUpload).ifPresent(x ->
                builder.link(identifier + "?ext=" + UPLOADS, Trellis.multipartUploadService.getIRIString()));

        // Add instance digests, if Requested and supported
        ofNullable(req.getWantDigest()).map(WantDigest::getAlgorithms).ifPresent(algs ->
                algs.stream().filter(binaryService.supportedAlgorithms()::contains).findFirst()
                .ifPresent(alg -> binaryService.getContent(req.getPartition(), dsid)
                    .flatMap(is -> binaryService.hexDigest(alg, is))
                    .ifPresent(d -> builder.header(DIGEST, d))));

        // Range Requests
        if (nonNull(req.getRange())) {
            try {
                final long skipped = binary.skip(req.getRange().getFrom());
                if (skipped < req.getRange().getFrom()) {
                    LOGGER.warn("Trying to skip more data available in the input stream! {}, {}",
                            skipped, req.getRange().getFrom());
                }
            } catch (final IOException ex) {
                LOGGER.error("Error seeking through binary: {}", ex.getMessage());
                return status(BAD_REQUEST).entity(ex.getMessage());
            }
            return builder.entity(new BoundedInputStream(binary, req.getRange().getTo() - req.getRange().getFrom()));
        }
        return builder.entity(binary);
    }

    private static ResponseBuilder basicGetResponseBuilder(final Resource res, final Optional<RDFSyntax> syntax) {
        final ResponseBuilder builder = ok();

        // Standard HTTP Headers
        builder.lastModified(from(res.getModified())).variants(VARIANTS);
        if (syntax.isPresent()) {
            builder.header(VARY, PREFER);
            builder.type(syntax.get().mediaType);
        }

        // Add LDP-required headers
        final IRI model = res.getBinary().isPresent() && syntax.isPresent() ?
                LDP.RDFSource : res.getInteractionModel();
        ldpResourceTypes(model).forEach(type -> {
            builder.link(type.getIRIString(), "type");
            // Mementos don't accept POST or PATCH
            if (LDP.Container.equals(type) && !res.isMemento()) {
                builder.header(ACCEPT_POST, VARIANTS.stream().map(Variant::getMediaType)
                        .map(mt -> mt.getType() + "/" + mt.getSubtype())
                        // text/html is excluded
                        .filter(mt -> !TEXT_HTML.equals(mt)).collect(joining(",")));
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

        return builder;
    }
}
