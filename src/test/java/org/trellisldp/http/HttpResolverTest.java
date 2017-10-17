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
import static java.util.Collections.emptyMap;
import static javax.ws.rs.core.Response.Status.Family.CLIENT_ERROR;
import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.simple.SimpleRDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.trellisldp.api.BinaryService.Resolver;
import org.trellisldp.api.RuntimeRepositoryException;

/**
 * @author acoburn
 */
@RunWith(JUnitPlatform.class)
public class HttpResolverTest {

    private final static RDF rdf = new SimpleRDF();

    private final static IRI resource = rdf.createIRI("http://www.trellisldp.org/ns/trellis.ttl");
    private final static IRI sslResource = rdf.createIRI("https://s3.amazonaws.com/www.trellisldp.org/ns/trellis.ttl");
    private final static String partition = "partition";

    @Mock
    private Client mockClient;

    @Mock
    private Response mockResponse;

    @Mock
    private WebTarget mockWebTarget;

    @Mock
    private Invocation.Builder mockInvocationBuilder;

    @Mock
    private Response.StatusType mockStatusType;

    @BeforeEach
    public void setUp() throws IOException {
        initMocks(this);
        when(mockClient.target(anyString())).thenReturn(mockWebTarget);
        when(mockWebTarget.request()).thenReturn(mockInvocationBuilder);
        when(mockInvocationBuilder.put(any(Entity.class))).thenReturn(mockResponse);
        when(mockInvocationBuilder.delete()).thenReturn(mockResponse);
        when(mockInvocationBuilder.get()).thenReturn(mockResponse);
        when(mockResponse.getStatusInfo()).thenReturn(mockStatusType);
        when(mockStatusType.getFamily()).thenReturn(SUCCESSFUL);
        when(mockStatusType.toString()).thenReturn("Successful");
    }

    @Test
    public void testExists() {

        final Resolver resolver = new HttpResolver();

        assertTrue(resolver.exists(partition, resource));
        assertTrue(resolver.exists(partition, sslResource));
        assertFalse(resolver.exists(partition, rdf.createIRI("http://www.trellisldp.org/ns/non-existent.ttl")));
    }

    @Test
    public void testGetContent() {
        final Resolver resolver = new HttpResolver();

        assertTrue(resolver.getContent(partition, resource).isPresent());
        assertTrue(resolver.getContent(partition, resource).map(this::uncheckedToString).get()
                .contains("owl:Ontology"));
    }

    @Test
    public void testGetSslContent() {
        final Resolver resolver = new HttpResolver();

        assertTrue(resolver.getContent(partition, sslResource).isPresent());
        assertTrue(resolver.getContent(partition, sslResource).map(this::uncheckedToString).get()
                .contains("owl:Ontology"));
    }

    @Test
    public void testSetContent() {
        final String contents = "A new resource";
        final Resolver resolver = new HttpResolver();

        final InputStream inputStream = new ByteArrayInputStream(contents.getBytes(UTF_8));
        assertThrows(RuntimeRepositoryException.class, () ->
                resolver.setContent(partition, sslResource, inputStream));
    }

    @Test
    public void testMockedClient() throws IOException {
        final Resolver resolver = new HttpResolver(mockClient);
        final String contents = "A new resource";
        final InputStream inputStream = new ByteArrayInputStream(contents.getBytes(UTF_8));
        resolver.setContent(partition, sslResource, inputStream);

        verify(mockInvocationBuilder).put(any(Entity.class));
    }

    @Test
    public void testMockedDelete() throws IOException {
        final Resolver resolver = new HttpResolver(mockClient);
        resolver.purgeContent(partition, sslResource);

        verify(mockInvocationBuilder).delete();
    }

    @Test
    public void testMockedDeleteiException() {
        when(mockStatusType.getFamily()).thenReturn(CLIENT_ERROR);
        when(mockStatusType.toString()).thenReturn("BAD REQUEST");
        final Resolver resolver = new HttpResolver(mockClient);
        assertThrows(RuntimeRepositoryException.class, () ->
                resolver.purgeContent(partition, sslResource));
    }

    @Test
    public void testHttpSchemes() {
        final Resolver resolver = new HttpResolver();
        assertEquals(2L, resolver.getUriSchemes().size());
        assertTrue(resolver.getUriSchemes().contains("http"));
        assertTrue(resolver.getUriSchemes().contains("https"));
    }

    @Test
    public void testMultipart() {
        final Resolver resolver = new HttpResolver();
        assertFalse(resolver.supportsMultipartUpload());
    }

    @Test
    public void testIniateMultipart() {
        final Resolver resolver = new HttpResolver();
        assertThrows(UnsupportedOperationException.class, () ->
                resolver.initiateUpload(partition, resource, "text/plain"));
    }

    @Test
    public void testMultipartAbort() {
        final Resolver resolver = new HttpResolver();
        assertThrows(UnsupportedOperationException.class, () ->
                resolver.abortUpload("test-identifier"));
    }

    @Test
    public void testMultipartComplete() {
        final Resolver resolver = new HttpResolver();
        assertThrows(UnsupportedOperationException.class, () ->
                resolver.completeUpload("test-identifier", emptyMap()));
    }

    @Test
    public void testMultipartUpload() {
        final String contents = "A new resource";
        final InputStream inputStream = new ByteArrayInputStream(contents.getBytes(UTF_8));
        final Resolver resolver = new HttpResolver();
        assertThrows(UnsupportedOperationException.class, () ->
                resolver.uploadPart("test-identifier", 1, inputStream));
    }

    @Test
    public void testMultipartSessionExists() {
        final Resolver resolver = new HttpResolver();
        assertThrows(UnsupportedOperationException.class, () ->
                resolver.uploadSessionExists("test-identifier"));
    }

    @Test
    public void testMultipartInitiate() {
        final Resolver resolver = new HttpResolver();
        assertThrows(UnsupportedOperationException.class, () ->
                resolver.initiateUpload(partition, resource, "text/plain"));
    }

    @Test
    public void testMultipartList() {
        final Resolver resolver = new HttpResolver();
        assertThrows(UnsupportedOperationException.class, () -> resolver.listParts("foo"));
    }


    @Test
    public void testExceptedPut() throws IOException {
        when(mockStatusType.getFamily()).thenReturn(CLIENT_ERROR);
        when(mockStatusType.toString()).thenReturn("BAD REQUEST");
        final String contents = "A new resource";
        final Resolver resolver = new HttpResolver(mockClient);
        final InputStream inputStream = new ByteArrayInputStream(contents.getBytes(UTF_8));

        assertThrows(RuntimeRepositoryException.class, () ->
                resolver.setContent(partition, resource, inputStream));
    }

    @Test
    public void testExceptedDelete() throws IOException {
        when(mockStatusType.getFamily()).thenReturn(CLIENT_ERROR);
        when(mockStatusType.toString()).thenReturn("BAD REQUEST");
        final Resolver resolver = new HttpResolver(mockClient);

        assertThrows(RuntimeRepositoryException.class, () ->
            resolver.purgeContent(partition, resource));
    }

    @Test
    public void testGetNoEntity() throws IOException {
        final Resolver resolver = new HttpResolver(mockClient);
        assertFalse(resolver.getContent(partition, resource).isPresent());
    }

    private String uncheckedToString(final InputStream is) {
        try {
            return IOUtils.toString(is, UTF_8);
        } catch (final IOException ex) {
            return null;
        }
    }
}
