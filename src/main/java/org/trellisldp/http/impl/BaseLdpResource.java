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

import static java.util.Arrays.stream;
import static java.util.stream.Stream.empty;
import static java.util.stream.Stream.of;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.trellisldp.http.impl.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.impl.RdfMediaType.VARIANTS;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Variant;

import org.apache.commons.rdf.api.BlankNodeOrIRI;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFSyntax;
import org.apache.commons.rdf.api.RDFTerm;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
public class BaseLdpResource {

    protected static final RDF rdf = ServiceLoader.load(RDF.class).iterator().next();

    protected final ResourceService resourceService;

    @Context
    protected UriInfo uriInfo;

    @Context
    protected HttpHeaders headers;

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

    protected static Predicate<Quad> filterWithPrefer(final Prefer prefer) {
        final Set<String> include = getDefaultRepresentation();
        prefer.getOmit().forEach(include::remove);
        prefer.getInclude().forEach(include::add);
        return quad -> quad.getGraphName().filter(x -> x instanceof IRI).map(x -> (IRI) x)
            .map(IRI::getIRIString).filter(include::contains).isPresent();
    }

    protected static Optional<RDFSyntax> getRdfSyntax(final List<MediaType> types) {
        return types.stream().flatMap(getSyntax).findFirst();
    }

    protected static Optional<IRI> getProfile(final List<MediaType> types) {
        return types.stream().flatMap(profileMapper).findFirst();
    }

    private static String stripSlash(final String path) {
        return path.endsWith("/") ? stripSlash(path.substring(0, path.length() - 1)) : path;
    }

    private static RDFTerm toExternalIri(final RDFTerm term, final String baseUrl) {
        if (term instanceof IRI) {
            final String iri = ((IRI) term).getIRIString();
            if (iri.startsWith(TRELLIS_PREFIX)) {
                return rdf.createIRI(baseUrl + iri.substring(TRELLIS_PREFIX.length()));
            }
        }
        return term;
    }

    private static Set<String> getDefaultRepresentation() {
        final Set<String> include = new HashSet<>();
        include.add(LDP.PreferContainment.getIRIString());
        include.add(LDP.PreferMembership.getIRIString());
        include.add(Trellis.PreferUserManaged.getIRIString());
        return include;
    }

    private static final Function<MediaType, Stream<IRI>> profileMapper = type -> {
        if (VARIANTS.stream().map(Variant::getMediaType).anyMatch(type::isCompatible)) {
            final Map<String, String> params = type.getParameters();
            if (params.containsKey("profile")) {
                return stream(params.get("profile").split(" ")).map(String::trim).flatMap(profile -> {
                    try {
                        return of(rdf.createIRI(profile));
                    } catch (final IllegalArgumentException ex) {
                        // ignore the profile value
                        return empty();
                    }
                });
            }
        }
        return empty();
    };

    private static final Function<MediaType, Stream<RDFSyntax>> getSyntax = type -> {
        final Optional<RDFSyntax> syntax = VARIANTS.stream().map(Variant::getMediaType).filter(type::isCompatible)
            .findFirst().map(MediaType::toString).flatMap(RDFSyntax::byMediaType);
        // TODO replace with Optional::stream with JDK 9
        return syntax.isPresent() ? of(syntax.get()) : empty();
    };
}
