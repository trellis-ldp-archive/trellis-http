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
import static org.trellisldp.http.domain.HttpConstants.PATCH;
import static org.trellisldp.http.domain.RdfMediaType.APPLICATION_SPARQL_UPDATE;
import static org.trellisldp.http.domain.RdfMediaType.VARIANTS;

import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Variant;

import org.slf4j.Logger;

import org.trellisldp.api.Resource;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.vocabulary.LDP;
import org.trellisldp.vocabulary.Trellis;

/**
 * The OPTIONS response builder
 *
 * @author acoburn
 */
public class LdpOptionsHandler extends BaseLdpHandler {

    private static final Logger LOGGER = getLogger(LdpOptionsHandler.class);

    /**
     * An OPTIONS response builder
     * @param resourceService the resource service
     */
    public LdpOptionsHandler(final ResourceService resourceService) {
        super(resourceService);
    }

    /**
     * Build the representation for the given resource
     * @param res the resource
     * @return the response builder
     */
    public ResponseBuilder ldpOptions(final Resource res) {
        final String identifier = baseUrl + path;

        LOGGER.debug("OPTIONS request for {}", identifier);

        // Check if this is already deleted
        final ResponseBuilder deleted = checkDeleted(res, identifier);
        if (nonNull(deleted)) {
            return deleted;
        }

        final ResponseBuilder builder = status(NO_CONTENT);

        if (res.isMemento()) {
            // Mementos are read-only
            builder.header(ALLOW, join(",", GET, HEAD, OPTIONS));
        } else if (multipartUploadPart && !res.getInteractionModel().equals(LDP.RDFSource)) {
            builder.header(ALLOW, join(",", OPTIONS, PUT));
        } else if (multipartUpload && !res.getInteractionModel().equals(LDP.RDFSource)) {
            builder.header(ALLOW, join(",", OPTIONS, POST));
            builder.header(ACCEPT_POST, "*/*");
        } else {
            builder.header(ACCEPT_PATCH, APPLICATION_SPARQL_UPDATE);
            if (Trellis.PreferAccessControl.equals(graphName)) {
                // ACL resources allow a limited set of methods (no PUT, DELETE or POST)
                builder.header(ALLOW, join(",", GET, HEAD, OPTIONS, PATCH));
            } else if (res.getInteractionModel().equals(LDP.RDFSource) ||
                    res.getInteractionModel().equals(LDP.NonRDFSource)) {
                // If it's not a container, POST isn't allowed
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
