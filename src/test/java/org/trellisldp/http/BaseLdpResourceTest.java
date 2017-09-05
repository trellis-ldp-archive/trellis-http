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

import java.util.Map;

import org.junit.Test;

/**
 * @author acoburn
 */
public class BaseLdpResourceTest {

    static class MyLdpResource extends BaseLdpResource {
        public MyLdpResource(final Map<String, String> partitions) {
            super(partitions);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReservedPartitionName1() {
        new MyLdpResource(singletonMap("bnode", "http://bnode.example.org"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReservedPartitionName2() {
        new MyLdpResource(singletonMap("admin", "http://admin.example.org"));
    }
}
