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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Instant.MAX;
import static java.time.Instant.ofEpochSecond;
import static java.time.ZoneOffset.UTC;
import static java.time.ZonedDateTime.parse;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Date.from;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.client.Entity.entity;
import static javax.ws.rs.core.HttpHeaders.CACHE_CONTROL;
import static javax.ws.rs.core.HttpHeaders.LINK;
import static javax.ws.rs.core.HttpHeaders.VARY;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN_TYPE;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.METHOD_NOT_ALLOWED;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.Status.UNSUPPORTED_MEDIA_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_PATCH;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_POST;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_RANGES;
import static org.trellisldp.http.domain.HttpConstants.APPLICATION_LINK_FORMAT;
import static org.trellisldp.http.domain.HttpConstants.DIGEST;
import static org.trellisldp.http.domain.HttpConstants.MEMENTO_DATETIME;
import static org.trellisldp.http.domain.HttpConstants.PREFER;
import static org.trellisldp.http.domain.HttpConstants.RANGE;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.domain.HttpConstants.UPLOADS;
import static org.trellisldp.http.domain.HttpConstants.WANT_DIGEST;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON_TYPE;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_N_TRIPLES;
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE_TYPE;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_SPARQL_UPDATE;
import static org.trellisldp.spi.RDFUtils.getInstance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.commons.rdf.api.BlankNode;
import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Literal;
import org.apache.commons.rdf.api.RDF;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import org.trellisldp.api.Binary;
import org.trellisldp.api.Resource;
import org.trellisldp.api.VersionRange;
import org.trellisldp.http.impl.HttpSession;
import org.trellisldp.io.JenaIOService;
import org.trellisldp.spi.AccessControlService;
import org.trellisldp.spi.AgentService;
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
abstract class AbstractLdpResourceTest extends JerseyTest {

    protected final static IOService ioService = new JenaIOService(null);

    private final static int timestamp = 1496262729;

    private final static Instant time = ofEpochSecond(timestamp);

    private final static ObjectMapper MAPPER = new ObjectMapper();

    private final static RDF rdf = getInstance();

    private final static IRI agent = rdf.createIRI("user:agent");

    private final static String BNODE_PREFIX = "trellis:bnode/";

    private final static String UPLOAD_SESSION_ID = "upload-session-id";

    private final static BlankNode bnode = rdf.createBlankNode();

    private final static String REPO1 = "repo1";
    private final static String REPO2 = "repo2";
    private final static String REPO3 = "repo3";
    private final static String REPO4 = "repo4";
    private final static String BASE_URL = "http://example.org/";

    private final static String RANDOM_VALUE = "randomValue";

    private final static String RESOURCE_PATH = REPO1 + "/resource";

    private final static String CHILD_PATH = RESOURCE_PATH + "/child";

    private final static String BINARY_PATH = REPO1 + "/binary";

    private final static String NON_EXISTENT_PATH = REPO1 + "/nonexistent";

    private final static String DELETED_PATH = REPO1 + "/deleted";

    private final static String USER_DELETED_PATH = REPO1 + "/userdeleted";

    private final static String BINARY_MIME_TYPE = "text/plain";

    private final static Long BINARY_SIZE = 100L;

    private final static IRI identifier = rdf.createIRI(TRELLIS_PREFIX + RESOURCE_PATH);

    private final static IRI root = rdf.createIRI(TRELLIS_PREFIX + REPO1);

    private final static IRI binaryIdentifier = rdf.createIRI(TRELLIS_PREFIX + BINARY_PATH);

    private final static IRI binaryInternalIdentifier = rdf.createIRI("file:some/file");

    private final static IRI nonexistentIdentifier = rdf.createIRI(TRELLIS_PREFIX + NON_EXISTENT_PATH);

    private final static IRI childIdentifier = rdf.createIRI(TRELLIS_PREFIX + CHILD_PATH);

    private final static IRI deletedIdentifier = rdf.createIRI(TRELLIS_PREFIX + DELETED_PATH);

    private final static IRI userDeletedIdentifier = rdf.createIRI(TRELLIS_PREFIX + USER_DELETED_PATH);

    protected final static Map<String, String> partitions = new HashMap<String, String>() { {
        put(REPO1, BASE_URL);
        put(REPO2, BASE_URL);
        put(REPO3, BASE_URL);
        put(REPO4, BASE_URL);
    }};

    @Mock
    protected ResourceService mockResourceService;

    @Mock
    protected ConstraintService mockConstraintService;

    @Mock
    protected BinaryService mockBinaryService;

    @Mock
    protected BinaryService.Resolver mockBinaryResolver;

    @Mock
    protected AccessControlService mockAccessControlService;

    @Mock
    protected AgentService mockAgentService;

    @Mock
    private Resource mockResource, mockVersionedResource, mockBinaryResource, mockDeletedResource,
            mockUserDeletedResource, mockBinaryVersionedResource;

    @Mock
    private Binary mockBinary;

    @Before
    public void setUpMocks() {
        when(mockResourceService.get(any(IRI.class), any(Instant.class)))
            .thenReturn(Optional.of(mockVersionedResource));
        when(mockResourceService.get(eq(identifier))).thenReturn(Optional.of(mockResource));
        when(mockResourceService.get(eq(root))).thenReturn(Optional.of(mockResource));
        when(mockResourceService.get(eq(childIdentifier))).thenReturn(Optional.empty());
        when(mockResourceService.get(eq(childIdentifier), any(Instant.class))).thenReturn(Optional.empty());
        when(mockResourceService.get(eq(binaryIdentifier))).thenReturn(Optional.of(mockBinaryResource));
        when(mockResourceService.get(eq(binaryIdentifier), any(Instant.class)))
            .thenReturn(Optional.of(mockBinaryVersionedResource));
        when(mockResourceService.get(eq(nonexistentIdentifier))).thenReturn(Optional.empty());
        when(mockResourceService.get(eq(nonexistentIdentifier), any(Instant.class))).thenReturn(Optional.empty());
        when(mockResourceService.get(eq(deletedIdentifier))).thenReturn(Optional.of(mockDeletedResource));
        when(mockResourceService.get(eq(deletedIdentifier), any(Instant.class)))
            .thenReturn(Optional.of(mockDeletedResource));
        when(mockResourceService.getIdentifierSupplier()).thenReturn(() -> RANDOM_VALUE);

        when(mockResourceService.get(eq(userDeletedIdentifier))).thenReturn(Optional.of(mockUserDeletedResource));
        when(mockResourceService.get(eq(userDeletedIdentifier), any(Instant.class)))
            .thenReturn(Optional.of(mockUserDeletedResource));

        when(mockAgentService.asAgent(anyString())).thenReturn(agent);
        when(mockAccessControlService.anyMatch(any(Session.class), any(IRI.class), any())).thenReturn(true);
        when(mockAccessControlService.canRead(any(Session.class), any(IRI.class))).thenReturn(true);
        when(mockAccessControlService.canWrite(any(Session.class), any(IRI.class))).thenReturn(true);
        when(mockAccessControlService.canControl(any(Session.class), any(IRI.class))).thenReturn(true);

        when(mockVersionedResource.getMementos()).thenAnswer(x -> Stream.of(
                new VersionRange(ofEpochSecond(timestamp - 2000), ofEpochSecond(timestamp - 1000)),
                new VersionRange(ofEpochSecond(timestamp - 1000), time),
                new VersionRange(time, ofEpochSecond(timestamp + 1000))));
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockVersionedResource.getModified()).thenReturn(time);
        when(mockVersionedResource.getBinary()).thenReturn(Optional.empty());
        when(mockVersionedResource.isMemento()).thenReturn(true);
        when(mockVersionedResource.getIdentifier()).thenReturn(identifier);
        when(mockVersionedResource.getInbox()).thenReturn(Optional.empty());
        when(mockVersionedResource.getAnnotationService()).thenReturn(Optional.empty());
        when(mockVersionedResource.getTypes()).thenAnswer(x -> Stream.empty());

