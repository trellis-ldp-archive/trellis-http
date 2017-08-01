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

import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.spi.RDFUtils.getInstance;

import java.util.Map;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.rdf.api.RDF;
import org.slf4j.Logger;
import org.trellisldp.http.impl.HttpSession;
import org.trellisldp.spi.Session;

/**
 * @author acoburn
 */
class BaseLdpResource {

    protected static final Logger LOGGER = getLogger(BaseLdpResource.class);

    protected static final RDF rdf = getInstance();

    protected final Map<String, String> partitions;

    protected final Session session;

    @Context
    protected UriInfo uriInfo;

    @Context
    protected HttpHeaders headers;

    @Context
    protected Request request;

    protected BaseLdpResource(final Map<String, String> partitions) {
        // TODO -- add user session here
        this.session = new HttpSession();
        this.partitions = partitions;
    }

    protected String getPartition(final String path) {
        return path.split("/", 2)[0];
    }

    protected String getBaseUrl(final String path) {
        return partitions.getOrDefault(getPartition(path), uriInfo.getBaseUri().toString());
    }

    protected Response redirectWithoutSlash(final String path) {
        return Response.seeOther(fromUri(stripSlash(path)).build()).build();
    }

    private static String stripSlash(final String path) {
        return path.endsWith("/") ? stripSlash(path.substring(0, path.length() - 1)) : path;
    }
}
