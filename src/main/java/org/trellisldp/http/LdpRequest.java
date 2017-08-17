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

import static java.util.Objects.isNull;
import static org.trellisldp.http.domain.HttpConstants.SESSION_PROPERTY;

import java.util.Map;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;

import org.trellisldp.http.domain.AcceptDatetime;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.http.domain.Range;
import org.trellisldp.http.domain.Version;
import org.trellisldp.http.domain.WantDigest;
import org.trellisldp.http.impl.HttpSession;
import org.trellisldp.spi.Session;

/**
 * @author acoburn
 */
public class LdpRequest {

    @Context
    private ContainerRequestContext ctx;

    @PathParam("partition")
    private String partition;

    @PathParam("path")
    private String path;

    @QueryParam("version")
    private Version version;

    @QueryParam("ext")
    private String ext;
    @Context
    private UriInfo uriInfo;

    @Context
    private HttpHeaders headers;

    @Context
    private Request request;

    @HeaderParam("Accept-Datetime")
    private AcceptDatetime datetime;

    @HeaderParam("Prefer")
    private Prefer prefer;

    @HeaderParam("Want-Digest")
    private WantDigest digest;

    @HeaderParam("Range")
    private Range range;

    @HeaderParam("Link")
    private Link link;

    @HeaderParam("Content-Type")
    private String contentType;

    @HeaderParam("Slug")
    private String slug;

    /**
     * Get the Content-Type header
     * @return the Content-Type header
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Get the slug header
     * @return the value of the slug header
     */
    public String getSlug() {
        return slug;
    }

    /**
     * Get the Link header
     * @return the Link header
     */
    public Link getLink() {
        return link;
    }

    /**
     * Get the Accept-Datetime value
     * @return the accept-datetime header
     */
    public AcceptDatetime getDatetime() {
        return datetime;
    }

    /**
     * Get the prefer header
     * @return the Prefer header
     */
    public Prefer getPrefer() {
        return prefer;
    }

    /**
     * Get the Want-Digest header
     * @return the Want-Digest header
     */
    public WantDigest getDigest() {
        return digest;
    }

    /**
     * Get the range header
     * @return the range header
     */
    public Range getRange() {
        return range;
    }

    /**
     * Get the partition name
     * @return the partition
     */
    public String getPartition() {
        return partition;
    }

    /**
     * Get the path
     * @return the path
     */
    public String getPath() {
        return path;
    }

    /**
     * Get the version value
     * @return the version query parameter
     */
    public Version getVersion() {
        return version;
    }

    /**
     * Get the ext value
     * @return the ext query parameter
     */
    public String getExt() {
        return ext;
    }

    /**
     * Get the request value
     * @return the request
     */
    public Request getRequest() {
        return request;
    }

    /**
     * Get the HTTP headers
     * @return the http headers
     */
    public HttpHeaders getHeaders() {
        return headers;
    }

    /**
     * Get a user session
     * @return a session
     */
    public Session getSession() {
        final Session session = (Session) ctx.getProperty(SESSION_PROPERTY);
        if (isNull(session)) {
            return new HttpSession();
        }
        return session;
    }

    /**
     * Get a base url value
     * @param partitions the partition baseUrl configurations
     * @return the baseUrl as a string
     */
    public String getBaseUrl(final Map<String, String> partitions) {
        return partitions.getOrDefault(partition, uriInfo.getBaseUri().toString());
    }
}
