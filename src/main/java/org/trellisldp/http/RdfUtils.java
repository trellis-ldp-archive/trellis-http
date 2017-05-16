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

import static java.util.Arrays.stream;
import static java.util.Optional.ofNullable;
import static java.util.stream.Stream.empty;
import static java.util.stream.Stream.of;
import static org.trellisldp.http.HttpConstants.DEFAULT_REPRESENTATION;
import static org.trellisldp.http.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.RdfMediaType.VARIANTS;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Variant;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFSyntax;
import org.apache.commons.rdf.api.RDFTerm;

/**
 * @author acoburn
 */
final class RdfUtils {

    private static final RDF rdf = ServiceLoader.load(RDF.class).iterator().next();

    /**
     * Get a Commons RDF instance
     * @return the RDF instance
     */
    public static RDF getInstance() {
        return rdf;
    }

    /**
     * Create a filter based on a Prefer header
     * @param prefer the Prefer header
     * @return a suitable predicate for filtering a stream of quads
     */
    public static Predicate<Quad> filterWithPrefer(final Prefer prefer) {
        final Set<String> include = new HashSet<>(DEFAULT_REPRESENTATION);
        ofNullable(prefer).ifPresent(p -> {
            p.getOmit().forEach(include::remove);
            p.getInclude().forEach(include::add);
        });
        return quad -> quad.getGraphName().filter(x -> x instanceof IRI).map(x -> (IRI) x)
            .map(IRI::getIRIString).filter(include::contains).isPresent();
    }

    /**
     * Fetch the appropriate RDF syntax, if one exists
     * @param types the mediatypes from HTTP Accept
     * @return the rdf syntax, if available
     */
    public static Optional<RDFSyntax> getRdfSyntax(final List<MediaType> types) {
        return types.stream().flatMap(getSyntax).findFirst();
    }

    /**
     * Fetch the appropriate Accept profile, if one exists
     * @param types the mediatypes from HTTP Accept
     * @return the profile, if available
     */
    public static Optional<IRI> getProfile(final List<MediaType> types) {
        return types.stream().flatMap(profileMapper).findFirst();
    }

    /**
     * Convert an internal IRI to an external IRI
     * @param term the RDF term
     * @param baseUrl the base URL
     * @return a converted RDF term
     */
    public static RDFTerm toExternalIri(final RDFTerm term, final String baseUrl) {
        if (term instanceof IRI) {
            final String iri = ((IRI) term).getIRIString();
            if (iri.startsWith(TRELLIS_PREFIX)) {
                return rdf.createIRI(baseUrl + iri.substring(TRELLIS_PREFIX.length()));
            }
        }
        return term;
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

    private RdfUtils() {
        // prevent instantiation
    }
}