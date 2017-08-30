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
import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.ofInstant;
import static java.time.ZonedDateTime.parse;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.HEAD;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.core.HttpHeaders.ALLOW;
import static javax.ws.rs.core.HttpHeaders.VARY;
import static javax.ws.rs.core.Response.Status.FOUND;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.APPLICATION_LINK_FORMAT;
import static org.trellisldp.http.impl.RdfUtils.getProfile;
import static org.trellisldp.http.impl.RdfUtils.getSyntax;
import static org.trellisldp.vocabulary.JSONLD.expanded;
import static org.trellisldp.vocabulary.LDP.RDFSource;
import static org.trellisldp.vocabulary.LDP.Resource;
import static org.trellisldp.vocabulary.PROV.atTime;
import static org.trellisldp.vocabulary.PROV.endedAtTime;
import static org.trellisldp.vocabulary.PROV.startedAtTime;
import static org.trellisldp.vocabulary.Trellis.PreferUserManaged;
import static org.trellisldp.vocabulary.XSD.dateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFSyntax;

import org.trellisldp.api.Resource;
import org.trellisldp.api.VersionRange;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.spi.IOService;

/**
 * @author acoburn
 */
public final class MementoResource {

    private static final RDF rdf = ServiceLoader.load(RDF.class).iterator().next();

    private static final String ORIGINAL = "original";

    private static final String TIMEGATE = "timegate";

    private static final String TIMEMAP = "timemap";

    private static final String MEMENTO = "memento";

    private static final String FROM = "from";

    private static final String UNTIL = "until";

    private static final String DATETIME = "datetime";

    private final Resource resource;

    /**
     * Wrap a resource in some Memento-specific response builders
     * @param resource the resource
     */
    public MementoResource(final Resource resource) {
        this.resource = resource;
    }

    /**
     * Create a response builder for a TimeMap response
     * @param partitions the partitions
     * @param req the LDP request
     * @param serializer the serializer to use
     * @return a response builder object
     */
    public Response.ResponseBuilder getTimeMapBuilder(final Map<String, String> partitions,
            final LdpRequest req, final IOService serializer) {

        final List<MediaType> acceptableTypes = req.getHeaders().getAcceptableMediaTypes();
        final String identifier = req.getBaseUrl(partitions) + req.getPartition() + req.getPath();
        final Response.ResponseBuilder builder = Response.ok().link(identifier, ORIGINAL + " " + TIMEGATE);
        final List<Link> links = getMementoLinks(identifier, resource.getMementos()).collect(toList());
        builder.links(links.toArray(new Link[0]))
               .header(ALLOW, join(",", GET, HEAD, OPTIONS));
        builder.link(Resource.getIRIString(), "type");
        builder.link(RDFSource.getIRIString(), "type");
        final Optional<RDFSyntax> syntax = getSyntax(acceptableTypes, of(APPLICATION_LINK_FORMAT));
        if (syntax.isPresent()) {
            final IRI profile = getProfile(acceptableTypes, syntax.get());
            builder.type(syntax.get().mediaType);
            builder.entity(ResourceStreamer.quadStreamer(serializer, links.stream().flatMap(linkToQuads),
                        syntax.get(), ofNullable(profile).orElse(expanded)));
        } else {
            builder.type(APPLICATION_LINK_FORMAT);
            builder.entity(links.stream().map(Link::toString).collect(joining(",\n")) + "\n");
        }
        return builder;
    }

    /**
     * Create a response builder for a TimeGate response
     * @param partitions the partitions
     * @param req the LDP request
     * @return a response builder object
     */
    public Response.ResponseBuilder getTimeGateBuilder(final Map<String, String> partitions, final LdpRequest req) {
        final String identifier = req.getBaseUrl(partitions) + req.getPartition() + req.getPath();
        return Response.status(FOUND)
            .location(fromUri(identifier + "?version=" + req.getDatetime().getInstant().toEpochMilli()).build())
            .link(identifier, ORIGINAL + " " + TIMEGATE)
            .links(getMementoLinks(identifier, resource.getMementos()).toArray(Link[]::new))
            .header(VARY, ACCEPT_DATETIME);
    }

    /**
     * Retrieve all of the Memento-related link headers given a stream of VersionRange objects
     * @param identifier the public identifier for the resource
     * @param mementos a stream of memento values
     * @return a stream of link headers
     */
    public static Stream<Link> getMementoLinks(final String identifier, final List<VersionRange> mementos) {
        return concat(getTimeMap(identifier, mementos.stream()), mementos.stream().map(mementoToLink(identifier)));
    }

    private static final Function<Link, Stream<Quad>> linkToQuads = link -> {
        final IRI iri = rdf.createIRI(link.getUri().toString());
        final List<Quad> buffer = new ArrayList<>();
        if (link.getParams().containsKey(FROM)) {
            buffer.add(rdf.createQuad(PreferUserManaged, iri, startedAtTime, rdf.createLiteral(parse(
                                link.getParams().get(FROM), RFC_1123_DATE_TIME).toString(), dateTime)));
        }
        if (link.getParams().containsKey(UNTIL)) {
            buffer.add(rdf.createQuad(PreferUserManaged, iri, endedAtTime, rdf.createLiteral(parse(
                                link.getParams().get(UNTIL), RFC_1123_DATE_TIME).toString(), dateTime)));
        }
        if (MEMENTO.equals(link.getRel()) && link.getParams().containsKey(DATETIME)) {
            buffer.add(rdf.createQuad(PreferUserManaged, iri, atTime, rdf.createLiteral(parse(
                                link.getParams().get(DATETIME), RFC_1123_DATE_TIME).toString(), dateTime)));
        }
        return buffer.stream();
    };

    private static Stream<Link> getTimeMap(final String identifier, final Stream<VersionRange> mementos) {
        return mementos.reduce((acc, x) -> new VersionRange(acc.getFrom(), x.getUntil()))
                .map(x -> Link.fromUri(identifier + "?ext=timemap").rel(TIMEMAP)
                        .type(APPLICATION_LINK_FORMAT)
                        .param(FROM, ofInstant(x.getFrom(), UTC).format(RFC_1123_DATE_TIME))
                        .param(UNTIL, ofInstant(x.getUntil(), UTC).format(RFC_1123_DATE_TIME)).build())
                // TODO use Optional::stream with JDK9
                .map(Stream::of).orElseGet(Stream::empty);
    }

    private static Function<VersionRange, Link> mementoToLink(final String identifier) {
        return range ->
            Link.fromUri(identifier + "?version=" + range.getFrom().toEpochMilli()).rel(MEMENTO)
                .param(DATETIME, ofInstant(range.getFrom(), UTC).format(RFC_1123_DATE_TIME)).build();
    }
}
