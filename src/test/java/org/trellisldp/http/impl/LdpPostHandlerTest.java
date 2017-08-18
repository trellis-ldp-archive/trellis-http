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

import static java.net.URI.create;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.ofEpochSecond;
import static java.util.Collections.emptyMap;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Link.fromUri;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.spi.RDFUtils.getInstance;
import static org.trellisldp.vocabulary.RDF.type;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.ws.rs.core.Link;
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
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
@RunWith(MockitoJUnitRunner.class)
public class LdpPostHandlerTest {

    private final static Instant time = ofEpochSecond(1496262729);
    private final static String baseUrl = "http://example.org/repo/";
    private final static RDF rdf = getInstance();
    private final static String BNODE_PREFIX = "trellis:bnode/";
    private final static Map<String, String> partitions = emptyMap();

    @Mock
    private ResourceService mockResourceService;

    @Mock
    private IOService mockIoService;

    @Mock
    private BinaryService mockBinaryService;

    @Mock
    private ConstraintService mockConstraintService;

    @Mock
    private Resource mockResource;

    @Mock
    private LdpRequest mockRequest;

    @Captor
    private ArgumentCaptor<IRI> iriArgument;

    @Captor
    private ArgumentCaptor<Graph> graphArgument;

    @Captor
    private ArgumentCaptor<Dataset> datasetArgument;

