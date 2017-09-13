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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonMap;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.rdf.api.RDFSyntax.JSONLD;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.trellisldp.spi.RDFUtils.TRELLIS_BNODE_PREFIX;
import static org.trellisldp.spi.RDFUtils.getInstance;
import static org.trellisldp.vocabulary.JSONLD.compacted;

import java.util.List;

import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.core.MediaType;

import org.apache.commons.rdf.api.BlankNode;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.Triple;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.Trellis;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @author acoburn
 */
@RunWith(MockitoJUnitRunner.class)
public class RdfUtilsTest {

    private static final RDF rdf = getInstance();

    @Mock
    private ResourceService mockResourceService;

    @Test
    public void testGetSyntax() {
        final List<MediaType> types = asList(
                new MediaType("application", "json"),
                new MediaType("text", "xml"),
                new MediaType("text", "turtle"));

        assertEquals(of(TURTLE), RdfUtils.getSyntax(types, empty()));
    }

    @Test
    public void testGetSyntaxEmpty() {
        assertFalse(RdfUtils.getSyntax(emptyList(), of("some/type")).isPresent());
        assertEquals(of(TURTLE), RdfUtils.getSyntax(emptyList(), empty()));
    }

    @Test
    public void testGetSyntaxFallback() {
        final List<MediaType> types = asList(
                new MediaType("application", "json"),
                new MediaType("text", "xml"),
                new MediaType("text", "turtle"));

        assertFalse(RdfUtils.getSyntax(types, of("application/json")).isPresent());
    }

    @Test(expected = NotAcceptableException.class)
    public void testGetSyntaxError() {
        final List<MediaType> types = asList(
                new MediaType("application", "json"),
                new MediaType("text", "xml"));

        RdfUtils.getSyntax(types, empty());
    }

    @Test
    public void testFilterPrefer() {
        final IRI iri = rdf.createIRI("trellis:repository/resource");
        final Quad q1 = rdf.createQuad(Trellis.PreferAudit, iri, DC.creator, rdf.createLiteral("me"));
        final Quad q2 = rdf.createQuad(Trellis.PreferServerManaged, iri, DC.modified, rdf.createLiteral("now"));
        final Quad q3 = rdf.createQuad(Trellis.PreferUserManaged, iri, DC.subject, rdf.createLiteral("subj"));
        final List<Quad> quads = asList(q1, q2, q3);

        final List<Quad> filtered = quads.stream().filter(RdfUtils.filterWithPrefer(
                    Prefer.valueOf("return=representation; include=\"" +
                        Trellis.PreferServerManaged.getIRIString() + "\""))).collect(toList());

        assertTrue(filtered.contains(q2));
        assertTrue(filtered.contains(q3));
        assertEquals(2, filtered.size());

        final List<Quad> filtered2 = quads.stream().filter(RdfUtils.filterWithPrefer(
                    Prefer.valueOf("return=representation"))).collect(toList());

        assertTrue(filtered2.contains(q3));
        assertEquals(1, filtered2.size());
    }

    @Test
    public void testSkolemize() {
        final IRI iri = rdf.createIRI("trellis:repository/resource");
        final IRI anonIri = rdf.createIRI(TRELLIS_BNODE_PREFIX + "foo");
        final Literal literal = rdf.createLiteral("A title");
        final BlankNode bnode = rdf.createBlankNode("foo");

        when(mockResourceService.skolemize(any(Literal.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(IRI.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(BlankNode.class))).thenAnswer(inv ->
                rdf.createIRI(TRELLIS_BNODE_PREFIX + ((BlankNode) inv.getArgument(0)).uniqueReference()));
        when(mockResourceService.unskolemize(any(IRI.class)))
            .thenAnswer(inv -> {
                final String uri = ((IRI) inv.getArgument(0)).getIRIString();
                if (uri.startsWith(TRELLIS_BNODE_PREFIX)) {
                    return bnode;
                }
                return (IRI) inv.getArgument(0);
            });

        when(mockResourceService.unskolemize(any(Literal.class))).then(returnsFirstArg());

        final IRI subject = rdf.createIRI("http://example.org/repository/resource");
        final Graph graph = rdf.createGraph();
        graph.add(rdf.createTriple(subject, DC.title, literal));
        graph.add(rdf.createTriple(subject, DC.subject, bnode));
        graph.add(rdf.createTriple(bnode, DC.title, literal));

        final List<Triple> triples = graph.stream()
            .map(RdfUtils.skolemizeTriples(mockResourceService, "http://example.org/"))
            .collect(toList());

        assertTrue(triples.stream().anyMatch(t -> t.getSubject().equals(iri)));
        assertTrue(triples.stream().anyMatch(t -> t.getObject().equals(literal)));
        assertTrue(triples.stream().anyMatch(t -> t.getSubject().ntriplesString()
                    .startsWith("<" + TRELLIS_BNODE_PREFIX)));

        triples.stream().map(RdfUtils.unskolemizeTriples(mockResourceService, "http://example.org/"))
            .forEach(t -> assertTrue(graph.contains(t)));
    }

    @Test
    public void testProfile() {
        final List<MediaType> types = asList(
                new MediaType("application", "json"),
                new MediaType("text", "xml"),
                new MediaType("application", "ld+json", singletonMap("profile", compacted.getIRIString())));
        assertEquals(compacted, RdfUtils.getProfile(types, JSONLD));
    }

    @Test
    public void testMultipleProfiles() {
        final List<MediaType> types = asList(
                new MediaType("application", "json"),
                new MediaType("text", "xml"),
                new MediaType("application", "ld+json", singletonMap("profile", "first second")));
        assertEquals(rdf.createIRI("first"), RdfUtils.getProfile(types, JSONLD));
    }

    @Test
    public void testNoProfile() {
        final List<MediaType> types = asList(
                new MediaType("application", "json"),
                new MediaType("text", "xml"),
                new MediaType("application", "ld+json"));
        assertNull(RdfUtils.getProfile(types, JSONLD));
    }
}