        when(mockBinaryVersionedResource.getMementos()).thenAnswer(x -> Stream.of(
                new VersionRange(ofEpochSecond(timestamp - 2000), ofEpochSecond(timestamp - 1000)),
                new VersionRange(ofEpochSecond(timestamp - 1000), time),
                new VersionRange(time, ofEpochSecond(timestamp + 1000))));
        when(mockBinaryVersionedResource.getInteractionModel()).thenReturn(LDP.NonRDFSource);
        when(mockBinaryVersionedResource.getModified()).thenReturn(time);
        when(mockBinaryVersionedResource.getBinary()).thenReturn(Optional.of(mockBinary));
        when(mockBinaryVersionedResource.isMemento()).thenReturn(true);
        when(mockBinaryVersionedResource.getIdentifier()).thenReturn(binaryIdentifier);
        when(mockBinaryVersionedResource.getInbox()).thenReturn(Optional.empty());
        when(mockBinaryVersionedResource.getAnnotationService()).thenReturn(Optional.empty());
        when(mockBinaryVersionedResource.getTypes()).thenAnswer(x -> Stream.empty());

        when(mockBinaryResource.getMementos()).thenAnswer(x -> Stream.empty());
        when(mockBinaryResource.getInteractionModel()).thenReturn(LDP.NonRDFSource);
        when(mockBinaryResource.getModified()).thenReturn(time);
        when(mockBinaryResource.getBinary()).thenReturn(Optional.of(mockBinary));
        when(mockBinaryResource.isMemento()).thenReturn(false);
        when(mockBinaryResource.getIdentifier()).thenReturn(binaryIdentifier);
        when(mockBinaryResource.getInbox()).thenReturn(Optional.empty());
        when(mockBinaryResource.getAnnotationService()).thenReturn(Optional.empty());
        when(mockBinaryResource.getTypes()).thenAnswer(x -> Stream.empty());

        when(mockBinary.getModified()).thenReturn(time);
        when(mockBinary.getIdentifier()).thenReturn(binaryInternalIdentifier);
        when(mockBinary.getMimeType()).thenReturn(Optional.of(BINARY_MIME_TYPE));
        when(mockBinary.getSize()).thenReturn(Optional.of(BINARY_SIZE));

        when(mockBinaryService.supportedAlgorithms()).thenReturn(new HashSet<>(asList("MD5", "SHA")));
        when(mockBinaryService.digest(eq("MD5"), any(InputStream.class))).thenReturn(Optional.of("md5-digest"));
        when(mockBinaryService.digest(eq("SHA"), any(InputStream.class))).thenReturn(Optional.of("sha1-digest"));
        when(mockBinaryService.getContent(eq(REPO1), eq(binaryInternalIdentifier)))
            .thenAnswer(x -> Optional.of(new ByteArrayInputStream("Some input stream".getBytes(UTF_8))));
        when(mockBinaryService.getResolver(eq(binaryInternalIdentifier))).thenReturn(Optional.of(mockBinaryResolver));
        when(mockBinaryService.getResolverForPartition(eq(REPO1))).thenReturn(Optional.of(mockBinaryResolver));
        when(mockBinaryService.getIdentifierSupplier(eq(REPO1))).thenReturn(() -> RANDOM_VALUE);

        when(mockResource.getMementos()).thenAnswer(x -> Stream.empty());
        when(mockResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockResource.getModified()).thenReturn(time);
        when(mockResource.getBinary()).thenReturn(Optional.empty());
        when(mockResource.isMemento()).thenReturn(false);
        when(mockResource.getIdentifier()).thenReturn(identifier);
        when(mockResource.getInbox()).thenReturn(Optional.empty());
        when(mockResource.getAnnotationService()).thenReturn(Optional.empty());
        when(mockResource.getTypes()).thenAnswer(x -> Stream.empty());

        when(mockDeletedResource.getMementos()).thenAnswer(x -> Stream.empty());
        when(mockDeletedResource.getInteractionModel()).thenReturn(LDP.Resource);
        when(mockDeletedResource.getModified()).thenReturn(time);
        when(mockDeletedResource.getBinary()).thenReturn(Optional.empty());
        when(mockDeletedResource.isMemento()).thenReturn(false);
        when(mockDeletedResource.getIdentifier()).thenReturn(identifier);
        when(mockDeletedResource.getInbox()).thenReturn(Optional.empty());
        when(mockDeletedResource.getAnnotationService()).thenReturn(Optional.empty());
        when(mockDeletedResource.getTypes()).thenAnswer(x -> Stream.of(Trellis.DeletedResource));

        when(mockUserDeletedResource.getMementos()).thenAnswer(x -> Stream.empty());
        when(mockUserDeletedResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockUserDeletedResource.getModified()).thenReturn(time);
        when(mockUserDeletedResource.getBinary()).thenReturn(Optional.empty());
        when(mockUserDeletedResource.isMemento()).thenReturn(false);
        when(mockUserDeletedResource.getIdentifier()).thenReturn(identifier);
        when(mockUserDeletedResource.getInbox()).thenReturn(Optional.empty());
        when(mockUserDeletedResource.getAnnotationService()).thenReturn(Optional.empty());
        when(mockUserDeletedResource.getTypes()).thenAnswer(x -> Stream.of(Trellis.DeletedResource));

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

