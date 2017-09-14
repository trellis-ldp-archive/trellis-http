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
import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.ofInstant;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Date.from;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.HEAD;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;
import static javax.ws.rs.core.HttpHeaders.ALLOW;
import static javax.ws.rs.core.HttpHeaders.VARY;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_HTML_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static javax.ws.rs.core.MediaType.WILDCARD_TYPE;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.notModified;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.rdf.api.RDFSyntax.RDFA_HTML;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_PATCH;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_POST;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_RANGES;
import static org.trellisldp.http.domain.HttpConstants.MEMENTO_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.PATCH;
import static org.trellisldp.http.domain.HttpConstants.PREFER;
import static org.trellisldp.http.domain.HttpConstants.PREFERENCE_APPLIED;
import static org.trellisldp.http.domain.HttpConstants.RANGE;
import static org.trellisldp.http.domain.HttpConstants.WANT_DIGEST;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON_TYPE;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_N_TRIPLES;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_SPARQL_UPDATE;
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE_TYPE;
import static org.trellisldp.spi.RDFUtils.getInstance;
import static org.trellisldp.vocabulary.JSONLD.compacted;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.function.Predicate;

import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.trellisldp.api.Binary;
import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.OA;
import org.trellisldp.vocabulary.SKOS;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
@RunWith(MockitoJUnitRunner.class)
public class GetHandlerTest {

    private final static Instant time = ofEpochSecond(1496262729);
    private final static Instant binaryTime = ofEpochSecond(1496262750);
    private final static String baseUrl = "http://localhost:8080/repo/";
    private final static RDF rdf = getInstance();

    private Binary testBinary = new Binary(rdf.createIRI("file:testResource.txt"), binaryTime, "text/plain", 100L);

    @Mock
    private ResourceService mockResourceService;

    @Mock
    private IOService mockIoService;

    @Mock
    private BinaryService mockBinaryService;

    @Mock
    private Resource mockResource;

    @Mock
    private Request mockRequest;

    @Mock
    private HttpHeaders mockHeaders;

    @Mock
    private LdpRequest mockLdpRequest;

    @Before
    public void setUp() {
        when(mockResource.getMementos()).thenReturn(emptyList());
        when(mockResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockResource.getModified()).thenReturn(time);
        when(mockResource.getBinary()).thenReturn(empty());
        when(mockResource.isMemento()).thenReturn(false);
        when(mockResource.getInbox()).thenReturn(empty());
        when(mockResource.getAnnotationService()).thenReturn(empty());
        when(mockResource.getTypes()).thenReturn(emptyList());

        when(mockLdpRequest.getRequest()).thenReturn(mockRequest);
        when(mockLdpRequest.getPath()).thenReturn("");
        when(mockLdpRequest.getPartition()).thenReturn("partition");
        when(mockLdpRequest.getBaseUrl(any())).thenReturn(baseUrl);
        when(mockLdpRequest.getHeaders()).thenReturn(mockHeaders);
    }

    @Test
    public void testGetLdprs() {
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(singletonList(TEXT_TURTLE_TYPE));

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(PREFERENCE_APPLIED));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertTrue(TEXT_TURTLE_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(TEXT_TURTLE_TYPE));
        assertEquals(from(time), res.getLastModified());

        final String allow = res.getHeaderString(ALLOW);
        assertTrue(allow.contains(GET));
        assertTrue(allow.contains(HEAD));
        assertTrue(allow.contains(OPTIONS));
        assertTrue(allow.contains(PUT));
        assertTrue(allow.contains(DELETE));
        assertTrue(allow.contains(PATCH));
        assertFalse(allow.contains(POST));

        final EntityTag etag = res.getEntityTag();
        assertTrue(etag.isWeak());
        assertEquals(md5Hex(time + baseUrl + "partition"), etag.getValue());

