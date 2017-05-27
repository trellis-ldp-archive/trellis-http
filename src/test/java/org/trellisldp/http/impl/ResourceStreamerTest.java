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

import static java.util.stream.Stream.of;
import static org.apache.commons.rdf.api.RDFSyntax.NQUADS;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import org.apache.commons.rdf.api.Quad;
import org.apache.commons.rdf.api.RDF;
import org.junit.Test;
import org.trellisldp.io.JenaSerializationService;
import org.trellisldp.spi.SerializationService;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
public class ResourceStreamerTest {

    final SerializationService svc = new JenaSerializationService(null);
    final RDF rdf = ServiceLoader.load(RDF.class).iterator().next();

    @Test
    public void testResourceStreamer() throws IOException {
        final Stream<Quad> quads = of(rdf.createQuad(
                    Trellis.PreferUserManaged,
                    rdf.createIRI("trellis:repository/foo"),
                    DC.title, rdf.createLiteral("A title")));

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ResourceStreamer streamer = ResourceStreamer.quadStreamer(svc, quads, NQUADS);
        streamer.write(out);
        assertEquals("<trellis:repository/foo> <http://purl.org/dc/terms/title> \"A title\" .\n",
                out.toString("UTF-8"));
    }
}
