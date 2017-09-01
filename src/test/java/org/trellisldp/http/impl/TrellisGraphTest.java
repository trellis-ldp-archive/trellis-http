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

import static org.mockito.Mockito.doThrow;

import java.io.IOException;

import javax.ws.rs.WebApplicationException;

import org.apache.commons.rdf.api.Graph;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;



/**
 * @author acoburn
 */
@RunWith(MockitoJUnitRunner.class)
public class TrellisGraphTest {

    @Mock
    private Graph mockGraph;

    @Before
    public void setUp() throws Exception {
        doThrow(new IOException()).when(mockGraph).close();
    }

    @Test(expected = WebApplicationException.class)
    public void testCloseGraphError() {
        try (final TrellisGraph dataset = new TrellisGraph(mockGraph)) {
            // nothing here
        }
    }
}
