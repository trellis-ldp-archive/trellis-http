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
import static java.util.Date.from;
import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.HEAD;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;
import static javax.ws.rs.core.HttpHeaders.ALLOW;
import static javax.ws.rs.core.HttpHeaders.VARY;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static javax.ws.rs.core.Response.Status.NOT_MODIFIED;
import static javax.ws.rs.core.Response.notModified;
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.rdf.api.RDFSyntax.JSONLD;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_PATCH;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_POST;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_RANGES;
import static org.trellisldp.http.domain.HttpConstants.MEMENTO_DATETIME;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.trellisldp.api.Resource;
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
public class LdpGetHandlerTest {

    private final static Instant time = ofEpochSecond(1496262729);
    private final static String baseUrl = "http://localhost:8080/repo";
    private final static RDF rdf = getInstance();

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

    @Before
    public void setUp() {
        when(mockResource.getMementos()).thenReturn(Stream.empty());
        when(mockResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockResource.getModified()).thenReturn(time);
        when(mockResource.getBlob()).thenReturn(Optional.empty());
        when(mockResource.isMemento()).thenReturn(false);
        when(mockResource.getInbox()).thenReturn(Optional.empty());
        when(mockResource.getAnnotationService()).thenReturn(Optional.empty());
        when(mockResource.getTypes()).thenAnswer(x -> Stream.empty());
        when(mockResource.stream()).thenReturn(Stream.empty());
    }

