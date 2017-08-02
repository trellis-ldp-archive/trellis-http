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
import static java.util.Optional.empty;
import static javax.ws.rs.core.Link.fromUri;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.mockito.Mockito.when;
import static org.trellisldp.spi.RDFUtils.getInstance;

import java.time.Instant;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Request;

import org.apache.commons.rdf.api.RDF;

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
    }

    @Test(expected = WebApplicationException.class)
    public void testPutNoSession() {
        final LdpPutHandler putHandler = new LdpPutHandler(mockResourceService, mockIoService, mockConstraintService,
                mockBinaryService, mockRequest);
        putHandler.setPath("resource");
        putHandler.setBaseUrl(baseUrl);
        putHandler.setSyntax(TURTLE);
        putHandler.setLink(fromUri(LDP.Container.getIRIString()).rel("type").build());

        putHandler.setResource();
    }

    @Test(expected = WebApplicationException.class)
    public void testPutNoSession2() {
        final LdpPutHandler putHandler = new LdpPutHandler(mockResourceService, mockIoService, mockConstraintService,
                mockBinaryService, mockRequest);
        putHandler.setPath("resource");
        putHandler.setBaseUrl(baseUrl);
        putHandler.setSyntax(TURTLE);
        putHandler.setLink(fromUri(LDP.Container.getIRIString()).rel("type").build());

        putHandler.setResource(mockResource);
    }
}
