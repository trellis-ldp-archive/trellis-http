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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.TEXT_HTML_TYPE;
import static javax.ws.rs.core.Response.Status.OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON_TYPE;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_N_TRIPLES_TYPE;
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE_TYPE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.InputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;

import org.trellisldp.io.JenaIOService;
import org.trellisldp.spi.IOService;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;

/**
 * @author acoburn
 */
public class RootResourceTest extends JerseyTest {

    final static ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Application configure() {
        final IOService ioService = new JenaIOService(null);

        final Map<String, String> partitions = new HashMap<>();
        partitions.put("repo1", "http://example.org/");
        partitions.put("repo2", "http://example.org/");
        partitions.put("repo3", "http://example.org/");
        partitions.put("repo4", "http://example.org/");

        final Properties properties = new Properties();
        properties.setProperty("title", "The title");
        properties.setProperty("seeAlso", "http://www.trellisldp.org");
        properties.setProperty("publisher", "https://example.org");

        final ResourceConfig config = new ResourceConfig();
        config.register(new RootResource(ioService, partitions, properties));
        return config;
    }

    @Test
    public void testJsonLd() throws IOException {

        final Response res = target("").request().accept("application/ld+json").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertTrue(res.hasEntity());

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        final List<Map<String, Object>> obj = MAPPER.readValue(entity,
                new TypeReference<List<Map<String, Object>>>(){});

        assertEquals(1L, obj.size());

        @SuppressWarnings("unchecked")
        final List<Map<String, String>> children = (List<Map<String, String>>) obj.get(0)
                .get(LDP.contains.getIRIString());

        final List<String> urls = children.stream().map(x -> x.get("@id")).collect(toList());
        assertEquals(4L, urls.size());
        assertTrue(urls.contains("http://example.org/repo1"));
        assertTrue(urls.contains("http://example.org/repo2"));
        assertTrue(urls.contains("http://example.org/repo3"));
        assertTrue(urls.contains("http://example.org/repo4"));

        @SuppressWarnings("unchecked")
        final List<Map<String, String>> titles = (List<Map<String, String>>) obj.get(0)
                .get(DC.title.getIRIString());

        final List<String> titleVals = titles.stream().map(x -> x.get("@value")).collect(toList());

        assertEquals(1L, titleVals.size());
        assertTrue(titleVals.contains("The title"));
    }

    @Test
    public void testTurtle() throws IOException {
        final Response res = target().request().get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(TEXT_TURTLE_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(TEXT_TURTLE_TYPE));
        assertTrue(res.hasEntity());
    }

    @Test
    public void testHTML() throws IOException {
        final Response res = target().request().accept("text/html").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(TEXT_HTML_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(TEXT_HTML_TYPE));
        assertTrue(res.hasEntity());

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        assertTrue(entity.contains("<title>The title</title>"));
    }

    @Test
    public void testNtriples() throws IOException {
        final Response res = target().request().accept("application/n-triples").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_N_TRIPLES_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_N_TRIPLES_TYPE));
        assertTrue(res.hasEntity());

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        assertTrue(entity.contains("> " + LDP.contains.ntriplesString() + " <http://example.org/repo1> ."));
        assertTrue(entity.contains("> " + LDP.contains.ntriplesString() + " <http://example.org/repo2> ."));
        assertTrue(entity.contains("> " + LDP.contains.ntriplesString() + " <http://example.org/repo3> ."));
        assertTrue(entity.contains("> " + LDP.contains.ntriplesString() + " <http://example.org/repo4> ."));
    }

    @Test
    public void testJsonLdCompact() throws IOException {

        final Response res = target("").request()
            .accept("application/ld+json; profile=\"http://www.w3.org/ns/json-ld#compacted\"").get();

        assertEquals(OK, res.getStatusInfo());
        assertTrue(APPLICATION_LD_JSON_TYPE.isCompatible(res.getMediaType()));
        assertTrue(res.getMediaType().isCompatible(APPLICATION_LD_JSON_TYPE));
        assertTrue(res.hasEntity());

        final String entity = IOUtils.toString((InputStream) res.getEntity(), UTF_8);
        final Map<String, Object> obj = MAPPER.readValue(entity, new TypeReference<Map<String, Object>>(){});

        assertTrue(obj.containsKey("@context"));
        assertTrue(obj.containsKey("contains"));
        assertTrue(obj.containsKey("title"));

        @SuppressWarnings("unchecked")
        final List<String> children = (List) obj.get("contains");
        assertTrue(children.contains("http://example.org/repo1"));
        assertTrue(children.contains("http://example.org/repo2"));
        assertTrue(children.contains("http://example.org/repo3"));
        assertTrue(children.contains("http://example.org/repo4"));

        assertEquals("The title", (String) obj.get("title"));
    }
}
