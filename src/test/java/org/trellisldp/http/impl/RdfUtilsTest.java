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
import static java.util.Collections.singletonMap;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.trellisldp.spi.RDFUtils.getInstance;

import java.util.List;

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
import org.trellisldp.vocabulary.JSONLD;
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

        assertEquals(TURTLE, RdfUtils.getRdfSyntax(types));
    }

    @Test
    public void testFilterPrefer() {
        final IRI iri = rdf.createIRI("trellis:repository/resource");
        final Quad q1 = rdf.createQuad(Trellis.PreferAudit, iri, DC.creator, rdf.createLiteral("me"));
        final Quad q2 = rdf.createQuad(Trellis.PreferServerManaged, iri, DC.modified, rdf.createLiteral("now"));
        final Quad q3 = rdf.createQuad(Trellis.PreferUserManaged, iri, DC.subject, rdf.createLiteral("subj"));
        final List<Quad> quads = asList(q1, q2, q3);

        final List<Quad> filtered = quads.stream().filter(RdfUtils.filterWithPrefer(
                    new Prefer("return=representation; include=\"" +
                        Trellis.PreferServerManaged.getIRIString() + "\""))).collect(toList());

        assertTrue(filtered.contains(q2));
        assertTrue(filtered.contains(q3));
        assertEquals(2, filtered.size());

        final List<Quad> filtered2 = quads.stream().filter(RdfUtils.filterWithPrefer(
                    new Prefer("return=representation"))).collect(toList());

        assertTrue(filtered2.contains(q3));
        assertEquals(1, filtered2.size());
    }

    @Test
    public void testExternalize() {
        final IRI iri1 = rdf.createIRI("trellis:repository/resource");
        final IRI iri2 = rdf.createIRI("http://example.org/resource");
        final Literal literal = rdf.createLiteral("Text");
        final String externalUrl = "http://localhost/api/";
        assertEquals(rdf.createIRI(externalUrl + "repository/resource"), RdfUtils.toExternalIri(iri1, externalUrl));
        assertEquals(iri2, RdfUtils.toExternalIri(iri2, externalUrl));
        assertEquals(literal, RdfUtils.toExternalIri(literal, externalUrl));
    }

    @Test
    public void testInternalize() {
        final IRI iri1 = rdf.createIRI("trellis:repository/resource");
        final IRI iri2 = rdf.createIRI("http://example.org/resource");
        final Literal literal = rdf.createLiteral("Text");
        final String externalUrl = "http://localhost/api/";

        assertEquals(iri1, RdfUtils.toInternalIri(rdf.createIRI(externalUrl + "repository/resource"), externalUrl));
        assertEquals(iri2, RdfUtils.toInternalIri(iri2, externalUrl));
        assertEquals(literal, RdfUtils.toInternalIri(literal, externalUrl));
    }

    @Test
    public void testSkolemize() {
        final IRI iri = rdf.createIRI("trellis:repository/resource");
        final IRI anonIri = rdf.createIRI("trellis:bnode/foo");
        final Literal literal = rdf.createLiteral("A title");
        final BlankNode bnode = rdf.createBlankNode("foo");

        when(mockResourceService.skolemize(any(BlankNode.class))).thenReturn(anonIri);
        when(mockResourceService.skolemize(any(Literal.class))).thenReturn(literal);
        when(mockResourceService.skolemize(any(IRI.class))).thenReturn(iri);
        when(mockResourceService.unskolemize(eq(anonIri))).thenReturn(bnode);
        when(mockResourceService.unskolemize(any(Literal.class))).thenReturn(literal);
        when(mockResourceService.unskolemize(eq(iri))).thenReturn(iri);

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
        assertTrue(triples.stream().anyMatch(t -> t.getSubject().equals(anonIri)));

        triples.stream().map(RdfUtils.unskolemizeTriples(mockResourceService, "http://example.org/"))
            .forEach(t -> assertTrue(graph.contains(t)));
    }

    @Test
    public void testProfile() {
        final List<MediaType> types = asList(
                new MediaType("application", "json"),
                new MediaType("text", "xml"),
                new MediaType("application", "ld+json", singletonMap("profile", JSONLD.compacted.getIRIString())));
        assertEquals(JSONLD.compacted, RdfUtils.getProfile(types));
    }

    @Test
    public void testMultipleProfiles() {
        final List<MediaType> types = asList(
                new MediaType("application", "json"),
                new MediaType("text", "xml"),
                new MediaType("application", "ld+json", singletonMap("profile", "first second")));
        assertEquals(rdf.createIRI("first"), RdfUtils.getProfile(types));
    }

    @Test
    public void testNoProfile() {
        final List<MediaType> types = asList(
                new MediaType("application", "json"),
                new MediaType("text", "xml"),
                new MediaType("application", "ld+json"));
        assertNull(RdfUtils.getProfile(types));
    }
}