    @Before
    public void setUp() {
        when(mockBinaryService.getIdentifierSupplier(anyString())).thenReturn(() -> "file:" + randomUUID());
        when(mockResourceService.put(any(IRI.class), any(Dataset.class))).thenReturn(true);
        when(mockResourceService.skolemize(any(Literal.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(IRI.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(BlankNode.class)))
            .thenAnswer(inv -> rdf.createIRI(BNODE_PREFIX + ((BlankNode) inv.getArgument(0)).uniqueReference()));

        when(mockRequest.getSession()).thenReturn(new HttpSession());
        when(mockRequest.getPartition()).thenReturn("partition");
        when(mockRequest.getPath()).thenReturn("/newresource");
        when(mockRequest.getBaseUrl(any())).thenReturn(baseUrl);
    }

    @Test
    public void testPostLdprs() {
        when(mockRequest.getLink()).thenReturn(fromUri(LDP.Container.getIRIString()).rel("type").build());

        final LdpPostHandler postHandler = new LdpPostHandler(partitions, mockRequest, mockResourceService,
                mockIoService, mockConstraintService, mockBinaryService);

        final Response res = postHandler.createResource().build();
        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertEquals(create(baseUrl + "partition/newresource"), res.getLocation());
    }

    @Test
    public void testDefaultType1() {
        final LdpPostHandler postHandler = new LdpPostHandler(partitions, mockRequest, mockResourceService,
                mockIoService, mockConstraintService, mockBinaryService);

        final Response res = postHandler.createResource().build();
        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertEquals(create(baseUrl + "partition/newresource"), res.getLocation());
    }

    @Test
    public void testDefaultType2() {
        when(mockRequest.getContentType()).thenReturn("text/plain");

        final LdpPostHandler postHandler = new LdpPostHandler(partitions, mockRequest, mockResourceService,
                mockIoService, mockConstraintService, mockBinaryService);

        final Response res = postHandler.createResource().build();
        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertEquals(create(baseUrl + "partition/newresource"), res.getLocation());
    }

    @Test
    public void testDefaultType3() {
        when(mockRequest.getLink()).thenReturn(fromUri(LDP.Resource.getIRIString()).rel("type").build());

        final LdpPostHandler postHandler = new LdpPostHandler(partitions, mockRequest, mockResourceService,
                mockIoService, mockConstraintService, mockBinaryService);

        final Response res = postHandler.createResource().build();
        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertEquals(create(baseUrl + "partition/newresource"), res.getLocation());
    }

    @Test
    public void testDefaultType4() {
        when(mockRequest.getContentType()).thenReturn("text/plain");
        when(mockRequest.getLink()).thenReturn(fromUri(LDP.Resource.getIRIString()).rel("type").build());

        final LdpPostHandler postHandler = new LdpPostHandler(partitions, mockRequest, mockResourceService,
                mockIoService, mockConstraintService, mockBinaryService);

        final Response res = postHandler.createResource().build();
        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertEquals(create(baseUrl + "partition/newresource"), res.getLocation());
    }

    @Test
    public void testDefaultType5() {
        when(mockRequest.getContentType()).thenReturn("text/turtle");

        final LdpPostHandler postHandler = new LdpPostHandler(partitions, mockRequest, mockResourceService,
                mockIoService, mockConstraintService, mockBinaryService);

        final Response res = postHandler.createResource().build();
        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertEquals(create(baseUrl + "partition/newresource"), res.getLocation());
    }

    @Test
    public void testEntity() {
        final String path = "partition/newresource";
        final IRI identifier = rdf.createIRI("trellis:" + path);
        final Triple triple = rdf.createTriple(rdf.createIRI(baseUrl + path), DC.title,
                        rdf.createLiteral("A title"));
        when(mockIoService.read(any(), any(), eq(TURTLE))).thenAnswer(x -> Stream.of(triple));
        final InputStream entity = new ByteArrayInputStream("<> <http://purl.org/dc/terms/title> \"A title\" ."
                .getBytes(UTF_8));

        when(mockRequest.getContentType()).thenReturn("text/turtle");

        final LdpPostHandler postHandler = new LdpPostHandler(partitions, mockRequest, mockResourceService,
                mockIoService, mockConstraintService, mockBinaryService);

        postHandler.setEntity(entity);

        final Response res = postHandler.createResource().build();
        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertEquals(create(baseUrl + path), res.getLocation());

        verify(mockBinaryService, never()).setContent(eq("partition"), any(IRI.class), any(InputStream.class));

        verify(mockIoService).read(eq(entity), eq(baseUrl + path), eq(TURTLE));

        verify(mockConstraintService).constrainedBy(eq(LDP.RDFSource), eq(baseUrl), graphArgument.capture());
        assertEquals(1L, graphArgument.getValue().size());
        assertTrue(graphArgument.getValue().contains(rdf.createTriple(
                        identifier, triple.getPredicate(), triple.getObject())));

        verify(mockResourceService).put(eq(identifier), datasetArgument.capture());
        assertTrue(datasetArgument.getValue().contains(rdf.createQuad(Trellis.PreferUserManaged,
                        identifier, triple.getPredicate(), triple.getObject())));
        assertTrue(datasetArgument.getValue().contains(rdf.createQuad(Trellis.PreferServerManaged,
                        identifier, type, LDP.RDFSource)));

        // Audit adds 5 triples + 1 interaction model + 1 user triple
        assertEquals(7L, datasetArgument.getValue().size());
    }

    @Test
    public void testEntity2() {
        final IRI identifier = rdf.createIRI("trellis:partition/newresource");
        final InputStream entity = new ByteArrayInputStream("Some data".getBytes(UTF_8));
        when(mockRequest.getContentType()).thenReturn("text/plain");

        final LdpPostHandler postHandler = new LdpPostHandler(partitions, mockRequest, mockResourceService,
                mockIoService, mockConstraintService, mockBinaryService);

        postHandler.setEntity(entity);

        final Response res = postHandler.createResource().build();
        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertEquals(create(baseUrl + "partition/newresource"), res.getLocation());

        verify(mockIoService, never()).read(any(), any(), any());
        verify(mockConstraintService, never()).constrainedBy(any(), eq(baseUrl), any());

        verify(mockBinaryService).setContent(eq("partition"), iriArgument.capture(), eq(entity));
        assertTrue(iriArgument.getValue().getIRIString().startsWith("file:"));

        verify(mockResourceService).put(eq(identifier), datasetArgument.capture());
        assertTrue(datasetArgument.getValue().contains(rdf.createQuad(Trellis.PreferServerManaged,
                        iriArgument.getValue(), DC.format, rdf.createLiteral("text/plain"))));
        assertTrue(datasetArgument.getValue().contains(rdf.createQuad(Trellis.PreferServerManaged,
                        identifier, DC.hasPart, iriArgument.getValue())));
        assertTrue(datasetArgument.getValue().contains(rdf.createQuad(Trellis.PreferServerManaged,
                        identifier, type, LDP.NonRDFSource)));

        // Audit adds 5 triples + 3 server managed + 0 user managed
        assertEquals(5L, datasetArgument.getValue().stream(of(Trellis.PreferAudit), null, null, null).count());
        assertEquals(3L, datasetArgument.getValue().stream(of(Trellis.PreferServerManaged), null, null, null).count());
        assertEquals(0L, datasetArgument.getValue().stream(of(Trellis.PreferUserManaged), null, null, null).count());
    }

    @Test
    public void testConstraint() {
        when(mockIoService.read(any(), any(), eq(TURTLE))).thenAnswer(x -> Stream.of(
                    rdf.createTriple(rdf.createIRI("http://example.org/repository/newresource"), DC.title,
                        rdf.createLiteral("A title"))));
        when(mockConstraintService.constrainedBy(eq(LDP.RDFSource), eq(baseUrl), any()))
            .thenReturn(of(Trellis.InvalidRange));
        final InputStream entity = new ByteArrayInputStream("<> <http://purl.org/dc/terms/title> \"A title\" ."
                .getBytes(UTF_8));

        when(mockRequest.getContentType()).thenReturn("text/turtle");

        final LdpPostHandler postHandler = new LdpPostHandler(partitions, mockRequest, mockResourceService,
                mockIoService, mockConstraintService, mockBinaryService);

        postHandler.setEntity(entity);

        final Response res = postHandler.createResource().build();
        assertEquals(BAD_REQUEST, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasLink(Trellis.InvalidRange, LDP.constrainedBy.getIRIString())));
    }

    @Test
    public void testError() {
        when(mockResourceService.put(eq(rdf.createIRI(TRELLIS_PREFIX + "partition/newresource")), any(Dataset.class)))
            .thenReturn(false);
        when(mockRequest.getContentType()).thenReturn("text/turtle");

        final LdpPostHandler postHandler = new LdpPostHandler(partitions, mockRequest, mockResourceService,
                mockIoService, mockConstraintService, mockBinaryService);

        final Response res = postHandler.createResource().build();
        assertEquals(INTERNAL_SERVER_ERROR, res.getStatusInfo());
    }

    private static Predicate<Link> hasLink(final IRI iri, final String rel) {
        return link -> rel.equals(link.getRel()) && iri.getIRIString().equals(link.getUri().toString());
    }

    private static Predicate<Link> hasType(final IRI iri) {
        return hasLink(iri, "type");
    }
}
