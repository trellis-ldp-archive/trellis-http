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
import static java.time.Instant.ofEpochSecond;
import static java.util.Collections.emptyMap;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.HttpHeaders.CONTENT_TYPE;
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
import static org.trellisldp.spi.RDFUtils.TRELLIS_BNODE_PREFIX;
import static org.trellisldp.spi.RDFUtils.TRELLIS_PREFIX;
import static org.trellisldp.spi.RDFUtils.getInstance;
import static org.trellisldp.vocabulary.RDF.type;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.WebApplicationException;
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
import org.trellisldp.http.domain.Digest;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.ConstraintViolation;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
@RunWith(MockitoJUnitRunner.class)
public class PostHandlerTest {

    private final static Instant time = ofEpochSecond(1496262729);
    private final static String baseUrl = "http://example.org/repo/";
    private final static RDF rdf = getInstance();
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
    private ArgumentCaptor<Map<String, String>> metadataArgument;

    @Before
    public void setUp() {
        when(mockBinaryService.getIdentifierSupplier(anyString())).thenReturn(() -> "file:" + randomUUID());
        when(mockResourceService.put(any(IRI.class), any(Dataset.class))).thenReturn(true);
        when(mockResourceService.skolemize(any(Literal.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(IRI.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(BlankNode.class))).thenAnswer(inv ->
                rdf.createIRI(TRELLIS_BNODE_PREFIX + ((BlankNode) inv.getArgument(0)).uniqueReference()));

        when(mockRequest.getSession()).thenReturn(new HttpSession());
        when(mockRequest.getPartition()).thenReturn("partition");
        when(mockRequest.getPath()).thenReturn("");
        when(mockRequest.getBaseUrl(any())).thenReturn(baseUrl);
    }

    @Test
    public void testPostLdprs() {
        when(mockRequest.getLink()).thenReturn(fromUri(LDP.Container.getIRIString()).rel("type").build());

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", null,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

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
        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", null,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

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

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", null,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

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

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", null,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

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

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", null,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

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

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", null,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

        final Response res = postHandler.createResource().build();
        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertEquals(create(baseUrl + "partition/newresource"), res.getLocation());
    }

    @Test
    public void testEntity() throws IOException {
        final String path = "partition/newresource";
        final IRI identifier = rdf.createIRI("trellis:" + path);
        final Triple triple = rdf.createTriple(rdf.createIRI(baseUrl + path), DC.title,
                        rdf.createLiteral("A title"));
        when(mockIoService.read(any(), any(), eq(TURTLE))).thenAnswer(x -> Stream.of(triple));
        final File entity = new File(getClass().getResource("/simpleTriple.ttl").getFile());

        when(mockRequest.getContentType()).thenReturn("text/turtle");

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", entity,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

        final Response res = postHandler.createResource().build();
        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertEquals(create(baseUrl + path), res.getLocation());

        verify(mockBinaryService, never()).setContent(eq("partition"), any(IRI.class), any(InputStream.class));

        verify(mockIoService).read(any(InputStream.class), eq(baseUrl + path), eq(TURTLE));

        verify(mockConstraintService).constrainedBy(eq(LDP.RDFSource), eq("trellis:partition"), any(Graph.class));

        verify(mockResourceService).put(eq(identifier), any(Dataset.class));
    }

    @Test
    public void testEntity2() throws IOException {
        final IRI identifier = rdf.createIRI("trellis:partition/newresource");
        final File entity = new File(getClass().getResource("/simpleData.txt").getFile());
        when(mockRequest.getContentType()).thenReturn("text/plain");

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", entity,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

        final Response res = postHandler.createResource().build();
        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertEquals(create(baseUrl + "partition/newresource"), res.getLocation());

        verify(mockIoService, never()).read(any(), any(), any());
        verify(mockConstraintService, never()).constrainedBy(any(), eq(baseUrl), any());

        verify(mockBinaryService).setContent(eq("partition"), iriArgument.capture(), any(InputStream.class),
                metadataArgument.capture());
        assertTrue(iriArgument.getValue().getIRIString().startsWith("file:"));
        assertEquals("text/plain", metadataArgument.getValue().get(CONTENT_TYPE));

        verify(mockResourceService).put(eq(identifier), any(Dataset.class));
    }

    @Test
    public void testEntity3() throws IOException {
        final IRI identifier = rdf.createIRI("trellis:partition/newresource");
        final File entity = new File(getClass().getResource("/simpleData.txt").getFile());
        when(mockRequest.getContentType()).thenReturn("text/plain");
        when(mockRequest.getDigest()).thenReturn(new Digest("md5", "1VOyRwUXW1CPdC5nelt7GQ=="));

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", entity,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

        final Response res = postHandler.createResource().build();
        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertEquals(create(baseUrl + "partition/newresource"), res.getLocation());

        verify(mockIoService, never()).read(any(), any(), any());
        verify(mockConstraintService, never()).constrainedBy(any(), eq(baseUrl), any());

        verify(mockBinaryService).setContent(eq("partition"), iriArgument.capture(), any(InputStream.class),
                metadataArgument.capture());
        assertTrue(iriArgument.getValue().getIRIString().startsWith("file:"));
        assertEquals("text/plain", metadataArgument.getValue().get(CONTENT_TYPE));

        verify(mockResourceService).put(eq(identifier), any(Dataset.class));
    }

    @Test
    public void testEntityBadDigest() {
        final File entity = new File(getClass().getResource("/simpleData.txt").getFile());
        when(mockRequest.getContentType()).thenReturn("text/plain");
        when(mockRequest.getDigest()).thenReturn(new Digest("md5", "blahblah"));

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", entity,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

        assertEquals(BAD_REQUEST, postHandler.createResource().build().getStatusInfo());
    }

    @Test(expected = BadRequestException.class)
    public void testBadDigest2() {
        final File entity = new File(getClass().getResource("/simpleData.txt").getFile());
        when(mockRequest.getContentType()).thenReturn("text/plain");
        when(mockRequest.getDigest()).thenReturn(new Digest("foo", "blahblah"));

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", entity,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

        postHandler.createResource();
    }

    @Test(expected = WebApplicationException.class)
    public void testBadEntityDigest() {
        when(mockRequest.getContentType()).thenReturn("text/plain");
        when(mockRequest.getDigest()).thenReturn(new Digest("md5", "blahblah"));
        final File entity = new File(new File(getClass().getResource("/simpleData.txt").getFile()).getParent());

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", entity,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

        postHandler.createResource();
    }

    @Test(expected = WebApplicationException.class)
    public void testEntityError() {
        final IRI identifier = rdf.createIRI("trellis:partition/newresource");
        final File entity = new File(getClass().getResource("/simpleData.txt").getFile() + ".nonexistent-suffix");
        when(mockRequest.getContentType()).thenReturn("text/plain");

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", entity,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

        postHandler.createResource();
    }

    @Test(expected = WebApplicationException.class)
    public void testConstraint() {
        final IRI identifier = rdf.createIRI("trellis:partition/resource");
        when(mockIoService.read(any(), any(), eq(TURTLE))).thenAnswer(x -> Stream.of(
                    rdf.createTriple(rdf.createIRI("http://example.org/repository/newresource"), DC.title,
                        rdf.createLiteral("A title"))));
        when(mockConstraintService.constrainedBy(eq(LDP.RDFSource), eq("trellis:partition"), any()))
            .thenReturn(of(new ConstraintViolation(Trellis.InvalidRange,
                            rdf.createTriple(identifier, type, rdf.createLiteral("Some literal")))));
        final File entity = new File(getClass().getResource("/simpleTriple.ttl").getFile());

        when(mockRequest.getContentType()).thenReturn("text/turtle");

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", entity,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

        postHandler.createResource();
    }

    @Test
    public void testError() {
        when(mockResourceService.put(eq(rdf.createIRI(TRELLIS_PREFIX + "partition/newresource")), any(Dataset.class)))
            .thenReturn(false);
        when(mockRequest.getContentType()).thenReturn("text/turtle");

        final PostHandler postHandler = new PostHandler(partitions, mockRequest, "/newresource", null,
                mockResourceService, mockIoService, mockConstraintService, mockBinaryService);

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