    @Test
    public void testGetLdprs() {
        final LdpGetHandler getHandler = new LdpGetHandler(mockResourceService, mockIoService,
                mockBinaryService, mockRequest);
        getHandler.setPath("/");
        getHandler.setBaseUrl(baseUrl);
        getHandler.setSyntax(TURTLE);

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
        assertTrue(allow.contains("PATCH"));
        assertFalse(allow.contains(POST));

        final EntityTag etag = res.getEntityTag();
        assertTrue(etag.isWeak());
        assertEquals(md5Hex(time + baseUrl + "/"), etag.getValue());

        final List<Object> varies = res.getHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));
    }

    @Test
    public void testGetPreferLdprs() {
        final LdpGetHandler getHandler = new LdpGetHandler(mockResourceService, mockIoService,
                mockBinaryService, mockRequest);
        getHandler.setPath("/");
        getHandler.setBaseUrl(baseUrl);
        getHandler.setSyntax(TURTLE);
        getHandler.setWantDigest(null);
        getHandler.setRange(null);
        getHandler.setPrefer(new Prefer("return=representation; include=\"http://www.w3.org/ns/ldp#PreferContainment"));

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

        final LdpGetHandler getHandler = new LdpGetHandler(mockResourceService, mockIoService,
                mockBinaryService, mockRequest);
        getHandler.setPath("/");
        getHandler.setBaseUrl(baseUrl);
        getHandler.setSyntax(TURTLE);

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
        assertTrue(allow.contains(PUT));
        assertTrue(allow.contains(DELETE));
        assertTrue(allow.contains("PATCH"));
        assertFalse(allow.contains(POST));

        final EntityTag etag = res.getEntityTag();
        assertTrue(etag.isWeak());
        assertEquals(md5Hex(time + baseUrl + "/"), etag.getValue());

        final List<Object> varies = res.getHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertFalse(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));
    }

    @Test
    public void testCache() {
        when(mockRequest.evaluatePreconditions(eq(from(time)), any(EntityTag.class)))
                .thenReturn(notModified());

        final LdpGetHandler getHandler = new LdpGetHandler(mockResourceService, mockIoService,
                mockBinaryService, mockRequest);
        getHandler.setPath("/");
        getHandler.setBaseUrl(baseUrl);
        getHandler.setSyntax(TURTLE);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(NOT_MODIFIED, res.getStatusInfo());
    }

    @Test
    public void testCacheError() {
        when(mockRequest.evaluatePreconditions(eq(from(time)), any(EntityTag.class)))
                .thenThrow(new IllegalArgumentException());

        final LdpGetHandler getHandler = new LdpGetHandler(mockResourceService, mockIoService,
                mockBinaryService, mockRequest);
        getHandler.setPath("/");
        getHandler.setBaseUrl(baseUrl);
        getHandler.setSyntax(TURTLE);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(OK, res.getStatusInfo());
    }

    @Test
    public void testExtraLinks() {
        final String inbox = "http://ldn.example.com/inbox";
        final String annService = "http://annotation.example.com/resource";

        when(mockResource.getAnnotationService()).thenReturn(Optional.of(rdf.createIRI(annService)));
        when(mockResource.getInbox()).thenReturn(Optional.of(rdf.createIRI(inbox)));
        when(mockResource.getTypes()).thenAnswer(x -> Stream.of(SKOS.Concept));

        final LdpGetHandler getHandler = new LdpGetHandler(mockResourceService, mockIoService,
                mockBinaryService, mockRequest);
        getHandler.setPath("/");
        getHandler.setBaseUrl(baseUrl);
        getHandler.setSyntax(TURTLE);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(SKOS.Concept)));
        assertTrue(res.getLinks().stream().anyMatch(hasLink(rdf.createIRI(inbox), "inbox")));
        assertTrue(res.getLinks().stream().anyMatch(hasLink(rdf.createIRI(annService),
                        OA.annotationService.getIRIString())));
    }

    @Test
    public void testNotAcceptableLdprs() {
        final LdpGetHandler getHandler = new LdpGetHandler(mockResourceService, mockIoService,
                mockBinaryService, mockRequest);
        getHandler.setPath("/");
        getHandler.setBaseUrl(baseUrl);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(NOT_ACCEPTABLE, res.getStatusInfo());
    }

    @Test
    public void testMinimalLdprs() {
        final LdpGetHandler getHandler = new LdpGetHandler(mockResourceService, mockIoService,
                mockBinaryService, mockRequest);
        getHandler.setPath("/");
        getHandler.setBaseUrl(baseUrl);
        getHandler.setSyntax(JSONLD);
        getHandler.setPrefer(new Prefer("return=minimal"));

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
        assertTrue(allow.contains("PATCH"));
        assertFalse(allow.contains(POST));

        final EntityTag etag = res.getEntityTag();
        assertTrue(etag.isWeak());
        assertEquals(md5Hex(time + baseUrl + "/"), etag.getValue());

        final List<Object> varies = res.getHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));
    }

    @Test
    public void testGetLdpc() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.Container);

        final LdpGetHandler getHandler = new LdpGetHandler(mockResourceService, mockIoService,
                mockBinaryService, mockRequest);
        getHandler.setPath("/");
        getHandler.setBaseUrl(baseUrl);
        getHandler.setSyntax(JSONLD);
        getHandler.setProfile(compacted);

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
        assertTrue(allow.contains("PATCH"));
        assertTrue(allow.contains(POST));

        final EntityTag etag = res.getEntityTag();
        assertTrue(etag.isWeak());
        assertEquals(md5Hex(time + baseUrl + "/"), etag.getValue());

        final List<Object> varies = res.getHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));
    }

    @Test
    public void testGetDeleted() {
        when(mockResource.getTypes()).thenAnswer(x -> Stream.of(Trellis.DeletedResource));

        final LdpGetHandler getHandler = new LdpGetHandler(mockResourceService, mockIoService,
                mockBinaryService, mockRequest);
        getHandler.setPath("/");
        getHandler.setBaseUrl(baseUrl);
        getHandler.setSyntax(TURTLE);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(GONE, res.getStatusInfo());
    }

    private static Predicate<Link> hasLink(final IRI iri, final String rel) {
        return link -> rel.equals(link.getRel()) && iri.getIRIString().equals(link.getUri().toString());
    }

    private static Predicate<Link> hasType(final IRI iri) {
        return hasLink(iri, "type");
    }
}
