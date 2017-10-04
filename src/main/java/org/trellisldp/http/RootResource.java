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
import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.HttpMethod.HEAD;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.core.HttpHeaders.ALLOW;
import static javax.ws.rs.core.Response.ok;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.impl.RdfUtils.getDefaultProfile;
import static org.trellisldp.http.impl.RdfUtils.getProfile;
import static org.trellisldp.http.impl.RdfUtils.getSyntax;

import com.codahale.metrics.annotation.Timed;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.NotAcceptableException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.apache.commons.rdf.api.Triple;
import org.slf4j.Logger;

import org.trellisldp.api.IOService;
import org.trellisldp.vocabulary.DC;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.RDF;
import org.trellisldp.vocabulary.RDFS;

/**
 * @author acoburn
 */
@Produces({"text/turtle,application/ld+json,application/n-triples,text/html"})
@Path("")
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
     * @param uriInfo information about the URI
     * @param headers the request headers
     * @return the root resource
     */
    @GET
    @Timed
    public Response getPartitions(@Context final UriInfo uriInfo, @Context final HttpHeaders headers) {

        final IRI identifier = rdf.createIRI(
                properties.getProperty("baseUrl", uriInfo.getBaseUri().toString()));

        LOGGER.debug("Request for root resource at: {}", identifier.getIRIString());

        final List<Triple> graph = new ArrayList<>();
        partitions.entrySet().stream().map(e -> rdf.createIRI(e.getValue() + e.getKey()))
            .map(obj -> rdf.createTriple(identifier, LDP.contains, obj)).forEach(graph::add);

        properties.stringPropertyNames().stream().filter(propMapping::containsKey)
            .map(name -> rdf.createTriple(identifier, propMapping.get(name), isUrl(properties.getProperty(name)) ?
                        rdf.createIRI(properties.getProperty(name)) :
                        rdf.createLiteral(properties.getProperty(name))))
            .forEach(graph::add);

        final RDFSyntax syntax = getSyntax(headers.getAcceptableMediaTypes(), empty())
            .orElseThrow(NotAcceptableException::new);

        final IRI profile = ofNullable(getProfile(headers.getAcceptableMediaTypes(), syntax))
            .orElseGet(() -> getDefaultProfile(syntax, identifier));

        final StreamingOutput stream = new StreamingOutput() {
            @Override
            public void write(final OutputStream out) throws IOException {
                ioService.write(graph.stream(), out, syntax, profile);
            }
        };

        return ok().header(ALLOW, join(",", HttpMethod.GET, HEAD, OPTIONS))
                    .link(LDP.Resource.getIRIString(), "type")
                    .link(LDP.RDFSource.getIRIString(), "type")
                    .type(syntax.mediaType).entity(stream).build();
    }

    private static Boolean isUrl(final String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private static Map<String, IRI> getPropertyMapping() {
        final Map<String, IRI> mappings = new HashMap<>();
        mappings.put("title", DC.title);
        mappings.put("description", DC.description);
        mappings.put("publisher", DC.publisher);
        mappings.put("isVersionOf", DC.isVersionOf);
        mappings.put("label", RDFS.label);
        mappings.put("comment", RDFS.comment);
        mappings.put("seeAlso", RDFS.seeAlso);
        mappings.put("type", RDF.type);
        return mappings;
    }
}
