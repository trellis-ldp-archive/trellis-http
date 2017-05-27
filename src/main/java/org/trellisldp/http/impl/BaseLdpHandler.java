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

import static java.util.Arrays.asList;
import static org.apache.commons.rdf.api.RDFSyntax.JSONLD;
import static org.apache.commons.rdf.api.RDFSyntax.NTRIPLES;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.trellisldp.spi.RDFUtils.getInstance;

import java.util.List;

import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFSyntax;
import org.trellisldp.spi.ResourceService;

/**
 * @author acoburn
 */
public class BaseLdpHandler {

    protected static final RDF rdf = getInstance();

    protected static List<RDFSyntax> SUPPORTED_RDF_TYPES = asList(TURTLE, JSONLD, NTRIPLES);

    protected final ResourceService resourceService;

    /**
     * A base class for response handling
     * @param resourceService the resource service
     */
    public BaseLdpHandler(final ResourceService resourceService) {
        this.resourceService = resourceService;
    }
}
