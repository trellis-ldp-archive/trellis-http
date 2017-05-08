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

import static org.trellisldp.http.Constants.ACCEPT_DATETIME;
import static org.trellisldp.http.Constants.APPLICATION_LINK_FORMAT;
import static org.trellisldp.http.Constants.VARY;
import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.ofInstant;
import static java.time.ZonedDateTime.parse;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.empty;
import static javax.ws.rs.core.Response.Status.FOUND;
import static javax.ws.rs.core.UriBuilder.fromUri;

import org.trellisldp.api.Resource;
import org.trellisldp.api.VersionRange;
import org.trellisldp.spi.SerializationService;
import org.trellisldp.vocabulary.PROV;
import org.trellisldp.vocabulary.Trellis;
import org.trellisldp.vocabulary.XSD;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFSyntax;

/**
 * @author acoburn
 */
final class MementoResource {

    private static final RDF rdf = ServiceLoader.load(RDF.class).iterator().next();

    private static final String ORIGINAL = "original";

    private static final String TIMEGATE = "timegate";

    private static final String TIMEMAP = "timemap";

    private static final String MEMENTO = "memento";

    private static final String FROM = "from";

    private static final String UNTIL = "until";

    private static final String DATETIME = "datetime";

    private static final String VERSION = "version";

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
     * @param identifier the public identifier for the resource
     * @param syntax the RDF syntax to use, if defined
     * @param serializer the serializer to use
     * @return a response builder object
     */
    public Response.ResponseBuilder getTimeMapBuilder(final String identifier, final Optional<RDFSyntax> syntax,
            final SerializationService serializer) {
        final Response.ResponseBuilder builder = Response.ok().link(identifier, ORIGINAL + " " + TIMEGATE);
        final List<Link> links = getMementoLinks(identifier, resource.getMementos()).collect(toList());
        builder.links(links.toArray(new Link[0]));
        if (syntax.isPresent()) {
            builder.entity(new ResourceStreamer(serializer, links.stream().flatMap(linkToQuads), syntax.get()));
        } else {
            builder.entity(links.stream().map(Link::toString).collect(joining(",\n")) + "\n");
        }
        return builder;
    }

    /**
     * Create a response builder for a TimeGate response
     * @param identifier the public identifier for the resource
     * @param datetime the datetime for the corresponding memento
     * @return a response builder object
     */
    public Response.ResponseBuilder getTimeGateBuilder(final String identifier, final Instant datetime) {
        return Response.status(FOUND)
            .location(fromUri(identifier + "?version=" + datetime.toEpochMilli()).build())
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
    public static Stream<Link> getMementoLinks(final String identifier, final Stream<VersionRange> mementos) {
        final List<VersionRange> ranges = mementos.collect(toList());
        return concat(getTimeMap(identifier, ranges.stream()), ranges.stream().map(mementoToLink(identifier)));
    }

    /**
     * Attempt to retrieve an Accept-Datetime from a set of HttpHeaders
     * @param headers the headers
     * @return a point in time, if one is available
     */
    public static Optional<Instant> getAcceptDatetime(final HttpHeaders headers) {
        return ofNullable(headers.getRequestHeaders().getFirst(ACCEPT_DATETIME))
                .map(x -> parse(x, RFC_1123_DATE_TIME)).map(ZonedDateTime::toInstant);
    }

    /**
     * Attempt to retrieve a version parameter from the request URI
     * @param uriInfo the request URI
     * @return a point in time, if one is available
     */
    public static Optional<Instant> getVersionParam(final UriInfo uriInfo) {
        return ofNullable(uriInfo.getQueryParameters().getFirst(VERSION))
                .map(String::trim).map(Long::parseLong).map(Instant::ofEpochMilli);
    }

    /**
     * Attempt to retrieve the timemap parameter from the request URI
     * @param uriInfo the request URI
     * @return true if the timemap parameter is set; false otherwise
     */
    public static Boolean getTimeMapParam(final UriInfo uriInfo) {
        return ofNullable(uriInfo.getQueryParameters().getFirst(TIMEMAP))
                .map(String::trim).map(Boolean::new).orElse(false);
    }

    private static final Function<Link, Stream<Quad>> linkToQuads = link -> {
        final IRI iri = rdf.createIRI(link.getUri().toString());
        final List<Quad> buffer = new ArrayList<>();
        if (link.getParams().containsKey(FROM)) {
            buffer.add(rdf.createQuad(Trellis.PreferUserManaged, iri, PROV.startedAtTime,
                        rdf.createLiteral(parse(link.getParams().get(FROM),
                                RFC_1123_DATE_TIME).toString(), XSD.dateTime)));
        }
        if (link.getParams().containsKey(UNTIL)) {
            buffer.add(rdf.createQuad(Trellis.PreferUserManaged, iri, PROV.endedAtTime,
                        rdf.createLiteral(parse(link.getParams().get(UNTIL),
                                RFC_1123_DATE_TIME).toString(), XSD.dateTime)));
        }
        if (MEMENTO.equals(link.getRel()) && link.getParams().containsKey(DATETIME)) {
            buffer.add(rdf.createQuad(Trellis.PreferUserManaged, iri, PROV.atTime,
                        rdf.createLiteral(parse(link.getParams().get(DATETIME),
                                RFC_1123_DATE_TIME).toString(), XSD.dateTime)));
        }
        return buffer.stream();
    };

    private static Stream<Link> getTimeMap(final String identifier, final Stream<VersionRange> mementos) {
        return mementos.reduce((acc, x) -> new VersionRange(acc.getFrom(), x.getUntil()))
                .map(x -> Link.fromUri(identifier + "?timemap=true").rel(TIMEMAP)
                        .type(APPLICATION_LINK_FORMAT)
                        .param(FROM, ofInstant(x.getFrom(), UTC).format(RFC_1123_DATE_TIME))
                        .param(UNTIL, ofInstant(x.getUntil(), UTC).format(RFC_1123_DATE_TIME)).build())
                // TODO use Optional::stream with JDK9
                .map(Stream::of).orElse(empty());
    }

    private static Function<VersionRange, Link> mementoToLink(final String identifier) {
        return range ->
            Link.fromUri(identifier + "?version=" + range.getFrom().toEpochMilli()).rel(MEMENTO)
                .param(DATETIME, ofInstant(range.getFrom(), UTC).format(RFC_1123_DATE_TIME)).build();
    }
}
