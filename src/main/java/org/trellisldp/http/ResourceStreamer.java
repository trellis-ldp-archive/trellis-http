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

import java.io.IOException;
import java.io.OutputStream;
import java.util.stream.Stream;

import javax.ws.rs.core.StreamingOutput;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDFSyntax;
import org.trellisldp.spi.SerializationService;

/**
 * @author acoburn
 */
class ResourceStreamer implements StreamingOutput {

    private final SerializationService service;
    private final Stream<Quad> stream;
    private final RDFSyntax syntax;
    private final IRI[] profiles;

    /**
     * Create a streamable RDF resource
     * @param service the serialization service
     * @param stream the stream of quads
     * @param syntax the RDF syntax to output
     * @param profiles the profile, if any
     */
    public ResourceStreamer(final SerializationService service, final Stream<Quad> stream, final RDFSyntax syntax,
            final IRI... profiles) {
        this.service = service;
        this.stream = stream;
        this.syntax = syntax;
        this.profiles = profiles;
    }

    @Override
    public void write(final OutputStream os) throws IOException {
        service.write(stream.map(Quad::asTriple), os, syntax, profiles);
    }
}
