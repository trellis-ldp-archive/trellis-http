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

import static java.time.Instant.ofEpochSecond;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.MediaType.TEXT_HTML_TYPE;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.rdf.api.RDFSyntax.RDFA_HTML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_POST;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_RANGES;
import static org.trellisldp.http.domain.HttpConstants.PREFERENCE_APPLIED;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_SPARQL_UPDATE;
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE_TYPE;
import static org.trellisldp.spi.RDFUtils.getInstance;
import static org.trellisldp.vocabulary.RDF.type;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.BlankNode;
import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.Triple;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.RuntimeRepositoryException;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.RDFS;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
@RunWith(MockitoJUnitRunner.class)
public class LdpPatchHandlerTest {

    private final static Instant time = ofEpochSecond(1496262729);
    private final static String baseUrl = "http://localhost:8080/repo/";
    private final static RDF rdf = getInstance();
    private final static String BNODE_PREFIX = "trellis:bnode/";
    private final static String insert = "INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}";
    private final static IRI identifier = rdf.createIRI("trellis:partition/resource");

    @Mock
    private ResourceService mockResourceService;

    @Mock
    private IOService mockIoService;

    @Mock
    private ConstraintService mockConstraintService;

    @Mock
    private Resource mockResource;

    @Mock
    private Request mockRequest;

    @Captor
    private ArgumentCaptor<Graph> graphArgument;

    @Captor
    private ArgumentCaptor<Dataset> datasetArgument;

    @Before
    public void setUp() {
        when(mockResource.getModified()).thenReturn(time);
        when(mockResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockResource.getIdentifier()).thenReturn(identifier);
        when(mockResourceService.put(any(IRI.class), any(Dataset.class))).thenReturn(true);
        when(mockResourceService.skolemize(any(Literal.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(IRI.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(BlankNode.class)))
            .thenAnswer(inv -> rdf.createIRI(BNODE_PREFIX + ((BlankNode) inv.getArgument(0)).uniqueReference()));
    }

    @Test(expected = WebApplicationException.class)
    public void testPatchNoSession() {
        final LdpPatchHandler patchHandler = new LdpPatchHandler(mockResourceService, mockIoService,
                mockConstraintService, mockRequest);
        patchHandler.setPath("resource");
        patchHandler.setBaseUrl(baseUrl);
        patchHandler.setAcceptableTypes(singletonList(TEXT_TURTLE_TYPE));
        patchHandler.setSparqlUpdate(insert);

        patchHandler.updateResource(mockResource).build();
    }

    @Test(expected = WebApplicationException.class)
    public void testPatchNoSparql() {
        final LdpPatchHandler patchHandler = new LdpPatchHandler(mockResourceService, mockIoService,
                mockConstraintService, mockRequest);
        patchHandler.setPath("resource");
        patchHandler.setBaseUrl(baseUrl);
        patchHandler.setAcceptableTypes(singletonList(TEXT_TURTLE_TYPE));
        patchHandler.setSession(new HttpSession());

        patchHandler.updateResource(mockResource).build();
    }

    @Test
    public void testPatchLdprs() {
        final LdpPatchHandler patchHandler = new LdpPatchHandler(mockResourceService, mockIoService,
                mockConstraintService, mockRequest);
        patchHandler.setPath("resource");
        patchHandler.setBaseUrl(baseUrl);
        patchHandler.setAcceptableTypes(singletonList(TEXT_TURTLE_TYPE));
        patchHandler.setSession(new HttpSession());
        patchHandler.setSparqlUpdate(insert);

        final Response res = patchHandler.updateResource(mockResource).build();
        assertEquals(NO_CONTENT, res.getStatusInfo());
    }

    @Test
    public void testEntity() {
        final Triple triple = rdf.createTriple(identifier, RDFS.label, rdf.createLiteral("A label"));

        when(mockResource.stream(eq(Trellis.PreferUserManaged))).thenAnswer(x -> Stream.of(triple));

        final LdpPatchHandler patchHandler = new LdpPatchHandler(mockResourceService, mockIoService,
                mockConstraintService, mockRequest);
        patchHandler.setPath("partition/resource");
        patchHandler.setBaseUrl(baseUrl);
        patchHandler.setSession(new HttpSession());
        patchHandler.setContentType(APPLICATION_SPARQL_UPDATE);
        patchHandler.setAcceptableTypes(singletonList(TEXT_TURTLE_TYPE));
        patchHandler.setSparqlUpdate(insert);

        final Response res = patchHandler.updateResource(mockResource).build();
        assertEquals(NO_CONTENT, res.getStatusInfo());

        verify(mockIoService).update(graphArgument.capture(), eq(insert), eq(identifier.getIRIString()));
        assertTrue(graphArgument.getValue().contains(triple));

        verify(mockConstraintService).constrainedBy(eq(LDP.RDFSource), eq(baseUrl), graphArgument.capture());
        assertTrue(graphArgument.getValue().contains(triple));

        verify(mockResourceService).put(eq(identifier), datasetArgument.capture());
        assertTrue(datasetArgument.getValue().contains(rdf.createQuad(Trellis.PreferUserManaged,
                        triple.getSubject(), triple.getPredicate(), triple.getObject())));
        assertTrue(datasetArgument.getValue().contains(rdf.createQuad(Trellis.PreferServerManaged,
                        triple.getSubject(), type, LDP.RDFSource)));

        // Audit adds 5 triples + 1 interaction model + 1 user triple
        assertEquals(7L, datasetArgument.getValue().size());
    }

    @Test
    public void testPreferRepresentation() {
        final LdpPatchHandler patchHandler = new LdpPatchHandler(mockResourceService, mockIoService,
                mockConstraintService, mockRequest);
        patchHandler.setPath("partition/resource");
        patchHandler.setBaseUrl(baseUrl);
        patchHandler.setSession(new HttpSession());
        patchHandler.setContentType(APPLICATION_SPARQL_UPDATE);
        patchHandler.setAcceptableTypes(singletonList(TEXT_TURTLE_TYPE));
        patchHandler.setSparqlUpdate(insert);
        patchHandler.setPrefer(new Prefer("return=representation"));

        final Response res = patchHandler.updateResource(mockResource).build();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertEquals("return=representation", res.getHeaderString(PREFERENCE_APPLIED));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertTrue(TEXT_TURTLE_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(TEXT_TURTLE_TYPE));
    }

    @Test
    public void testPreferHTMLRepresentation() {
        final LdpPatchHandler patchHandler = new LdpPatchHandler(mockResourceService, mockIoService,
                mockConstraintService, mockRequest);
        patchHandler.setPath("partition/resource");
        patchHandler.setBaseUrl(baseUrl);
        patchHandler.setSession(new HttpSession());
        patchHandler.setContentType(APPLICATION_SPARQL_UPDATE);
        patchHandler.setAcceptableTypes(singletonList(MediaType.valueOf(RDFA_HTML.mediaType)));
        patchHandler.setSparqlUpdate(insert);
        patchHandler.setPrefer(new Prefer("return=representation"));

        final Response res = patchHandler.updateResource(mockResource).build();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertEquals("return=representation", res.getHeaderString(PREFERENCE_APPLIED));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertTrue(TEXT_HTML_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(TEXT_HTML_TYPE));
    }

    @Test
    public void testConstraint() {
        when(mockConstraintService.constrainedBy(eq(LDP.RDFSource), eq(baseUrl), any()))
            .thenReturn(Optional.of(Trellis.InvalidRange));
        final LdpPatchHandler patchHandler = new LdpPatchHandler(mockResourceService, mockIoService,
                mockConstraintService, mockRequest);
        patchHandler.setPath("resource");
        patchHandler.setBaseUrl(baseUrl);
        patchHandler.setSession(new HttpSession());
        patchHandler.setContentType(APPLICATION_SPARQL_UPDATE);
        patchHandler.setSparqlUpdate(insert);

        final Response res = patchHandler.updateResource(mockResource).build();
        assertEquals(BAD_REQUEST, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasLink(Trellis.InvalidRange, LDP.constrainedBy.getIRIString())));
    }

    @Test
    public void testDeleted() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.Resource);
        when(mockResource.getTypes()).thenAnswer(x -> Stream.of(Trellis.DeletedResource));

