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

import static java.lang.String.join;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.joining;
import static javax.ws.rs.HttpMethod.DELETE;
import static javax.ws.rs.HttpMethod.GET;
import static javax.ws.rs.HttpMethod.HEAD;
import static javax.ws.rs.HttpMethod.OPTIONS;
import static javax.ws.rs.HttpMethod.POST;
import static javax.ws.rs.HttpMethod.PUT;
import static javax.ws.rs.core.HttpHeaders.ALLOW;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.status;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_PATCH;
import static org.trellisldp.http.domain.HttpConstants.ACCEPT_POST;
import static org.trellisldp.http.domain.HttpConstants.ACL;
import static org.trellisldp.http.domain.HttpConstants.PATCH;
import static org.trellisldp.http.domain.HttpConstants.TIMEMAP;
import static org.trellisldp.http.domain.HttpConstants.UPLOADS;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_SPARQL_UPDATE;
import static org.trellisldp.http.domain.RdfMediaType.VARIANTS;

import java.util.Map;

import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Variant;

import org.apache.commons.rdf.api.IRI;
import org.slf4j.Logger;

import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.LdpRequest;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * The OPTIONS response builder
 *
 * @author acoburn
 */
public class OptionsHandler extends BaseLdpHandler {

    private static final Logger LOGGER = getLogger(OptionsHandler.class);

    /**
     * An OPTIONS response builder
     * @param partitions the partitions
     * @param req the LDP request
     * @param resourceService the resource service
     */
    public OptionsHandler(final Map<String, String> partitions, final LdpRequest req,
            final ResourceService resourceService) {
        super(partitions, req, resourceService);
    }

    /**
     * Build the representation for the given resource
     * @param res the resource
     * @return the response builder
     */
    public ResponseBuilder ldpOptions(final Resource res) {
        final String identifier = req.getBaseUrl(partitions) + req.getPartition() + req.getPath();

        LOGGER.debug("OPTIONS request for {}", identifier);

        final IRI graphName = ACL.equals(req.getExt()) ? Trellis.PreferAccessControl : Trellis.PreferUserManaged;

        // Check if this is already deleted
        final ResponseBuilder deleted = checkDeleted(res, identifier);
        if (nonNull(deleted)) {
            return deleted;
        }

        final ResponseBuilder builder = status(NO_CONTENT);

        if (res.isMemento() || TIMEMAP.equals(req.getExt())) {
            // Mementos and TimeMaps are read-only
            builder.header(ALLOW, join(",", GET, HEAD, OPTIONS));
        } else if (UPLOADS.equals(req.getExt())) {
            // Upload handlers accept POST
            builder.header(ALLOW, join(",", POST, OPTIONS));
        } else {
            builder.header(ACCEPT_PATCH, APPLICATION_SPARQL_UPDATE);
            // ACL resources allow a limited set of methods (no DELETE or POST)
            // If it's not a container, POST isn't allowed
            if (Trellis.PreferAccessControl.equals(graphName) || res.getInteractionModel().equals(LDP.RDFSource) ||
                    res.getInteractionModel().equals(LDP.NonRDFSource)) {
                builder.header(ALLOW, join(",", GET, HEAD, OPTIONS, PATCH, PUT, DELETE));
            } else {
                // Containers and binaries support POST
                builder.header(ALLOW, join(",", GET, HEAD, OPTIONS, PATCH, PUT, DELETE, POST));
                builder.header(ACCEPT_POST, VARIANTS.stream().map(Variant::getMediaType)
                        .map(mt -> mt.getType() + "/" + mt.getSubtype())
                        .filter(mt -> !TEXT_HTML.equals(mt)).collect(joining(",")));
            }
        }

        return builder;
    }
}
