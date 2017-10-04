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

import static java.util.Arrays.asList;
import static org.trellisldp.api.RDFUtils.getInstance;

import java.util.List;
import java.util.Map;

import org.apache.commons.rdf.api.RDF;

/**
 * @author acoburn
 */
class BaseLdpResource {

    protected static final RDF rdf = getInstance();

    protected final Map<String, String> partitions;

    private final List<String> reservedPartitionNames = asList("bnode", "admin");

    protected BaseLdpResource(final Map<String, String> partitions) {
        reservedPartitionNames.stream().filter(partitions::containsKey).findAny().ifPresent(partition -> {
            throw new IllegalArgumentException("Invalid partition name: " + partition);
        });
        this.partitions = partitions;
    }
}