        final LdpPatchHandler patchHandler = new LdpPatchHandler(mockResourceService, mockIoService,
                mockConstraintService, mockRequest);
        patchHandler.setPath("partition/resource");
        patchHandler.setBaseUrl(baseUrl);
        patchHandler.setSession(new HttpSession());
        patchHandler.setContentType(APPLICATION_SPARQL_UPDATE);
        patchHandler.setSparqlUpdate(insert);

        final Response res = patchHandler.updateResource(mockResource).build();
        assertEquals(GONE, res.getStatusInfo());
    }

    @Test
    public void testConflict() {
        when(mockRequest.evaluatePreconditions(any(Date.class), any(EntityTag.class)))
            .thenReturn(status(CONFLICT));

        final LdpPatchHandler patchHandler = new LdpPatchHandler(mockResourceService, mockIoService,
                mockConstraintService, mockRequest);
        patchHandler.setPath("partition/resource");
        patchHandler.setBaseUrl(baseUrl);
        patchHandler.setSession(new HttpSession());
        patchHandler.setContentType(APPLICATION_SPARQL_UPDATE);
        patchHandler.setSparqlUpdate(insert);

        final Response res = patchHandler.updateResource(mockResource).build();
        assertEquals(CONFLICT, res.getStatusInfo());
    }

    @Test
    public void testError() {
        when(mockResourceService.put(eq(rdf.createIRI(TRELLIS_PREFIX + "partition/resource")), any(Dataset.class)))
            .thenReturn(false);

        final LdpPatchHandler patchHandler = new LdpPatchHandler(mockResourceService, mockIoService,
                mockConstraintService, mockRequest);
        patchHandler.setPath("partition/resource");
        patchHandler.setBaseUrl(baseUrl);
        patchHandler.setSession(new HttpSession());
        patchHandler.setContentType(APPLICATION_SPARQL_UPDATE);
        patchHandler.setSparqlUpdate(insert);

        final Response res = patchHandler.updateResource(mockResource).build();
        assertEquals(INTERNAL_SERVER_ERROR, res.getStatusInfo());
    }

    @Test
    public void testError2() {
        doThrow(RuntimeRepositoryException.class).when(mockIoService)
            .update(any(Graph.class), eq(insert), eq(identifier.getIRIString()));

        final LdpPatchHandler patchHandler = new LdpPatchHandler(mockResourceService, mockIoService,
                mockConstraintService, mockRequest);
        patchHandler.setPath("partition/resource");
        patchHandler.setBaseUrl(baseUrl);
        patchHandler.setSession(new HttpSession());
        patchHandler.setContentType(APPLICATION_SPARQL_UPDATE);
        patchHandler.setSparqlUpdate(insert);

        final Response res = patchHandler.updateResource(mockResource).build();
        assertEquals(BAD_REQUEST, res.getStatusInfo());
    }

    private static Predicate<Link> hasLink(final IRI iri, final String rel) {
        return link -> rel.equals(link.getRel()) && iri.getIRIString().equals(link.getUri().toString());
    }

    private static Predicate<Link> hasType(final IRI iri) {
        return hasLink(iri, "type");
    }
}
