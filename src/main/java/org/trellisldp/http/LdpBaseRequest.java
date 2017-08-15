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

import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.trellisldp.http.domain.Version;
import org.trellisldp.http.impl.HttpSession;
import org.trellisldp.spi.Session;

/**
 * @author acoburn
 */
public class LdpBaseRequest {
    /**
     * The Container context
     */
    @Context
    public ContainerRequestContext ctx;

    /**
     * The partition
     */
    @PathParam("partition")
    public String partition;

    /**
     * The resource path
     */
    @PathParam("path")
    public String path;

    /**
     * The version parameter
     */
    @QueryParam("version")
    public Version version;

    /**
     * An extension parameter
     */
    @QueryParam("ext")
    public String ext;

    /**
     * The URI info
     */
    @Context
    public UriInfo uriInfo;

    /**
     * The raw HTTP headers
     */
    @Context
    public HttpHeaders headers;

    /**
     * The underlying Jax Rs request
     */
    @Context
    public Request request;

    /**
     * The Security context of the request
     */
    @Context
    public SecurityContext security;

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
