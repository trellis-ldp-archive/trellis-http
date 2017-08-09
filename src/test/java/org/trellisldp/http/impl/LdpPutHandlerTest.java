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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.ofEpochSecond;
import static java.util.Collections.singletonList;
import static java.util.Optional.empty;
import static java.util.UUID.randomUUID;
import static javax.ws.rs.core.Link.fromUri;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
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
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE;
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE_TYPE;
import static org.trellisldp.spi.RDFUtils.getInstance;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.function.Predicate;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.BlankNode;
import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFSyntax;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.trellisldp.api.Binary;
import org.trellisldp.api.Resource;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.LDP;

/**
 * @author acoburn
 */
@RunWith(MockitoJUnitRunner.class)
public class LdpPutHandlerTest {

    private final static Instant time = ofEpochSecond(1496262729);
    private final static Instant binaryTime = ofEpochSecond(1496262750);

    private final static String baseUrl = "http://localhost:8080/repo/";
    private final static RDF rdf = getInstance();
    private final static String BNODE_PREFIX = "trellis:bnode/";
    private final Binary testBinary = new Binary(rdf.createIRI("file:binary.txt"), binaryTime, "text/plain", null);

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
    private Request mockRequest;

    @Before
    public void setUp() {
        when(mockResource.getBinary()).thenReturn(empty());
        when(mockResource.getModified()).thenReturn(time);
        when(mockBinaryService.getIdentifierSupplier(anyString())).thenReturn(() -> "file:" + randomUUID());
        when(mockResourceService.put(any(IRI.class), any(Dataset.class))).thenReturn(true);
        when(mockResourceService.skolemize(any(Literal.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(IRI.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(BlankNode.class)))
            .thenAnswer(inv -> rdf.createIRI(BNODE_PREFIX + ((BlankNode) inv.getArgument(0)).uniqueReference()));
    }

    @Test(expected = WebApplicationException.class)
    public void testPutNoSession() {
        final LdpPutHandler putHandler = new LdpPutHandler(mockResourceService, mockIoService, mockConstraintService,
                mockBinaryService, mockRequest);
        putHandler.setPath("resource");
        putHandler.setBaseUrl(baseUrl);
        putHandler.setAcceptableTypes(singletonList(TEXT_TURTLE_TYPE));
        putHandler.setLink(fromUri(LDP.Container.getIRIString()).rel("type").build());

        putHandler.setResource();
    }

    @Test(expected = WebApplicationException.class)
    public void testPutNoSession2() {
        final LdpPutHandler putHandler = new LdpPutHandler(mockResourceService, mockIoService, mockConstraintService,
                mockBinaryService, mockRequest);
        putHandler.setPath("resource");
        putHandler.setBaseUrl(baseUrl);
        putHandler.setAcceptableTypes(singletonList(TEXT_TURTLE_TYPE));
        putHandler.setLink(fromUri(LDP.Container.getIRIString()).rel("type").build());

        putHandler.setResource(mockResource);
    }

    @Test
    public void testPutLdpResource() {
        final LdpPutHandler putHandler = new LdpPutHandler(mockResourceService, mockIoService, mockConstraintService,
                mockBinaryService, mockRequest);
        putHandler.setPath("partition/resource");
        putHandler.setBaseUrl(baseUrl);
        putHandler.setAcceptableTypes(singletonList(TEXT_TURTLE_TYPE));
        putHandler.setContentType(TEXT_TURTLE);
        putHandler.setEntity(
                new ByteArrayInputStream("<> <http://purl.org/dc/terms/title> \"A title\" .".getBytes(UTF_8)));
        putHandler.setSession(new HttpSession());
        putHandler.setLink(fromUri(LDP.Resource.getIRIString()).rel("type").build());

        final Response res = putHandler.setResource(mockResource).build();
        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));

        verify(mockBinaryService, never()).setContent(anyString(), any(IRI.class), any(InputStream.class));
        verify(mockIoService).read(any(InputStream.class), eq(baseUrl + "partition/resource"), eq(TURTLE));
    }