        final List<Object> varies = res.getHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));
    }

    @Test
    public void testGetPreferLdprs() {
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(singletonList(TEXT_TURTLE_TYPE));
        when(mockLdpRequest.getPrefer())
            .thenReturn(Prefer.valueOf("return=representation; include=\"http://www.w3.org/ns/ldp#PreferContainment"));

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertEquals("return=representation", res.getHeaderString(PREFERENCE_APPLIED));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertTrue(TEXT_TURTLE_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(TEXT_TURTLE_TYPE));
        assertEquals(from(time), res.getLastModified());
    }

    @Test
    public void testGetVersionedLdprs() {
        when(mockResource.isMemento()).thenReturn(true);
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(singletonList(TEXT_TURTLE_TYPE));

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(PREFERENCE_APPLIED));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertTrue(TEXT_TURTLE_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(TEXT_TURTLE_TYPE));
        assertEquals(from(time), res.getLastModified());
        assertEquals(ofInstant(time, UTC).format(RFC_1123_DATE_TIME), res.getHeaderString(MEMENTO_DATETIME));

        final String allow = res.getHeaderString(ALLOW);
        assertTrue(allow.contains(GET));
        assertTrue(allow.contains(HEAD));
        assertTrue(allow.contains(OPTIONS));
        assertFalse(allow.contains(PUT));
        assertFalse(allow.contains(DELETE));
        assertFalse(allow.contains(PATCH));
        assertFalse(allow.contains(POST));

        final EntityTag etag = res.getEntityTag();
        assertTrue(etag.isWeak());
        assertEquals(md5Hex(time + baseUrl + "partition"), etag.getValue());

        final List<Object> varies = res.getHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertFalse(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));
    }

    @Test(expected = WebApplicationException.class)
    public void testCache() {
        when(mockRequest.evaluatePreconditions(eq(from(time)), any(EntityTag.class)))
                .thenReturn(notModified());
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(singletonList(TEXT_TURTLE_TYPE));

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        getHandler.getRepresentation(mockResource);
    }

    @Test(expected = WebApplicationException.class)
    public void testCacheLdpNr() {
        when(mockResource.getBinary()).thenReturn(of(testBinary));
        when(mockResource.getInteractionModel()).thenReturn(LDP.NonRDFSource);
        when(mockRequest.evaluatePreconditions(eq(from(binaryTime)), any(EntityTag.class)))
                .thenReturn(notModified());
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(singletonList(WILDCARD_TYPE));

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        getHandler.getRepresentation(mockResource);
    }

    @Test
    public void testExtraLinks() {
        final String inbox = "http://ldn.example.com/inbox";
        final String annService = "http://annotation.example.com/resource";

        when(mockResource.getAnnotationService()).thenReturn(of(rdf.createIRI(annService)));
        when(mockResource.getInbox()).thenReturn(of(rdf.createIRI(inbox)));
        when(mockResource.getTypes()).thenReturn(singletonList(SKOS.Concept));
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(singletonList(TEXT_TURTLE_TYPE));

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(SKOS.Concept)));
        assertTrue(res.getLinks().stream().anyMatch(hasLink(rdf.createIRI(inbox), "inbox")));
        assertTrue(res.getLinks().stream().anyMatch(hasLink(rdf.createIRI(annService),
                        OA.annotationService.getIRIString())));
    }

    @Test(expected = NotAcceptableException.class)
    public void testNotAcceptableLdprs() {
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(singletonList(APPLICATION_JSON_TYPE));

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        getHandler.getRepresentation(mockResource);
    }

    @Test
    public void testMinimalLdprs() {
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(singletonList(APPLICATION_LD_JSON_TYPE));
        when(mockLdpRequest.getPrefer()).thenReturn(Prefer.valueOf("return=minimal"));

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertEquals("return=minimal", res.getHeaderString(PREFERENCE_APPLIED));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertEquals(from(time), res.getLastModified());

        final String allow = res.getHeaderString(ALLOW);
        assertTrue(allow.contains(GET));
        assertTrue(allow.contains(HEAD));
        assertTrue(allow.contains(OPTIONS));
        assertTrue(allow.contains(PUT));
        assertTrue(allow.contains(DELETE));
        assertTrue(allow.contains(PATCH));
        assertFalse(allow.contains(POST));

        final EntityTag etag = res.getEntityTag();
        assertTrue(etag.isWeak());
        assertEquals(md5Hex(time + baseUrl + "partition"), etag.getValue());

        final List<Object> varies = res.getHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));
    }

    @Test
    public void testGetLdpc() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(singletonList(
                    MediaType.valueOf(APPLICATION_LD_JSON + "; profile=\"" + compacted.getIRIString() + "\"")));

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(PREFERENCE_APPLIED));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertEquals(from(time), res.getLastModified());
        assertFalse(res.getLinks().stream().anyMatch(link -> link.getRel().equals("describes")));
        assertFalse(res.getLinks().stream().anyMatch(link -> link.getRel().equals("describedby")));
        assertFalse(res.getLinks().stream().anyMatch(link -> link.getRel().equals("canonical")));

        final String acceptPost = res.getHeaderString(ACCEPT_POST);
        assertNotNull(acceptPost);
        assertTrue(acceptPost.contains("text/turtle"));
        assertTrue(acceptPost.contains(APPLICATION_LD_JSON));
        assertTrue(acceptPost.contains(APPLICATION_N_TRIPLES));

        final String allow = res.getHeaderString(ALLOW);
        assertTrue(allow.contains(GET));
        assertTrue(allow.contains(HEAD));
        assertTrue(allow.contains(OPTIONS));
        assertTrue(allow.contains(PUT));
        assertTrue(allow.contains(DELETE));
        assertTrue(allow.contains(PATCH));
        assertTrue(allow.contains(POST));

        final EntityTag etag = res.getEntityTag();
        assertTrue(etag.isWeak());
        assertEquals(md5Hex(time + baseUrl + "partition"), etag.getValue());

        final List<Object> varies = res.getHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));
    }

    @Test
    public void testGetHTML() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(singletonList(MediaType.valueOf(RDFA_HTML.mediaType)));

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(PREFERENCE_APPLIED));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertTrue(TEXT_HTML_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(TEXT_HTML_TYPE));
    }

    @Test
    public void testGetBinaryDescription() {
        when(mockResource.getBinary()).thenReturn(of(testBinary));
        when(mockResource.getInteractionModel()).thenReturn(LDP.NonRDFSource);
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(singletonList(TEXT_TURTLE_TYPE));

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertTrue(res.getMediaType().isCompatible(TEXT_TURTLE_TYPE));
        assertEquals(-1, res.getLength());
        assertEquals(from(time), res.getLastModified());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream()
                .anyMatch(link -> link.getRel().equals("describes") &&
                    !link.getUri().toString().endsWith("#description")));
        assertTrue(res.getLinks().stream()
                .anyMatch(link -> link.getRel().equals("canonical") &&
                    link.getUri().toString().endsWith("#description")));
    }

    @Test
    public void testGetBinary() throws IOException {
        when(mockResource.getBinary()).thenReturn(of(testBinary));
        when(mockResource.getInteractionModel()).thenReturn(LDP.NonRDFSource);

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertTrue(res.getMediaType().isCompatible(TEXT_PLAIN_TYPE));
        assertEquals(-1, res.getLength());
        assertEquals(from(binaryTime), res.getLastModified());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertTrue(res.getLinks().stream()
                .anyMatch(link -> link.getRel().equals("describedby") &&
                    link.getUri().toString().endsWith("#description")));
        assertTrue(res.getLinks().stream()
                .anyMatch(link -> link.getRel().equals("canonical") &&
                    !link.getUri().toString().endsWith("#description")));
    }

    @Test
    public void testGetAcl() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockResource.hasAcl()).thenReturn(true);
        when(mockHeaders.getAcceptableMediaTypes()).thenReturn(singletonList(TEXT_TURTLE_TYPE));
        when(mockLdpRequest.getExt()).thenReturn("acl");

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(OK, res.getStatusInfo());

        final String allow = res.getHeaderString(ALLOW);
        assertTrue(allow.contains(GET));
        assertTrue(allow.contains(HEAD));
        assertTrue(allow.contains(OPTIONS));
        assertFalse(allow.contains(PUT));
        assertFalse(allow.contains(DELETE));
        assertTrue(allow.contains(PATCH));
        assertFalse(allow.contains(POST));
    }

    @Test(expected = WebApplicationException.class)
    public void testGetDeleted() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.Resource);
        when(mockResource.getTypes()).thenReturn(singletonList(Trellis.DeletedResource));

        final GetHandler getHandler = new GetHandler(emptyMap(), mockLdpRequest, mockResourceService,
                mockIoService, mockBinaryService);

        getHandler.getRepresentation(mockResource);
    }

    private static Predicate<Link> hasLink(final IRI iri, final String rel) {
        return link -> rel.equals(link.getRel()) && iri.getIRIString().equals(link.getUri().toString());
    }

    private static Predicate<Link> hasType(final IRI iri) {
        return hasLink(iri, "type");
    }
}
