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

import static org.trellisldp.http.RdfUtils.getInstance;

import java.time.Instant;
import java.util.function.BiFunction;

import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.rdf.api.IRI;
import org.apache.commons.rdf.api.RDF;
import org.apache.commons.rdf.api.RDFSyntax;
import org.trellisldp.spi.ResourceService;

/**
 * @author acoburn
 */
abstract class LdpResponseBuilder<T extends LdpResponseBuilder> {

    protected static final RDF rdf = getInstance();

    protected final ResourceService resourceService;

    protected String baseUrl = "";
    protected IRI profile = null;
    protected Prefer prefer = null;
    protected Boolean timemap = false;
    protected Version version = null;
    protected RDFSyntax syntax = null;
    protected AcceptDatetime datetime = null;
    protected WantDigest digest = null;
    protected Range range = null;
    protected BiFunction<Instant, EntityTag, ResponseBuilder> evaluator = null;

    /**
     * A LDP Response builder
     * @param resourceService the resourceService
     */
    protected LdpResponseBuilder(final ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    /**
     * Add a base URL to the response
     * @param url the URL
     * @return the Response builder
     */
    @SuppressWarnings("unchecked")
    public T withBaseUrl(final String url) {
        this.baseUrl = url;
        return (T) this;
    }

    /**
     * Add a cache evaluator
     * @param evaluator the evaluator
     * @return the Response builder
     */
    @SuppressWarnings("unchecked")
    public T withCacheEvaluator(final BiFunction<Instant, EntityTag, ResponseBuilder> evaluator) {
        this.evaluator = evaluator;
        return (T) this;
    }

    /**
     * Add a prefer object
     * @param prefer the prefer object
     * @return the Response builder
     */
    @SuppressWarnings("unchecked")
    public T withPrefer(final Prefer prefer) {
        this.prefer = prefer;
        return (T) this;
    }

    /**
     * Add a profile object
     * @param profile the profile object
     * @return the Response builder
     */
    @SuppressWarnings("unchecked")
    public T withProfile(final IRI profile) {
        this.profile = profile;
        return (T) this;
    }

    /**
     * Add a version object
     * @param version the version object
     * @return the Response builder
     */
    @SuppressWarnings("unchecked")
    public T withVersion(final Version version) {
        this.version = version;
        return (T) this;
    }

    /**
     * Add an RDFSyntax object
     * @param syntax the syntax object
     * @return the Response builder
     */
    @SuppressWarnings("unchecked")
    public T withSyntax(final RDFSyntax syntax) {
        this.syntax = syntax;
        return (T) this;
    }

    /**
     * Add a timemap marker
     * @param timemap whether to include a timemap
     * @return the Response builder
     */
    @SuppressWarnings("unchecked")
    public T withTimemap(final Boolean timemap) {
        this.timemap = timemap;
        return (T) this;
    }

    /**
     * Add a datetime object
     * @param datetime the datetime object
     * @return the Response builder
     */
    @SuppressWarnings("unchecked")
    public T withDatetime(final AcceptDatetime datetime) {
        this.datetime = datetime;
        return (T) this;
    }

    /**
     * Add a want-digest object
     * @param digest the want-digest object
     * @return the Response builder
     */
    @SuppressWarnings("unchecked")
    public T withWantDigest(final WantDigest digest) {
        this.digest = digest;
        return (T) this;
    }

    /**
     * Add a range abject
     * @param range the range object
     * @return the Response builder
     */
    @SuppressWarnings("unchecked")
    public T withRange(final Range range) {
        this.range = range;
        return (T) this;
    }

    /**
     * Build the response
     * @param path the path
     * @return the response
     */
    public abstract Response build(final String path);
}
