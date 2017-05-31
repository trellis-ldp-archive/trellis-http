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
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import javax.ws.rs.core.Link;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.trellisldp.api.Resource;
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

    private LdpGetHandler getHandler;

    @Before
    public void setUp() {

        final Instant time = ofEpochSecond(1496262729);

        when(mockResource.getTypes()).thenAnswer(x -> Stream.empty());
        when(mockResource.getMementos()).thenReturn(Stream.empty());
        when(mockResource.getInteractionModel()).thenReturn(LDP.RDFSource);
        when(mockResource.getModified()).thenReturn(time);
        when(mockResource.getBlob()).thenReturn(Optional.empty());
        when(mockResource.isMemento()).thenReturn(false);
        when(mockResource.getInbox()).thenReturn(Optional.empty());
        when(mockResource.getAnnotationService()).thenReturn(Optional.empty());
        when(mockResource.stream()).thenReturn(Stream.empty());

        getHandler = new LdpGetHandler(mockResourceService, mockIoService,
                mockBinaryService, mockRequest);
        getHandler.setPath("/");
        getHandler.setBaseUrl("http://localhost:8080/repo");
        getHandler.setSyntax(TURTLE);
    }

    @Test
    public void testGetLdprs() {
        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(OK, res.getStatusInfo());
        assertTrue(res.getLinks().stream().filter(l -> l.getRel().equals("type"))
                .map(Link::getUri).map(URI::toString)
                .anyMatch(LDP.Resource.getIRIString()::equals));
        assertFalse(res.getLinks().stream().filter(l -> l.getRel().equals("type"))
                .map(Link::getUri).map(URI::toString)
                .anyMatch(LDP.Container.getIRIString()::equals));
        assertTrue(res.getLinks().stream().filter(l -> l.getRel().equals("type"))
                .map(Link::getUri).map(URI::toString)
                .anyMatch(LDP.RDFSource.getIRIString()::equals));
    }

    @Test
    public void testGetDeleted() {
        when(mockResource.getTypes()).thenReturn(Stream.of(Trellis.DeletedResource));

        final Response res = getHandler.getRepresentation(mockResource).build();
        assertEquals(GONE, res.getStatusInfo());
    }
}
