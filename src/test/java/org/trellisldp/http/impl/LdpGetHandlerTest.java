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

import static java.util.Date.from;
import static java.time.Instant.ofEpochSecond;
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
import static org.apache.commons.codec.digest.DigestUtils.md5Hex;
import static org.apache.commons.rdf.api.RDFSyntax.JSONLD;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_PATCH;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_POST;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_RANGES;
import static org.trellisldp.http.domain.HttpConstants.PREFER;
import static org.trellisldp.http.domain.HttpConstants.PREFERENCE_APPLIED;
import static org.trellisldp.http.domain.HttpConstants.RANGE;
import static org.trellisldp.http.domain.HttpConstants.WANT_DIGEST;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON_TYPE;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_SPARQL_UPDATE;
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE_TYPE;

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
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
@RunWith(MockitoJUnitRunner.class)
public class LdpGetHandlerTest {

    private final static Instant time = ofEpochSecond(1496262729);
    private final static String baseUrl = "http://localhost:8080/repo";

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
        when(mockResource.getTypes()).thenAnswer(x -> Stream.empty());
        when(mockResource.getMementos()).thenReturn(Stream.empty());
        when(mockResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockResource.getModified()).thenReturn(time);
        when(mockResource.getBlob()).thenReturn(Optional.empty());
        when(mockResource.isMemento()).thenReturn(false);
        when(mockResource.getInbox()).thenReturn(Optional.empty());
        when(mockResource.getAnnotationService()).thenReturn(Optional.empty());
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
        assertTrue(res.getLinks().stream().anyMatch(hasLdpType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasLdpType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasLdpType(LDP.Container)));
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
        assertTrue(res.getLinks().stream().anyMatch(hasLdpType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasLdpType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasLdpType(LDP.Container)));
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
    public void testGetDeleted() {
        when(mockResource.getTypes()).thenReturn(Stream.of(Trellis.DeletedResource));

        final LdpGetHandler getHandler = new LdpGetHandler(mockResourceService, mockIoService,
                mockBinaryService, mockRequest);
        getHandler.setPath("/");
        getHandler.setBaseUrl(baseUrl);
        getHandler.setSyntax(TURTLE);

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(GONE, res.getStatusInfo());
    }

    private Predicate<Link> hasLdpType(final IRI type) {
        return link -> "type".equals(link.getRel()) && type.getIRIString().equals(link.getUri().toString());
    }
}
