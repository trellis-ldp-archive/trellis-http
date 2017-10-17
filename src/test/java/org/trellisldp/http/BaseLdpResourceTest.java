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

import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

/**
 * @author acoburn
 */
@RunWith(JUnitPlatform.class)
public class BaseLdpResourceTest {

    static class MyLdpResource extends BaseLdpResource {
        public MyLdpResource(final Map<String, String> partitions) {
            super(partitions);
        }
    }

    @Test
    public void testReservedPartitionName1() {
        assertThrows(IllegalArgumentException.class, () ->
                new MyLdpResource(singletonMap("bnode", "http://bnode.example.org")));
    }

    @Test
    public void testReservedPartitionName2() {
        assertThrows(IllegalArgumentException.class, () ->
                new MyLdpResource(singletonMap("admin", "http://admin.example.org")));
    }
}
