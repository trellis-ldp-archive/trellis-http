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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;

import javax.ws.rs.WebApplicationException;

import org.apache.commons.rdf.api.Dataset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.Mock;

/**
 * @author acoburn
 */
@RunWith(JUnitPlatform.class)
public class TrellisDatasetTest {

    @Mock
    private Dataset mockDataset;

    @BeforeEach
    public void setUp() throws Exception {
        initMocks(this);
        doThrow(new IOException()).when(mockDataset).close();
    }

    @Test
    public void testCloseDatasetError() {
        assertThrows(WebApplicationException.class, () -> {
            try (final TrellisDataset dataset = new TrellisDataset(mockDataset)) {
                // nothing here
            }
        });
    }
}
