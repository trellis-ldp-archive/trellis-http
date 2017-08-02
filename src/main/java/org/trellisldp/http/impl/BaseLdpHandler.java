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
import static java.util.Date.from;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.status;
import static org.apache.commons.rdf.api.RDFSyntax.JSONLD;
import static org.apache.commons.rdf.api.RDFSyntax.NTRIPLES;
import static org.apache.commons.rdf.api.RDFSyntax.TURTLE;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.spi.RDFUtils.getInstance;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFSyntax;
import org.slf4j.Logger;
import org.trellisldp.api.Resource;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.Session;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
public class BaseLdpHandler {

    private static final Logger LOGGER = getLogger(BaseLdpHandler.class);

    protected static final RDF rdf = getInstance();

    protected static final List<RDFSyntax> SUPPORTED_RDF_TYPES = asList(TURTLE, JSONLD, NTRIPLES);

    protected final ResourceService resourceService;

    protected String path = "";
    protected String baseUrl = "";
    protected IRI profile = null;
    protected RDFSyntax syntax = null;
    protected Session session = null;
    protected Prefer prefer = null;
    protected Link link = null;
    protected InputStream entity = null;
    protected String contentType = null;

    /**
     * A base class for response handling
     * @param resourceService the resource service
     */
    public BaseLdpHandler(final ResourceService resourceService) {
        this.resourceService = resourceService;
    }


    /**
     * Check if this is a deleted resource, and if so return an appropriate response
     * @param res the resource
     * @param identifier the identifier
     * @return if the resource has been deleted, return an HTTP response builder, otherwise null
     */
    protected ResponseBuilder checkDeleted(final Resource res, final String identifier) {
       if (res.getTypes().anyMatch(Trellis.DeletedResource::equals)) {
            return status(GONE).links(MementoResource.getMementoLinks(identifier, res.getMementos())
                    .toArray(Link[]::new));
        }
        return null;
    }

    /**
     * Get the partition name, given the path
     * @param path the path
     * @return the partition portion of the path
     */
    protected static String getPartition(final String path) {
        return path.split("/", 2)[0];
    }

    /**
     * Check the request for a cache-related response
     * @param request the request
     * @param modified the modified time
     * @param etag the etag
     * @return the ResponseBuilder, which will be null if there is not a cache-hit
     */
    protected static ResponseBuilder checkCache(final Request request, final Instant modified, final EntityTag etag) {
        try {
            return request.evaluatePreconditions(from(modified), etag);
        } catch (final IllegalArgumentException ex) {
            LOGGER.warn("Ignoring cache-related headers: {}", ex.getMessage());
        }
        return null;
    }


    /**
     * Set the path
     * @param path the path
     */
    public void setPath(final String path) {
        if (path.startsWith("/")) {
            this.path = path.substring(1);
        } else {
            this.path = path;
        }
    }

    /**
     * Set the syntax
     * @param syntax the syntax
     */
    public void setSyntax(final RDFSyntax syntax) {
        this.syntax = syntax;
    }

    /**
     * Set the link header
     * @param link the link
     */
    public void setLink(final Link link) {
        this.link = link;
    }

    /**
     * Set the content-type
     * @param contentType the content type
     */
    public void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    /**
     * Set the entity
     * @param entity the entity
     */
    public void setEntity(final InputStream entity) {
        this.entity = entity;
    }

    /**
     * Set the profile
     * @param profile the profile
     */
    public void setProfile(final IRI profile) {
        this.profile = profile;
    }

    /**
     * Set the baseUrl
     * @param baseUrl the baseUrl
     */
    public void setBaseUrl(final String baseUrl) {
        if (baseUrl.endsWith("/")) {
            this.baseUrl = baseUrl;
        } else {
            this.baseUrl = baseUrl + "/";
        }
    }

    /**
     * Set the prefer values
     * @param prefer the prefer header
     */
    public void setPrefer(final Prefer prefer) {
        this.prefer = prefer;
    }

    /**
     * Set the session
     * @param session the session
     */
    public void setSession(final Session session) {
        this.session = session;
    }
}
