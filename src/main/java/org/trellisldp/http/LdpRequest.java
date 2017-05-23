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

import static java.util.Optional.ofNullable;

import java.io.InputStream;

import java.util.Optional;

import javax.ws.rs.core.Link;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDFSyntax;
import org.trellisldp.spi.Session;

/**
 * A class representing an LDP Request
 *
 * @author acoburn
 */
final class LdpRequest {

    private final String baseUrl;
    private final String path;

    private final Prefer prefer;
    private final WantDigest digest;
    private final Range range;
    private final String contentType;
    private final String slug;
    private final Link link;

    private final Session session;
    private final RDFSyntax syntax;
    private final IRI profile;

    private final String update;
    private final InputStream entity;

    public static class LdpRequestBuilder {
        private String baseUrl = null;
        private String path = null;
        private String update = null;
        private InputStream entity = null;
        private IRI profile = null;
        private Prefer prefer = null;
        private RDFSyntax syntax = null;
        private WantDigest digest = null;
        private Range range = null;
        private Session session = null;
        private String contentType = null;
        private String slug = null;
        private Link link = null;

        /**
         * A LDP Response builder
         */
        protected LdpRequestBuilder() {
        }

        /**
         * Add a base URL to the response
         * @param url the URL
         * @return the Response builder
         */
        public LdpRequestBuilder withBaseUrl(final String url) {
            this.baseUrl = url;
            return this;
        }

        /**
         * Add a path to the request
         * @param path the path
         * @return the Response builder
         */
        public LdpRequestBuilder withPath(final String path) {
            this.path = path;
            return this;
        }

        /**
         * Add a prefer object
         * @param prefer the prefer object
         * @return the Response builder
         */
        public LdpRequestBuilder withPrefer(final Prefer prefer) {
            this.prefer = prefer;
            return this;
        }

        /**
         * Add a profile object
         * @param profile the profile object
         * @return the Response builder
         */
        public LdpRequestBuilder withProfile(final IRI profile) {
            this.profile = profile;
            return this;
        }

        /**
         * Add an RDFSyntax object
         * @param syntax the syntax object
         * @return the Response builder
         */
        public LdpRequestBuilder withSyntax(final RDFSyntax syntax) {
            this.syntax = syntax;
            return this;
        }

        /**
         * Add a want-digest object
         * @param digest the want-digest object
         * @return the Response builder
         */
        public LdpRequestBuilder withWantDigest(final WantDigest digest) {
            this.digest = digest;
            return this;
        }

        /**
         * Add a range abject
         * @param range the range object
         * @return the Response builder
         */
        public LdpRequestBuilder withRange(final Range range) {
            this.range = range;
            return this;
        }

        /**
         * Add a session
         * @param session the session
         * @return te Response builder
         */
        public LdpRequestBuilder withSession(final Session session) {
            this.session = session;
            return this;
        }

        /**
         * Add a Sparql-Update command
         * @param update the sparql update command
         * @return the Response builder
         */
        public LdpRequestBuilder withSparqlUpdate(final String update) {
            this.update = update;
            return this;
        }

        /**
         * Add an entity
         * @param entity the entity
         * @return the Response builder
         */
        public LdpRequestBuilder withEntity(final InputStream entity) {
            this.entity = entity;
            return this;
        }

        /**
         * Add a slug
         * @param slug the slug
         * @return the Response builder
         */
        public LdpRequestBuilder withSlug(final String slug) {
            this.slug = slug;
            return this;
        }

        /**
         * Add an interaction model
         * @param model the model
         * @return the Response builder
         */
        public LdpRequestBuilder withLink(final Link link) {
            this.link = link;
            return this;
        }

        /**
         * Add a content-type
         * @param contentType the content type
         * @return the Response builder
         */
        public LdpRequestBuilder withContentType(final String contentType) {
            this.contentType = contentType;
            return this;
        }

        public LdpRequest build() {
            return new LdpRequest(baseUrl, path, prefer, digest, range,
                    contentType, slug, link, session, syntax, profile, update, entity);
        }
    }

    protected LdpRequest(final String baseUrl, final String path,
            final Prefer prefer, final WantDigest digest, final Range range,
            final String contentType, final String slug, final Link link,
            final Session session, final RDFSyntax syntax, final IRI profile,
            final String update, final InputStream entity) {

        this.baseUrl = baseUrl;
        this.path = path;

        this.prefer = prefer;
        this.digest = digest;
        this.range = range;
        this.contentType = contentType;
        this.slug = slug;
        this.link = link;

        this.session = session;
        this.syntax = syntax;
        this.profile = profile;

        this.update = update;
        this.entity = entity;
    }

    public static LdpRequestBuilder builder() {
        return new LdpRequestBuilder();
    }

    public String getBaseUrl() {
        return ofNullable(baseUrl).orElse("");
    }

    public String getPath() {
        return ofNullable(path).orElse("");
    }

    public Optional<Prefer> getPrefer() {
        return ofNullable(prefer);
    }

    public Optional<WantDigest> getDigest() {
        return ofNullable(digest);
    }

    public Optional<Range> getRange() {
        return ofNullable(range);
    }

    public Optional<String> getContentType() {
        return ofNullable(contentType);
    }

    public Optional<String> getSlug() {
        return ofNullable(slug);
    }

    public Optional<Link> getLink() {
        return ofNullable(link);
    }

    public Optional<Session> getSession() {
        return ofNullable(session);
    }

    public Optional<RDFSyntax> getSyntax() {
        return ofNullable(syntax);
    }

    public Optional<IRI> getProfile() {
        return ofNullable(profile);
    }

    public Optional<String> getUpdate() {
        return ofNullable(update);
    }

    public Optional<InputStream> getEntity() {
        return ofNullable(entity);
    }
}
