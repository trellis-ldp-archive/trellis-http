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

import static java.lang.String.join;
import static java.time.Instant.ofEpochSecond;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.HttpHeaders.WWW_AUTHENTICATE;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;
import static javax.ws.rs.core.SecurityContext.BASIC_AUTH;
import static javax.ws.rs.core.SecurityContext.DIGEST_AUTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.trellisldp.http.domain.HttpConstants.APPLICATION_LINK_FORMAT;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_SPARQL_UPDATE_TYPE;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_N_TRIPLES_TYPE;
import static org.trellisldp.spi.RDFUtils.getInstance;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.BlankNode;
import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.RDF;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import org.trellisldp.api.Resource;
import org.trellisldp.io.JenaIOService;
import org.trellisldp.spi.AccessControlService;
import org.trellisldp.spi.BinaryService;
import org.trellisldp.spi.ConstraintService;
import org.trellisldp.spi.IOService;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.Session;
import org.trellisldp.vocabulary.ACL;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
public class LdpUnauthorizedResourceTest extends JerseyTest {

    private final static IOService ioService = new JenaIOService(null);

    private final static Instant time = ofEpochSecond(1496262729);

    private final static RDF rdf = getInstance();

    private final static IRI identifier = rdf.createIRI("trellis:repo1/resource");

    private final static IRI agent = rdf.createIRI("user:agent");

    private final static String BNODE_PREFIX = "trellis:bnode/";

    private final static BlankNode bnode = rdf.createBlankNode();

    @Mock
    private ResourceService mockResourceService;

    @Mock
    private ConstraintService mockConstraintService;

    @Mock
    private BinaryService mockBinaryService;

    @Mock
    private Resource mockResource;

    @Mock
    private Resource mockVersionedResource;

    @Mock
    private AccessControlService mockAccessControlService;

    @Override
    public Application configure() {

        final Map<String, String> partitions = new HashMap<>();
        partitions.put("repo1", "http://example.org/");
        partitions.put("repo2", "http://example.org/");
        partitions.put("repo3", "http://example.org/");
        partitions.put("repo4", "http://example.org/");

        // Junit runner doesn't seem to work very well with JerseyTest
        initMocks(this);

        final ResourceConfig config = new ResourceConfig();
        config.register(new LdpResource(mockResourceService, ioService, mockConstraintService, mockBinaryService,
                    partitions, emptyList()));
        config.register(new TestAuthenticationFilter("testUser", "group"));
        config.register(new WebAcFilter(partitions.entrySet().stream().map(Map.Entry::getKey).collect(toSet()),
                    asList(BASIC_AUTH, DIGEST_AUTH), mockAccessControlService));
        return config;
    }

    @Before
    public void setUpMocks() {
        when(mockResourceService.get(any(IRI.class), any(Instant.class)))
            .thenReturn(Optional.of(mockVersionedResource));
        when(mockResourceService.get(any(IRI.class))).thenReturn(Optional.of(mockResource));

        when(mockAccessControlService.anyMatch(any(Session.class), any(IRI.class), any()))
            .thenReturn(false);

        when(mockVersionedResource.getMementos()).thenReturn(Stream.empty());
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockVersionedResource.getModified()).thenReturn(time);
        when(mockVersionedResource.getBinary()).thenReturn(Optional.empty());
        when(mockVersionedResource.isMemento()).thenReturn(true);
        when(mockVersionedResource.getIdentifier()).thenReturn(identifier);
        when(mockVersionedResource.getInbox()).thenReturn(Optional.empty());
        when(mockVersionedResource.getAnnotationService()).thenReturn(Optional.empty());
        when(mockVersionedResource.getTypes()).thenAnswer(x -> Stream.empty());

        when(mockResource.getMementos()).thenReturn(Stream.empty());
        when(mockResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockResource.getModified()).thenReturn(time);
        when(mockResource.getBinary()).thenReturn(Optional.empty());
        when(mockResource.isMemento()).thenReturn(false);
        when(mockResource.getIdentifier()).thenReturn(identifier);
        when(mockResource.getInbox()).thenReturn(Optional.empty());
        when(mockResource.getAnnotationService()).thenReturn(Optional.empty());
        when(mockResource.getTypes()).thenAnswer(x -> Stream.empty());

