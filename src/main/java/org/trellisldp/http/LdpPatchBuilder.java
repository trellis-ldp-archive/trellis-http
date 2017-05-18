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

import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.rdf.api.RDFSyntax.RDFA_HTML;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.http.RdfUtils.toExternalIri;
import static org.trellisldp.http.RdfUtils.toInternalIri;

import java.util.function.Function;

import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.BlankNodeOrIRI;
import org.apache.commons.rdf.api.Dataset;
import org.apache.commons.rdf.api.Graph;
import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.Triple;
import org.slf4j.Logger;

import org.trellisldp.api.Resource;
import org.trellisldp.spi.AuditData;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.SerializationService;
import org.trellisldp.vocabulary.JSONLD;
import org.trellisldp.vocabulary.PROV;
import org.trellisldp.vocabulary.Trellis;


/**
 * The PATCH response builder
 *
 * @author acoburn
 */
class LdpPatchBuilder extends LdpResponseBuilder {

    private static final Logger LOGGER = getLogger(LdpPatchBuilder.class);

    private final SerializationService serializationService;

    protected LdpPatchBuilder(final ResourceService resourceService, final SerializationService serializationService) {
        super(resourceService);
        this.serializationService = serializationService;
    }

    /**
     * Create a builder for an LDP PATCH response
     * @param resourceService the resource service
     * @param serializationService the serialization service
     * @return the response builder`
     */
    public static LdpPatchBuilder builder(final ResourceService resourceService,
            final SerializationService serializationService) {
        return new LdpPatchBuilder(resourceService, serializationService);
    }

    @Override
    public Response build(final String path) {
        requireNonNull(update, "Sparql-Update cannot be null");
        return resourceService.get(rdf.createIRI(TRELLIS_PREFIX + path))
                .map(updateResource(path)).orElse(status(NOT_FOUND)).build();
    }

    private Function<Resource, ResponseBuilder> updateResource(final String path) {
        return res -> {
            final String identifier = baseUrl + path;
            if (res.getTypes().anyMatch(Trellis.DeletedResource::equals)) {
                return status(GONE).links(MementoResource.getMementoLinks(identifier, res.getMementos())
                        .toArray(Link[]::new));
            }

            LOGGER.debug("Updating {} via PATCH", identifier);

            final Graph graph = rdf.createGraph();
            res.stream(Trellis.PreferUserManaged).forEach(graph::add);
            serializationService.update(graph, update, TRELLIS_PREFIX + path);

            final IRI bnode = (IRI) resourceService.skolemize(rdf.createBlankNode());
            final Dataset dataset = rdf.createDataset();
            graph.stream().map(skolemize(resourceService, baseUrl)).map(t ->
                    rdf.createQuad(Trellis.PreferUserManaged, t.getSubject(), t.getPredicate(), t.getObject()))
                .forEach(dataset::add);

            dataset.add(Trellis.PreferAudit, res.getIdentifier(), PROV.wasGeneratedBy, bnode);
            AuditData.updateData(bnode, session).stream().forEach(dataset::add);

            resourceService.put(res.getIdentifier(), dataset);

            final ResponseBuilder builder = ok();

            if (ofNullable(prefer).flatMap(Prefer::getPreference).filter("representation"::equals).isPresent()) {
                builder.entity(ResourceStreamer.tripleStreamer(serializationService,
                            graph.stream().map(unskolemize(resourceService, baseUrl)),
                            syntax, ofNullable(profile).orElseGet(() ->
                                RDFA_HTML.equals(syntax) ? rdf.createIRI(identifier) : JSONLD.expanded)));
            } else {
                builder.status(NO_CONTENT);
            }

            // Set no-cache directive
            final CacheControl cc = new CacheControl();
            cc.setNoCache(true);
            builder.cacheControl(cc);

            return builder;
        };
    }

    private static Function<Triple, Triple> unskolemize(final ResourceService svc, final String baseUrl) {
        return triple -> rdf.createTriple((BlankNodeOrIRI) toExternalIri(svc.unskolemize(triple.getSubject()), baseUrl),
                    triple.getPredicate(), toExternalIri(svc.unskolemize(triple.getObject()), baseUrl));
    }

    private static Function<Triple, Triple> skolemize(final ResourceService svc, final String baseUrl) {
        return triple -> rdf.createTriple((BlankNodeOrIRI) toInternalIri(svc.skolemize(triple.getSubject()), baseUrl),
                triple.getPredicate(), toInternalIri(svc.skolemize(triple.getObject()), baseUrl));
    }
}