    /* ******************************* *
     *            GET Tests
     * ******************************* */
    @Test
    public void testGetJson() throws IOException {
        final Response res = target("/" + RESOURCE_PATH).request().accept("application/ld+json").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertTrue(res.hasEntity());

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        final List<String> varies = res.getStringHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        final List<Map<String, Object>> obj = MAPPER.readValue(entity,
                new TypeReference<List<Map<String, Object>>>(){});

        assertEquals(1L, obj.size());

        @SuppressWarnings("unchecked")
        final List<Map<String, String>> titles = (List<Map<String, String>>) obj.get(0)
                .get(DC.title.getIRIString());

        final List<String> titleVals = titles.stream().map(x -> x.get("@value")).collect(toList());

        assertEquals(1L, titleVals.size());
        assertTrue(titleVals.contains("A title"));
    }

    @Test
    public void testGetDefaultType() {
        final Response res = target(RESOURCE_PATH).request().get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(TEXT_TURTLE_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(TEXT_TURTLE_TYPE));
    }

    @Test
    public void testGetRootSlash() {
        final Response res = target(REPO1 + "/").request().get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(TEXT_TURTLE_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(TEXT_TURTLE_TYPE));
    }

    @Test
    public void testGetRoot() {
        final Response res = target(REPO1).request().get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(TEXT_TURTLE_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(TEXT_TURTLE_TYPE));
    }