        when(mockResourceService.unskolemize(any(IRI.class)))
            .thenAnswer(inv -> {
                final String uri = ((IRI) inv.getArgument(0)).getIRIString();
                if (uri.startsWith(BNODE_PREFIX)) {
                    return bnode;
                }
                return (IRI) inv.getArgument(0);
            });

        when(mockResourceService.unskolemize(any(Literal.class))).then(returnsFirstArg());
        when(mockResourceService.put(any(IRI.class), any(Dataset.class))).thenReturn(true);
        when(mockResourceService.skolemize(any(Literal.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(IRI.class))).then(returnsFirstArg());
        when(mockResourceService.skolemize(any(BlankNode.class)))
            .thenAnswer(inv -> rdf.createIRI(BNODE_PREFIX + ((BlankNode) inv.getArgument(0)).uniqueReference()));
        when(mockResource.stream()).thenReturn(Stream.of(
                rdf.createQuad(Trellis.PreferUserManaged, identifier, DC.title, rdf.createLiteral("A title")),
                rdf.createQuad(Trellis.PreferAccessControl, identifier, ACL.mode, ACL.Control)));
    }

    @Test
    public void testGetJson() {
        final Response res = target("/repo1/resource").request().accept("application/ld+json").get();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testDefaultType() {
        final Response res = target("repo1/resource").request().get();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testTrailingSlash() {
        final Response res = target("repo1/resource/").request().get();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testOptions1() {
        final Response res = target("repo1/resource").request().options();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testOptions2() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.Container);
        final Response res = target("repo1/resource").request().options();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testGetJsonCompact() {
        final Response res = target("repo1/resource").request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testGetTimeMapLink() {
        final Response res = target("repo1/resource").queryParam("ext", "timemap").request()
            .accept(APPLICATION_LINK_FORMAT).get();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testGetTimeMapJson() {
        final Response res = target("repo1/resource").queryParam("ext", "timemap").request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testGetVersionJson() {
        final Response res = target("repo1/resource").queryParam("version", 1496262729).request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testGetAclJsonCompact() {
        final Response res = target("repo1/resource").queryParam("ext", "acl").request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testPatch1() {
        final Response res = target("repo1/resource").queryParam("ext", "acl").request()
            .method("PATCH", entity("INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}",
                        APPLICATION_SPARQL_UPDATE_TYPE));

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testPatch2() {
        final Response res = target("repo1/resource").request()
            .method("PATCH", entity("INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}",
                        APPLICATION_SPARQL_UPDATE_TYPE));

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testPost1() {
        final Response res = target("repo1/resource").queryParam("ext", "acl").request()
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" . ",
                    APPLICATION_N_TRIPLES_TYPE));

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testPost2() {
        final Response res = target("repo1/resource").request()
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" . ",
                    APPLICATION_N_TRIPLES_TYPE));

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testPut1() {
        final Response res = target("repo1/resource").queryParam("ext", "acl").request()
            .put(entity("<> <http://purl.org/dc/terms/title> \"A title\" . ",
                    APPLICATION_N_TRIPLES_TYPE));

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testPut2() {
        final Response res = target("repo1/resource").request()
            .put(entity("<> <http://purl.org/dc/terms/title> \"A title\" . ",
                    APPLICATION_N_TRIPLES_TYPE));

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testDelete1() {
        final Response res = target("repo1/resource").queryParam("ext", "acl").request().delete();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testDelete2() {
        final Response res = target("repo1/resource").request().delete();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }

    @Test
    public void testDelete3() {
        final Response res = target("repo1/resource/").request().delete();

        assertEquals(UNAUTHORIZED, res.getStatusInfo());
        assertEquals(join(",", BASIC_AUTH, DIGEST_AUTH), res.getHeaderString(WWW_AUTHENTICATE));
        assertEquals(2L, res.getHeaders().get(WWW_AUTHENTICATE).size());
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(DIGEST_AUTH));
        assertTrue(res.getHeaders().get(WWW_AUTHENTICATE).contains(BASIC_AUTH));
    }
}