    @Test
    public void testPutLdpResource2() {
        final LdpPutHandler putHandler = new LdpPutHandler(mockResourceService, mockIoService, mockConstraintService,
                mockBinaryService, mockRequest);
        putHandler.setPath("partition/resource");
        putHandler.setBaseUrl(baseUrl);
        putHandler.setAcceptableTypes(singletonList(TEXT_TURTLE_TYPE));
        putHandler.setContentType(TEXT_TURTLE);
        putHandler.setEntity(
                new ByteArrayInputStream("<> <http://purl.org/dc/terms/title> \"A title\" .".getBytes(UTF_8)));
        putHandler.setSession(new HttpSession());
        putHandler.setLink(fromUri(LDP.Container.getIRIString()).rel("type").build());

        final Response res = putHandler.setResource(mockResource).build();
        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));

        verify(mockBinaryService, never()).setContent(anyString(), any(IRI.class), any(InputStream.class));
        verify(mockIoService).read(any(InputStream.class), eq(baseUrl + "partition/resource"), eq(TURTLE));
    }

    @Test
    public void testPutLdpResource3() {
        final LdpPutHandler putHandler = new LdpPutHandler(mockResourceService, mockIoService, mockConstraintService,
                mockBinaryService, mockRequest);
        putHandler.setPath("partition/resource");
        putHandler.setBaseUrl(baseUrl);
        putHandler.setContentType(TEXT_PLAIN);
        putHandler.setEntity(new ByteArrayInputStream("Some data".getBytes(UTF_8)));
        putHandler.setSession(new HttpSession());
        putHandler.setLink(fromUri(LDP.Resource.getIRIString()).rel("type").build());

        final Response res = putHandler.setResource(mockResource).build();
        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));

        verify(mockBinaryService).setContent(eq("partition"), any(IRI.class), any(InputStream.class));
        verify(mockIoService, never()).read(any(InputStream.class), anyString(), any(RDFSyntax.class));
        verify(mockConstraintService, never()).constrainedBy(any(IRI.class), anyString(), any(Graph.class));
    }

    @Test
    public void testPutLdpResource4() {
        final LdpPutHandler putHandler = new LdpPutHandler(mockResourceService, mockIoService, mockConstraintService,
                mockBinaryService, mockRequest);
        putHandler.setPath("partition/resource");
        putHandler.setBaseUrl(baseUrl);
        putHandler.setContentType(TEXT_PLAIN);
        putHandler.setEntity(new ByteArrayInputStream("Some data".getBytes(UTF_8)));
        putHandler.setSession(new HttpSession());
        putHandler.setLink(fromUri(LDP.NonRDFSource.getIRIString()).rel("type").build());

        final Response res = putHandler.setResource(mockResource).build();
        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));

        verify(mockBinaryService).setContent(eq("partition"), any(IRI.class), any(InputStream.class));
        verify(mockIoService, never()).read(any(InputStream.class), anyString(), any(RDFSyntax.class));
        verify(mockConstraintService, never()).constrainedBy(any(IRI.class), anyString(), any(Graph.class));
    }

    @Test
    public void testError() {
        when(mockResourceService.put(eq(rdf.createIRI(TRELLIS_PREFIX + "partition/resource")), any(Dataset.class)))
            .thenReturn(false);

        final LdpPutHandler putHandler = new LdpPutHandler(mockResourceService, mockIoService, mockConstraintService,
                mockBinaryService, mockRequest);
        putHandler.setPath("partition/resource");
        putHandler.setBaseUrl(baseUrl);
        putHandler.setSession(new HttpSession());
        putHandler.setContentType(TEXT_PLAIN);
        putHandler.setEntity(new ByteArrayInputStream("Some data".getBytes(UTF_8)));
        putHandler.setLink(fromUri(LDP.NonRDFSource.getIRIString()).rel("type").build());

        putHandler.setContentType("text/turtle");
        putHandler.setAcceptableTypes(singletonList(TEXT_TURTLE_TYPE));

        final Response res = putHandler.setResource().build();
        assertEquals(INTERNAL_SERVER_ERROR, res.getStatusInfo());
    }

    private static Predicate<Link> hasLink(final IRI iri, final String rel) {
        return link -> rel.equals(link.getRel()) && iri.getIRIString().equals(link.getUri().toString());
    }

    private static Predicate<Link> hasType(final IRI iri) {
        return hasLink(iri, "type");
    }
}
