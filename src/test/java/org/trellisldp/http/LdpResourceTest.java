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

import static java.util.Collections.emptyList;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.core.Application;

import org.glassfish.jersey.server.ResourceConfig;

/**
 * @author acoburn
 */
public class LdpResourceTest extends AbstractLdpResourceTest {

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
        config.register(new LdpResource(mockResourceService, ioService, mockConstraintService,
                    mockBinaryService, null, null, partitions, emptyList()));
        return config;
    }
}