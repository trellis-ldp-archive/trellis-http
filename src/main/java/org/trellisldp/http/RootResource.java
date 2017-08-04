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

import static java.lang.String.join;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.HttpMethod.HEAD;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.core.HttpHeaders.ALLOW;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.Response.ok;
import static org.apache.commons.rdf.api.RDFSyntax.RDFA_HTML;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_LD_JSON;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_N_TRIPLES;
import static org.trellisldp.http.domain.RdfMediaType.TEXT_TURTLE;
import static org.trellisldp.http.domain.RdfMediaType.VARIANTS;
import static org.trellisldp.http.impl.RdfUtils.getProfile;
import static org.trellisldp.http.impl.RdfUtils.getRdfSyntax;

import com.codahale.metrics.annotation.Timed;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.apache.commons.rdf.api.Triple;
import org.slf4j.Logger;

import org.trellisldp.http.impl.ResourceStreamer;
import org.trellisldp.spi.IOService;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.JSONLD;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.RDFS;

/**
 * @author acoburn
 */
@Path("")
@Produces({TEXT_TURTLE, APPLICATION_LD_JSON, APPLICATION_N_TRIPLES, TEXT_HTML})
public class RootResource extends BaseLdpResource {

    private static final Logger LOGGER = getLogger(RootResource.class);

    private static Map<String, IRI> propMapping = getPropertyMapping();

    private final IOService ioService;
    private final Properties properties;

    /**
     * Create a root resource
     * @param ioService the I/O service
     * @param partitions a map of partition/baseURLs
     * @param properties a collection of properties to customize the triple output
     */
    public RootResource(final IOService ioService, final Map<String, String> partitions, final Properties properties) {
        super(partitions);
        this.ioService = ioService;
        this.properties = properties;
    }

    /**
     * Get a representation of the root resource
     * @return the root resource
     */
    @GET
    @Timed
    public Response getPartitions() {

        final IRI identifier = rdf.createIRI(
                properties.getProperty("baseUrl", uriInfo.getBaseUri().toString()));

        LOGGER.debug("Request for root resource at: {}", identifier.getIRIString());

        final Graph graph = rdf.createGraph();
        partitions.entrySet().stream().map(e -> rdf.createIRI(e.getValue() + e.getKey()))
            .map(obj -> rdf.createTriple(identifier, LDP.contains, obj))
            .forEach(graph::add);

        properties.stringPropertyNames().stream().filter(propMapping::containsKey)
            .forEach(name -> graph.add(identifier, propMapping.get(name),
                        isUrl(properties.getProperty(name)) ?
                            rdf.createIRI(properties.getProperty(name)) :
                            rdf.createLiteral(properties.getProperty(name))));

        final RDFSyntax syntax = getRdfSyntax(headers.getAcceptableMediaTypes());
        return ok().header(ALLOW, join(",", HttpMethod.GET, HEAD, OPTIONS))
                    .link(LDP.Resource.getIRIString(), "type")
                    .link(LDP.RDFSource.getIRIString(), "type")
                    .variants(VARIANTS)
                    .type(syntax.mediaType)
                    .entity(ResourceStreamer.tripleStreamer(ioService, graph.stream().map(x -> (Triple) x),
                        syntax, ofNullable(getProfile(headers.getAcceptableMediaTypes())).orElseGet(() ->
                            RDFA_HTML.equals(syntax) ? identifier : JSONLD.expanded)))
                    .build();
    }

    private static Boolean isUrl(final String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static Map<String, IRI> getPropertyMapping() {
        final Map<String, IRI> mappings = new HashMap<>();
        mappings.put("title", DC.title);
        mappings.put("description", DC.description);
        mappings.put("publisher", DC.publisher);
        mappings.put("label", RDFS.label);
        mappings.put("comment", RDFS.comment);
        mappings.put("seeAlso", RDFS.seeAlso);
        return mappings;
    }
}
