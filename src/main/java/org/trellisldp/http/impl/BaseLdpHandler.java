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

import java.io.InputStream;
import java.util.List;

import javax.ws.rs.core.Link;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFSyntax;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.spi.ResourceService;
import org.trellisldp.spi.Session;

/**
 * @author acoburn
 */
public class BaseLdpHandler {

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
     * Set the path
     * @param path the path
     */
    public void setPath(final String path) {
        this.path = path;
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
        this.baseUrl = baseUrl;
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