    @Test
    public void testGetDatetime() {
        final Response res = target(RESOURCE_PATH).request()
            .header(ACCEPT_DATETIME, RFC_1123_DATE_TIME.withZone(UTC).format(time)).get();

        assertEquals(OK, res.getStatusInfo());
        assertNotNull(res.getHeaderString(MEMENTO_DATETIME));
        assertEquals(time, parse(res.getHeaderString(MEMENTO_DATETIME), RFC_1123_DATE_TIME).toInstant());

        // Jersey's client doesn't parse complex link headers correctly
        final List<Link> links = res.getStringHeaders().get(LINK).stream().map(Link::valueOf).collect(toList());
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496260729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 1000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496261729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(time).equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496262729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timemap") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("from")) &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp + 1000))
                        .equals(l.getParams().get("until")) &&
                    APPLICATION_LINK_FORMAT.equals(l.getType()) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?ext=timemap")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timegate") &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("original") &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertFalse(links.stream().anyMatch(hasLink(rdf.createIRI(BASE_URL + RESOURCE_PATH + "?ext=upload"),
                        Trellis.multipartUploadService.getIRIString())));
        assertTrue(links.stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(links.stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(links.stream().anyMatch(hasType(LDP.Container)));
    }

    @Test
    public void testGetTrailingSlash() {
        final Response res = target(RESOURCE_PATH + "/").request().get();

        assertEquals(OK, res.getStatusInfo());
        assertEquals(from(time), res.getLastModified());
        assertTrue(res.getLinks().stream().anyMatch(l ->
                    l.getRel().contains("timegate") && l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(res.getLinks().stream().anyMatch(l ->
                    l.getRel().contains("original") && l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
    }

    @Test
    public void testGetBinaryDescription() {
        final Response res = target(BINARY_PATH).request().accept("text/turtle").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertTrue(res.getAllowedMethods().contains("POST"));

        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));

        assertTrue(res.getMediaType().isCompatible(TEXT_TURTLE_TYPE));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));

        final List<String> varies = res.getStringHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));
    }

    @Test
    public void testGetBinary() throws IOException {
        final Response res = target(BINARY_PATH).request().get();

        assertEquals(OK, res.getStatusInfo());
        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertFalse(res.getLinks().stream().anyMatch(hasLink(rdf.createIRI(BASE_URL + BINARY_PATH + "?ext=upload"),
                        Trellis.multipartUploadService.getIRIString())));

        assertTrue(res.getMediaType().isCompatible(TEXT_PLAIN_TYPE));
        assertNotNull(res.getHeaderString(ACCEPT_RANGES));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));

        final List<String> varies = res.getStringHeaders().get(VARY);
        assertTrue(varies.contains(RANGE));
        assertTrue(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertFalse(varies.contains(PREFER));

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        assertEquals("Some input stream", entity);
    }

    @Test
    public void testGetBinaryDigestMd5() throws IOException {
        final Response res = target(BINARY_PATH).request().header(WANT_DIGEST, "MD5").get();

        assertEquals(OK, res.getStatusInfo());
        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));

        assertTrue(res.getMediaType().isCompatible(TEXT_PLAIN_TYPE));
        assertNotNull(res.getHeaderString(ACCEPT_RANGES));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
        assertEquals("md5-digest", res.getHeaderString(DIGEST));

        final List<String> varies = res.getStringHeaders().get(VARY);
        assertTrue(varies.contains(RANGE));
        assertTrue(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertFalse(varies.contains(PREFER));

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        assertEquals("Some input stream", entity);
    }

    @Test
    public void testGetBinaryDigestSha1() throws IOException {
        final Response res = target(BINARY_PATH).request().header(WANT_DIGEST, "SHA").get();

        assertEquals(OK, res.getStatusInfo());
        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));

        assertTrue(res.getMediaType().isCompatible(TEXT_PLAIN_TYPE));
        assertNotNull(res.getHeaderString(ACCEPT_RANGES));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
        assertEquals("sha1-digest", res.getHeaderString(DIGEST));

        final List<String> varies = res.getStringHeaders().get(VARY);
        assertTrue(varies.contains(RANGE));
        assertTrue(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertFalse(varies.contains(PREFER));

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        assertEquals("Some input stream", entity);
    }

    @Test
    public void testGetBinaryRange() throws IOException {
        final Response res = target(BINARY_PATH).request().header(RANGE, "bytes=3-10").get();

        assertEquals(OK, res.getStatusInfo());
        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));

        assertTrue(res.getMediaType().isCompatible(TEXT_PLAIN_TYPE));
        assertNotNull(res.getHeaderString(ACCEPT_RANGES));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));

        final List<String> varies = res.getStringHeaders().get(VARY);
        assertTrue(varies.contains(RANGE));
        assertTrue(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertFalse(varies.contains(PREFER));

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        assertEquals("e input", entity);
    }

    @Test
    public void testGetBinaryVersion() throws IOException {
        final Response res = target(BINARY_PATH).queryParam("version", timestamp).request().get();

        assertEquals(OK, res.getStatusInfo());
        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        // Jersey's client doesn't parse complex link headers correctly
        final List<Link> links = res.getStringHeaders().get(LINK).stream().map(Link::valueOf).collect(toList());

        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + BINARY_PATH + "?version=1496260729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 1000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + BINARY_PATH + "?version=1496261729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(time).equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + BINARY_PATH + "?version=1496262729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timemap") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("from")) &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp + 1000))
                        .equals(l.getParams().get("until")) &&
                    APPLICATION_LINK_FORMAT.equals(l.getType()) &&
                    l.getUri().toString().equals(BASE_URL + BINARY_PATH + "?ext=timemap")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timegate") &&
                    l.getUri().toString().equals(BASE_URL + BINARY_PATH)));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("original") &&
                    l.getUri().toString().equals(BASE_URL + BINARY_PATH)));
        assertTrue(links.stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(links.stream().anyMatch(hasType(LDP.NonRDFSource)));
        assertFalse(links.stream().anyMatch(hasType(LDP.Container)));

        assertTrue(res.getMediaType().isCompatible(TEXT_PLAIN_TYPE));
        assertEquals("bytes", res.getHeaderString(ACCEPT_RANGES));
        assertNotNull(res.getHeaderString(MEMENTO_DATETIME));
        assertEquals(time, parse(res.getHeaderString(MEMENTO_DATETIME), RFC_1123_DATE_TIME).toInstant());

        final List<String> varies = res.getStringHeaders().get(VARY);
        assertTrue(varies.contains(RANGE));
        assertTrue(varies.contains(WANT_DIGEST));
        assertFalse(varies.contains(ACCEPT_DATETIME));
        assertFalse(varies.contains(PREFER));

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        assertEquals("Some input stream", entity);
    }

    @Test
    public void testGetJsonCompact() throws IOException {
        final Response res = target(RESOURCE_PATH).request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertEquals(from(time), res.getLastModified());
        assertTrue(res.getLinks().stream().anyMatch(l ->
                    l.getRel().contains("timegate") && l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(res.getLinks().stream().anyMatch(l ->
                    l.getRel().contains("original") && l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(res.hasEntity());

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        final List<String> varies = res.getStringHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        final Map<String, Object> obj = MAPPER.readValue(entity, new TypeReference<Map<String, Object>>(){});

        assertTrue(obj.containsKey("@context"));
        assertTrue(obj.containsKey("title"));
        assertFalse(obj.containsKey("mode"));

        assertEquals("A title", (String) obj.get("title"));
    }

    @Test
    public void testGetTimeMapLink() throws IOException {
        when(mockResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockResource.getMementos()).thenAnswer(x -> Stream.of(
                new VersionRange(ofEpochSecond(timestamp - 2000), ofEpochSecond(timestamp - 1000)),
                new VersionRange(ofEpochSecond(timestamp - 1000), time),
                new VersionRange(time, ofEpochSecond(timestamp + 1000))));

        final Response res = target(RESOURCE_PATH).queryParam("ext", "timemap").request()
            .accept(APPLICATION_LINK_FORMAT).get();

        assertEquals(OK, res.getStatusInfo());
        assertEquals(MediaType.valueOf(APPLICATION_LINK_FORMAT), res.getMediaType());
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertNull(res.getLastModified());

        // Jersey's client doesn't parse complex link headers correctly, so res.getLinks() is not used here
        final List<Link> links = res.getStringHeaders().get(LINK).stream().map(Link::valueOf).collect(toList());

        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496260729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 1000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496261729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(time).equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496262729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timemap") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("from")) &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp + 1000))
                        .equals(l.getParams().get("until")) &&
                    APPLICATION_LINK_FORMAT.equals(l.getType()) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?ext=timemap")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timegate") &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("original") &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(links.stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(links.stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(links.stream().anyMatch(hasType(LDP.Container)));

        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        final List<Link> entityLinks = stream(entity.split(",\n")).map(Link::valueOf).collect(toList());
        assertEquals(4L, entityLinks.size());
        entityLinks.forEach(l -> assertTrue(links.contains(l)));
    }

    @Test
    public void testGetTimeMapJsonCompact() throws IOException {
        when(mockResource.getMementos()).thenAnswer(x -> Stream.of(
                new VersionRange(ofEpochSecond(timestamp - 2000), ofEpochSecond(timestamp - 1000)),
                new VersionRange(ofEpochSecond(timestamp - 1000), time),
                new VersionRange(time, ofEpochSecond(timestamp + 1000))));

        final Response res = target(RESOURCE_PATH).queryParam("ext", "timemap").request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertNull(res.getLastModified());

        // Jersey's client doesn't parse complex link headers correctly, so res.getLinks() is not used here
        final List<Link> links = res.getStringHeaders().get(LINK).stream().map(Link::valueOf).collect(toList());

        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496260729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 1000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496261729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(time).equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496262729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timemap") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("from")) &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp + 1000))
                        .equals(l.getParams().get("until")) &&
                    APPLICATION_LINK_FORMAT.equals(l.getType()) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?ext=timemap")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timegate") &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("original") &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(links.stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(links.stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(links.stream().anyMatch(hasType(LDP.Container)));

        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        final Map<String, Object> obj = MAPPER.readValue(entity,
                new TypeReference<Map<String, Object>>(){});

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> graph = (List<Map<String, Object>>) obj.get("@graph");

        assertEquals(4L, graph.size());
        assertTrue(graph.stream().anyMatch(x -> x.containsKey("@id") &&
                    x.get("@id").equals(BASE_URL + RESOURCE_PATH + "?ext=timemap") &&
                    x.containsKey("startedAtTime") &&
                    x.containsKey("endedAtTime")));
        assertTrue(graph.stream().anyMatch(x -> x.containsKey("@id") &&
                    x.get("@id").equals(BASE_URL + RESOURCE_PATH + "?version=1496260729000") &&
                    x.containsKey("atTime")));
        assertTrue(graph.stream().anyMatch(x -> x.containsKey("@id") &&
                    x.get("@id").equals(BASE_URL + RESOURCE_PATH + "?version=1496261729000") &&
                    x.containsKey("atTime")));
        assertTrue(graph.stream().anyMatch(x -> x.containsKey("@id") &&
                    x.get("@id").equals(BASE_URL + RESOURCE_PATH + "?version=1496262729000") &&
                    x.containsKey("atTime")));
    }

    @Test
    public void testGetTimeMapJson() throws IOException {
        when(mockResource.getMementos()).thenAnswer(x -> Stream.of(
                new VersionRange(ofEpochSecond(timestamp - 2000), ofEpochSecond(timestamp - 1000)),
                new VersionRange(ofEpochSecond(timestamp - 1000), time),
                new VersionRange(time, ofEpochSecond(timestamp + 1000))));

        final Response res = target(RESOURCE_PATH).queryParam("ext", "timemap").request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#expanded\"").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertNull(res.getLastModified());

        // Jersey's client doesn't parse complex link headers correctly, so res.getLinks() is not used here
        final List<Link> links = res.getStringHeaders().get(LINK).stream().map(Link::valueOf).collect(toList());

        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496260729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 1000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496261729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(time).equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496262729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timemap") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("from")) &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp + 1000))
                        .equals(l.getParams().get("until")) &&
                    APPLICATION_LINK_FORMAT.equals(l.getType()) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?ext=timemap")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timegate") &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("original") &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(links.stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(links.stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(links.stream().anyMatch(hasType(LDP.Container)));

        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        final List<Map<String, Object>> obj = MAPPER.readValue(entity,
                new TypeReference<List<Map<String, Object>>>(){});

        assertEquals(4L, obj.size());
        assertTrue(obj.stream().anyMatch(x -> x.containsKey("@id") &&
                    x.get("@id").equals(BASE_URL + RESOURCE_PATH + "?ext=timemap") &&
                    x.containsKey("http://www.w3.org/ns/prov#startedAtTime") &&
                    x.containsKey("http://www.w3.org/ns/prov#endedAtTime")));
        assertTrue(obj.stream().anyMatch(x -> x.containsKey("@id") &&
                    x.get("@id").equals(BASE_URL + RESOURCE_PATH + "?version=1496260729000") &&
                    x.containsKey("http://www.w3.org/ns/prov#atTime")));
        assertTrue(obj.stream().anyMatch(x -> x.containsKey("@id") &&
                    x.get("@id").equals(BASE_URL + RESOURCE_PATH + "?version=1496261729000") &&
                    x.containsKey("http://www.w3.org/ns/prov#atTime")));
        assertTrue(obj.stream().anyMatch(x -> x.containsKey("@id") &&
                    x.get("@id").equals(BASE_URL + RESOURCE_PATH + "?version=1496262729000") &&
                    x.containsKey("http://www.w3.org/ns/prov#atTime")));
    }

    @Test
    public void testGetVersionJson() throws IOException {
        final Response res = target(RESOURCE_PATH).queryParam("version", timestamp).request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertEquals(from(time), res.getLastModified());

        // Jersey's client doesn't parse complex link headers correctly, so res.getLinks() is not used here
        final List<Link> links = res.getStringHeaders().get(LINK).stream().map(Link::valueOf).collect(toList());

        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496260729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 1000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496261729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(time).equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496262729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timemap") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("from")) &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp + 1000))
                        .equals(l.getParams().get("until")) &&
                    APPLICATION_LINK_FORMAT.equals(l.getType()) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?ext=timemap")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timegate") &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("original") &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(links.stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(links.stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(links.stream().anyMatch(hasType(LDP.Container)));

        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));
        assertEquals(time, parse(res.getHeaderString(MEMENTO_DATETIME), RFC_1123_DATE_TIME).toInstant());
    }

    @Test
    public void testGetVersionContainerJson() throws IOException {
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.Container);
        final Response res = target(RESOURCE_PATH).queryParam("version", timestamp).request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertEquals(from(time), res.getLastModified());

        // Jersey's client doesn't parse complex link headers correctly, so res.getLinks() is not used here
        final List<Link> links = res.getStringHeaders().get(LINK).stream().map(Link::valueOf).collect(toList());

        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496260729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 1000))
                        .equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496261729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("memento") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(time).equals(l.getParams().get("datetime")) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?version=1496262729000")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timemap") &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp - 2000))
                        .equals(l.getParams().get("from")) &&
                    RFC_1123_DATE_TIME.withZone(UTC).format(ofEpochSecond(timestamp + 1000))
                        .equals(l.getParams().get("until")) &&
                    APPLICATION_LINK_FORMAT.equals(l.getType()) &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH + "?ext=timemap")));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("timegate") &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(links.stream().anyMatch(l -> l.getRels().contains("original") &&
                    l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertTrue(links.stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(links.stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(links.stream().anyMatch(hasType(LDP.Container)));

        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));
        assertEquals(time, parse(res.getHeaderString(MEMENTO_DATETIME), RFC_1123_DATE_TIME).toInstant());
    }

    @Test
    public void testGetAclJsonCompact() throws IOException {
        final Response res = target(RESOURCE_PATH).queryParam("ext", "acl").request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_RANGES));
        assertEquals(from(time), res.getLastModified());
        // The next two assertions may change at some point
        assertFalse(res.getLinks().stream().anyMatch(l ->
                    l.getRel().contains("timegate") && l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));
        assertFalse(res.getLinks().stream().anyMatch(l ->
                    l.getRel().contains("original") && l.getUri().toString().equals(BASE_URL + RESOURCE_PATH)));

        assertTrue(res.hasEntity());
        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        final Map<String, Object> obj = MAPPER.readValue(entity, new TypeReference<Map<String, Object>>(){});

        assertTrue(obj.containsKey("@context"));
        assertFalse(obj.containsKey("title"));
        assertTrue(obj.containsKey("mode"));
        assertEquals(ACL.Control.getIRIString(), (String) obj.get("mode"));

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        final List<String> varies = res.getStringHeaders().get(VARY);
        assertFalse(varies.contains(RANGE));
        assertFalse(varies.contains(WANT_DIGEST));
        assertTrue(varies.contains(ACCEPT_DATETIME));
        assertTrue(varies.contains(PREFER));
    }

    @Test
    public void testGetUserDeleted() {
        // Just setting the Trellis.DeletedResource type shouldn't, itself, lead to a 410 GONE response
        final Response res = target(USER_DELETED_PATH).request().get();

        assertEquals(OK, res.getStatusInfo());
    }

    @Test
    public void testGetNotFound() {
        final Response res = target(NON_EXISTENT_PATH).request().get();

        assertEquals(NOT_FOUND, res.getStatusInfo());
    }

    @Test
    public void testGetGone() {
        final Response res = target(DELETED_PATH).request().get();

        assertEquals(GONE, res.getStatusInfo());
    }

    /* ******************************* *
     *            OPTIONS Tests
     * ******************************* */
    @Test
    public void testOptionsLDPRS() {
        final Response res = target(RESOURCE_PATH).request().options();

        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertNull(res.getHeaderString(MEMENTO_DATETIME));

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));

        // LDP type links are not part of OPTIONS responses
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
    }

    @Test
    public void testOptionsLDPNR() {
        final Response res = target(BINARY_PATH).request().options();

        assertEquals(NO_CONTENT, res.getStatusInfo());

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_POST));

        assertNull(res.getHeaderString(MEMENTO_DATETIME));

        // LDP type links are not part of OPTIONS responses
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
    }

    @Test
    public void testOptionsLDPC() {
        when(mockResource.getInteractionModel()).thenReturn(LDP.Container);
        final Response res = target(RESOURCE_PATH).request().options();

        assertEquals(NO_CONTENT, res.getStatusInfo());

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertTrue(res.getAllowedMethods().contains("POST"));

        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNotNull(res.getHeaderString(ACCEPT_POST));
        final List<String> acceptPost = asList(res.getHeaderString(ACCEPT_POST).split(","));
        assertEquals(3L, acceptPost.size());
        assertTrue(acceptPost.contains("text/turtle"));
        assertTrue(acceptPost.contains(APPLICATION_LD_JSON));
        assertTrue(acceptPost.contains(APPLICATION_N_TRIPLES));

        assertNull(res.getHeaderString(MEMENTO_DATETIME));

        // LDP type links are not part of OPTIONS responses
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
    }

    @Test
    public void testOptionsACL() {
        final Response res = target(RESOURCE_PATH).queryParam("ext", "acl").request().options();

        assertEquals(NO_CONTENT, res.getStatusInfo());

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testOptionsNonexistent() {
        final Response res = target(NON_EXISTENT_PATH).request().options();

        assertEquals(NOT_FOUND, res.getStatusInfo());
    }

    @Test
    public void testOptionsGone() {
        final Response res = target(DELETED_PATH).request().options();

        assertEquals(GONE, res.getStatusInfo());
    }

    @Test
    public void testOptionsSlash() {
        final Response res = target(RESOURCE_PATH + "/").request().options();

        assertEquals(OK, res.getStatusInfo());

        assertTrue(res.getAllowedMethods().contains("PATCH"));
        assertTrue(res.getAllowedMethods().contains("PUT"));
        assertTrue(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertEquals(APPLICATION_SPARQL_UPDATE, res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testOptionsTimemap() {
        when(mockResource.getMementos()).thenAnswer(x -> Stream.of(
                new VersionRange(ofEpochSecond(timestamp - 2000), ofEpochSecond(timestamp - 1000)),
                new VersionRange(ofEpochSecond(timestamp - 1000), time),
                new VersionRange(time, ofEpochSecond(timestamp + 1000))));

        final Response res = target(RESOURCE_PATH).queryParam("ext", "timemap").request().options();

        assertEquals(NO_CONTENT, res.getStatusInfo());

        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertNull(res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_POST));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testOptionsVersion() {
        final Response res = target(RESOURCE_PATH).queryParam("version", timestamp).request().options();

        assertEquals(NO_CONTENT, res.getStatusInfo());

        assertFalse(res.getAllowedMethods().contains("PATCH"));
        assertFalse(res.getAllowedMethods().contains("PUT"));
        assertFalse(res.getAllowedMethods().contains("DELETE"));
        assertTrue(res.getAllowedMethods().contains("GET"));
        assertTrue(res.getAllowedMethods().contains("HEAD"));
        assertTrue(res.getAllowedMethods().contains("OPTIONS"));
        assertFalse(res.getAllowedMethods().contains("POST"));

        assertNull(res.getHeaderString(ACCEPT_PATCH));
        assertNull(res.getHeaderString(ACCEPT_POST));
    }

    /* ******************************* *
     *            POST Tests
     * ******************************* */
    @Test
    public void testPost() {
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockResourceService.get(eq(rdf.createIRI(TRELLIS_PREFIX + RESOURCE_PATH + "/" + RANDOM_VALUE)), eq(MAX)))
            .thenReturn(Optional.empty());

        final Response res = target(RESOURCE_PATH).request()
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(CREATED, res.getStatusInfo());
        assertEquals(BASE_URL + RESOURCE_PATH + "/" + RANDOM_VALUE, res.getLocation().toString());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
    }

    @Test
    public void testPostToLdpRs() {
        when(mockResourceService.get(eq(rdf.createIRI(TRELLIS_PREFIX + RESOURCE_PATH + "/" + RANDOM_VALUE)), eq(MAX)))
            .thenReturn(Optional.empty());

        final Response res = target(RESOURCE_PATH).request()
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
    }

    @Test
    public void testPostSlug() {
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.Container);

        final Response res = target(RESOURCE_PATH).request().header("Slug", "child")
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(CREATED, res.getStatusInfo());
        assertEquals(BASE_URL + CHILD_PATH, res.getLocation().toString());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
    }

    @Test
    public void testPostVersion() {
        final Response res = target(RESOURCE_PATH).queryParam("version", timestamp).request().header("Slug", "test")
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
    }

    @Test
    public void testPostAcl() {
        final Response res = target(RESOURCE_PATH).queryParam("ext", "acl").request().header("Slug", "test")
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
    }

    @Test
    public void testPostNonexistent() {
        final Response res = target(NON_EXISTENT_PATH).request()
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(NOT_FOUND, res.getStatusInfo());
    }

    @Test
    public void testPostGone() {
        final Response res = target(DELETED_PATH).request()
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(GONE, res.getStatusInfo());
    }

    @Test
    public void testPostBinary() {
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockResourceService.get(eq(rdf.createIRI(TRELLIS_PREFIX + RESOURCE_PATH + "/newresource")),
                    any(Instant.class))).thenReturn(Optional.empty());
        final Response res = target(RESOURCE_PATH).request().header("Slug", "newresource")
            .post(entity("some data.", TEXT_PLAIN_TYPE));

        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
    }

    @Test
    public void testPostBinaryWithInvalidDigest() {
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockResourceService.get(eq(rdf.createIRI(TRELLIS_PREFIX + RESOURCE_PATH + "/newresource")),
                    any(Instant.class))).thenReturn(Optional.empty());
        final Response res = target(RESOURCE_PATH).request().header("Slug", "newresource")
            .header("Digest", "md5=blahblah").post(entity("some data.", TEXT_PLAIN_TYPE));

        assertEquals(BAD_REQUEST, res.getStatusInfo());
    }

    @Test
    public void testPostBinaryWithMd5Digest() {
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockResourceService.get(eq(rdf.createIRI(TRELLIS_PREFIX + RESOURCE_PATH + "/newresource")),
                    any(Instant.class))).thenReturn(Optional.empty());
        final Response res = target(RESOURCE_PATH).request().header("Digest", "md5=BJozgIQwPzzVzSxvjQsWkA==")
            .header("Slug", "newresource").post(entity("some data.", TEXT_PLAIN_TYPE));

        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
    }

    @Test
    public void testPostBinaryWithSha1Digest() {
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockResourceService.get(eq(rdf.createIRI(TRELLIS_PREFIX + RESOURCE_PATH + "/newresource")),
                    any(Instant.class))).thenReturn(Optional.empty());
        final Response res = target(RESOURCE_PATH).request().header("Digest", "sha=3VWEuvPnAM6riDQJUu4TG7A4Ots=")
            .header("Slug", "newresource").post(entity("some data.", TEXT_PLAIN_TYPE));

        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
    }

    @Test
    public void testPostBinaryWithSha256Digest() {
        when(mockVersionedResource.getInteractionModel()).thenReturn(LDP.Container);
        when(mockResourceService.get(eq(rdf.createIRI(TRELLIS_PREFIX + RESOURCE_PATH + "/newresource")),
                    any(Instant.class))).thenReturn(Optional.empty());
        final Response res = target(RESOURCE_PATH).request()
            .header("Digest", "sha-256=voCCIRTNXosNlEgQ/7IuX5dFNvFQx5MfG/jy1AKiLMU=")
            .header("Slug", "newresource").post(entity("some data.", TEXT_PLAIN_TYPE));

        assertEquals(CREATED, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
    }

    @Test
    public void testPostInvalidContent() {
        final Response res = target(RESOURCE_PATH).request().post(entity("blah blah blah", "invalid/type"));

        assertEquals(UNSUPPORTED_MEDIA_TYPE, res.getStatusInfo());
    }

    @Test
    public void testPostSlash() {
        final Response res = target(RESOURCE_PATH + "/").request().header("Slug", "test")
            .post(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(OK, res.getStatusInfo());
    }

    /* ******************************* *
     *            PUT Tests
     * ******************************* */
    @Test
    public void testPutExisting() {
        final Response res = target(RESOURCE_PATH).request()
            .put(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testPutNew() {
        when(mockResourceService.get(eq(rdf.createIRI(TRELLIS_PREFIX + RESOURCE_PATH + "/test")), eq(MAX)))
            .thenReturn(Optional.empty());

        final Response res = target(RESOURCE_PATH + "/test").request()
            .put(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testPutVersion() {
        final Response res = target(RESOURCE_PATH).queryParam("version", timestamp).request()
            .put(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
    }

    @Test
    public void testPutAcl() {
        final Response res = target(RESOURCE_PATH).queryParam("ext", "acl").request()
            .put(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
    }

    @Test
    public void testPutInvalidContent() {
        final Response res = target(RESOURCE_PATH).request().put(entity("blah blah blah", "invalid/type"));

        assertEquals(UNSUPPORTED_MEDIA_TYPE, res.getStatusInfo());
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testPutBinary() {
        final Response res = target(BINARY_PATH).request().put(entity("some data.", TEXT_PLAIN_TYPE));

        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
    }

    @Test
    public void testPutBinaryWithInvalidDigest() {
        final Response res = target(BINARY_PATH).request().header("Digest", "md5=blahblah")
            .put(entity("some data.", TEXT_PLAIN_TYPE));

        assertEquals(BAD_REQUEST, res.getStatusInfo());
    }

    @Test
    public void testPutBinaryWithMd5Digest() {
        final Response res = target(BINARY_PATH).request().header("Digest", "md5=BJozgIQwPzzVzSxvjQsWkA==")
            .put(entity("some data.", TEXT_PLAIN_TYPE));

        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
    }

    @Test
    public void testPutBinaryWithSha1Digest() {
        final Response res = target(BINARY_PATH).request().header("Digest", "sha=3VWEuvPnAM6riDQJUu4TG7A4Ots=")
            .put(entity("some data.", TEXT_PLAIN_TYPE));

        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
    }

    @Test
    public void testPutBinaryWithSha256Digest() {
        final Response res = target(BINARY_PATH).request()
            .header("Digest", "sha-256=voCCIRTNXosNlEgQ/7IuX5dFNvFQx5MfG/jy1AKiLMU=")
            .put(entity("some data.", TEXT_PLAIN_TYPE));

        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.NonRDFSource)));
    }

    @Test
    public void testPutSlash() {
        final Response res = target(RESOURCE_PATH + "/").request()
            .put(entity("<> <http://purl.org/dc/terms/title> \"A title\" .", TEXT_TURTLE_TYPE));

        assertEquals(OK, res.getStatusInfo());
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    /* ******************************* *
     *            DELETE Tests
     * ******************************* */
    @Test
    public void testDeleteExisting() {
        final Response res = target(RESOURCE_PATH).request().delete();

        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testDeleteNonexistent() {
        final Response res = target(NON_EXISTENT_PATH).request().delete();

        assertEquals(NOT_FOUND, res.getStatusInfo());
    }

    @Test
    public void testDeleteDeleted() {
        final Response res = target(DELETED_PATH).request().delete();

        assertEquals(GONE, res.getStatusInfo());
    }

    @Test
    public void testDeleteVersion() {
        final Response res = target(RESOURCE_PATH).queryParam("version", timestamp).request().delete();

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testDeleteNonExistant() {
        when(mockResourceService.get(eq(rdf.createIRI(TRELLIS_PREFIX + RESOURCE_PATH + "/test")), eq(MAX)))
            .thenReturn(Optional.empty());

        final Response res = target(RESOURCE_PATH + "/test").request().delete();

        assertEquals(NOT_FOUND, res.getStatusInfo());
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testDeleteAcl() {
        final Response res = target(RESOURCE_PATH).queryParam("ext", "acl").request().delete();

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testDeleteSlash() {
        final Response res = target(RESOURCE_PATH + "/").request().delete();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    /* ********************* *
     *      PATCH tests
     * ********************* */
    @Test
    public void testPatchVersion() {
        final Response res = target(RESOURCE_PATH).queryParam("version", timestamp).request()
            .method("PATCH", entity("INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}",
                        APPLICATION_SPARQL_UPDATE));

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
    }

    @Test
    public void testPatchExisting() {
        final Response res = target(RESOURCE_PATH).request()
            .method("PATCH", entity("INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}",
                        APPLICATION_SPARQL_UPDATE));

        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testPatchNew() {
        when(mockResourceService.get(eq(rdf.createIRI(TRELLIS_PREFIX + RESOURCE_PATH + "/test")), eq(MAX)))
            .thenReturn(Optional.empty());

        final Response res = target(RESOURCE_PATH + "/test").request()
            .method("PATCH", entity("INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}",
                        APPLICATION_SPARQL_UPDATE));

        assertEquals(NOT_FOUND, res.getStatusInfo());
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testPatchAcl() {
        final Response res = target(RESOURCE_PATH).queryParam("ext", "acl").request()
            .method("PATCH", entity("INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}",
                        APPLICATION_SPARQL_UPDATE));

        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.Resource)));
        assertTrue(res.getLinks().stream().anyMatch(hasType(LDP.RDFSource)));
        assertFalse(res.getLinks().stream().anyMatch(hasType(LDP.Container)));
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testPatchInvalidContent() {
        final Response res = target(RESOURCE_PATH).request().method("PATCH", entity("blah blah blah", "invalid/type"));

        assertEquals(UNSUPPORTED_MEDIA_TYPE, res.getStatusInfo());
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testPatchSlash() {
        final Response res = target(RESOURCE_PATH + "/").request()
            .method("PATCH", entity("INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}",
                        APPLICATION_SPARQL_UPDATE));

        assertEquals(OK, res.getStatusInfo());
        assertNull(res.getHeaderString(MEMENTO_DATETIME));
    }

    @Test
    public void testPatchUpload() {
        final Response res = target(RESOURCE_PATH).queryParam("ext", UPLOADS).request()
            .method("PATCH", entity("INSERT { <> <http://purl.org/dc/terms/title> \"A title\" } WHERE {}",
                        APPLICATION_SPARQL_UPDATE));

        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
    }

    /**
     * Some other method
     */
    @Test
    public void testOtherMethod() {
        final Response res = target(RESOURCE_PATH).request().method("FOO");
        assertEquals(METHOD_NOT_ALLOWED, res.getStatusInfo());
    }

    /**
     * An other partition
     */
    @Test
    public void testOtherParition() {
        final Response res = target("other/object").request().get();
        assertEquals(NOT_FOUND, res.getStatusInfo());
    }

    /**
     * Multipart upload tests
     */
    @Test
    public void testMultipartExtLinkHeaderLDPRS() {
        when(mockBinaryResolver.supportsMultipartUpload()).thenReturn(true);
        final Response res = target(RESOURCE_PATH).request().get();

        assertEquals(OK, res.getStatusInfo());
        assertFalse(res.getLinks().stream().anyMatch(
                    hasLink(rdf.createIRI(BASE_URL + RESOURCE_PATH + "?ext=" + UPLOADS),
                            Trellis.multipartUploadService.getIRIString())));
    }

    @Test
    public void testMultipartExtLinkHeaderContainer() {
        when(mockBinaryResolver.supportsMultipartUpload()).thenReturn(true);
        when(mockResource.getInteractionModel()).thenReturn(LDP.Container);
        final Response res = target(RESOURCE_PATH).request().get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasLink(rdf.createIRI(BASE_URL + RESOURCE_PATH + "?ext=" + UPLOADS),
                            Trellis.multipartUploadService.getIRIString())));
    }

    @Test
    public void testMultipartExtLinkHeader() {
        when(mockBinaryResolver.supportsMultipartUpload()).thenReturn(true);
        final Response res = target(BINARY_PATH).request().get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().anyMatch(hasLink(rdf.createIRI(BASE_URL + BINARY_PATH + "?ext=" + UPLOADS),
                            Trellis.multipartUploadService.getIRIString())));
    }

    @Test
    public void testMultipartGet() {
        when(mockBinaryResolver.supportsMultipartUpload()).thenReturn(true);
        when(mockBinaryResolver.uploadSessionExists(eq(UPLOAD_SESSION_ID))).thenReturn(true);
        when(mockBinaryResolver.listParts(eq(UPLOAD_SESSION_ID))).thenAnswer(x -> Stream.of(
                new SimpleEntry<>(1, "digest1"),
                new SimpleEntry<>(2, "digest2")));
        final Response res = target("upload/" + REPO1 + "/" + UPLOAD_SESSION_ID).request().get();
        assertEquals(OK, res.getStatusInfo());
    }

    @Test
    public void testMultipartGetNotFound() {
        when(mockBinaryResolver.supportsMultipartUpload()).thenReturn(true);

        final Response res = target("upload/" + REPO1 + "/" + UPLOAD_SESSION_ID).request().get();
        assertEquals(NOT_FOUND, res.getStatusInfo());
    }

    @Test
    public void testMultipartPut() throws IOException {
        final InputStream inputStream = new ByteArrayInputStream("blah blah blah".getBytes(UTF_8));
        when(mockBinaryResolver.supportsMultipartUpload()).thenReturn(true);
        when(mockBinaryResolver.uploadSessionExists(eq(UPLOAD_SESSION_ID))).thenReturn(true);
        when(mockBinaryResolver.uploadPart(eq(UPLOAD_SESSION_ID), eq(15), any(InputStream.class)))
            .thenReturn("digest1");

        final Response res = target("upload/" + REPO1 + "/" + UPLOAD_SESSION_ID + "/15").request()
            .put(entity("blah blah blah", TEXT_PLAIN_TYPE));
        assertEquals(OK, res.getStatusInfo());
        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        assertEquals("{\"digest\":\"digest1\"}", entity);
    }

    @Test
    public void testMultipartPutNotFound() throws IOException {
        when(mockBinaryResolver.supportsMultipartUpload()).thenReturn(true);

        final Response res = target("upload/" + REPO1 + "/" + UPLOAD_SESSION_ID + "/15").request()
            .put(entity("blah blah blah", TEXT_PLAIN_TYPE));
        assertEquals(NOT_FOUND, res.getStatusInfo());
    }

    @Test
    public void testMultipartPost() {
        final BinaryService.MultipartUpload upload = new BinaryService.MultipartUpload(BASE_URL, BINARY_PATH,
                new HttpSession(), mockBinary);

        when(mockBinaryResolver.supportsMultipartUpload()).thenReturn(true);
        when(mockBinaryResolver.uploadSessionExists(eq(UPLOAD_SESSION_ID))).thenReturn(true);
        when(mockBinaryResolver.completeUpload(eq(UPLOAD_SESSION_ID), any())).thenReturn(upload);

        final Response res = target("upload/" + REPO1 + "/" + UPLOAD_SESSION_ID).request()
            .post(entity("{\"20\": \"value\"}", APPLICATION_JSON_TYPE));
        assertEquals(CREATED, res.getStatusInfo());
    }

    @Test
    public void testMultipartDelete() {
        when(mockBinaryResolver.supportsMultipartUpload()).thenReturn(true);
        when(mockBinaryResolver.uploadSessionExists(eq(UPLOAD_SESSION_ID))).thenReturn(true);

        final Response res = target("upload/" + REPO1 + "/" + UPLOAD_SESSION_ID).request().delete();
        assertEquals(NO_CONTENT, res.getStatusInfo());
    }

    @Test
    public void testMultipartDeleteNotFound() {
        when(mockBinaryResolver.supportsMultipartUpload()).thenReturn(true);

        final Response res = target("upload/" + REPO1 + "/" + UPLOAD_SESSION_ID).request().delete();
        assertEquals(NOT_FOUND, res.getStatusInfo());
    }

    /* ************************************ *
     *      Test cache control headers
     * ************************************ */
    @Test
    public void testCacheControl() {
        final Response res = target(RESOURCE_PATH).request().get();
        assertEquals(OK, res.getStatusInfo());
        assertNotNull(res.getHeaderString(CACHE_CONTROL));
        assertTrue(res.getHeaderString(CACHE_CONTROL).contains("max-age="));
    }

    @Test
    public void testCacheControlOptions() {
        final Response res = target(RESOURCE_PATH).request().options();
        assertEquals(NO_CONTENT, res.getStatusInfo());
        assertNull(res.getHeaderString(CACHE_CONTROL));
    }

    protected static Predicate<Link> hasLink(final IRI iri, final String rel) {
        return link -> rel.equals(link.getRel()) && iri.getIRIString().equals(link.getUri().toString());
    }

    protected static Predicate<Link> hasType(final IRI iri) {
        return hasLink(iri, "type");
    }
}
