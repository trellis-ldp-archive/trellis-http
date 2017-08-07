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
import static java.util.Objects.nonNull;
import static javax.ws.rs.core.UriBuilder.fromUri;
import static org.slf4j.LoggerFactory.getLogger;
import static org.trellisldp.http.domain.HttpConstants.TRELLIS_PREFIX;
import static org.trellisldp.spi.RDFUtils.getInstance;

import java.util.Map;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.rdf.api.RDF;
import org.slf4j.Logger;
import org.trellisldp.http.impl.HttpSession;
import org.trellisldp.spi.AgentService;
import org.trellisldp.spi.AccessControlService;
import org.trellisldp.spi.Session;
import org.trellisldp.vocabulary.ACL;
import org.trellisldp.vocabulary.Trellis;

/**
 * @author acoburn
 */
class BaseLdpResource {

    protected static final Logger LOGGER = getLogger(BaseLdpResource.class);

    protected static final RDF rdf = getInstance();

    protected final Map<String, String> partitions;

    protected final AgentService agentService;

    protected final AccessControlService accessService;

    @Context
    protected UriInfo uriInfo;

    @Context
    protected HttpHeaders headers;

    @Context
    protected Request request;

    @Context
    protected SecurityContext security;

    protected BaseLdpResource(final Map<String, String> partitions) {
        this(partitions, null, null);
    }

    protected BaseLdpResource(final Map<String, String> partitions, final AgentService agentService,
            final AccessControlService accessService) {
        this.partitions = partitions;
        this.agentService = agentService;
        this.accessService = accessService;
    }

    protected Session getSession() {
        if (isNull(security.getUserPrincipal()) || isNull(agentService)) {
            return new HttpSession();
            // TODO make "admin" role configurable?
        } else if (security.isUserInRole("admin")) {
            return new HttpSession(Trellis.RepositoryAdministrator);
        }
        return new HttpSession(agentService.asAgent(security.getUserPrincipal().getName()));
    }

    protected void verifyCanAppend(final Session session, final String path) {
        if (!Trellis.RepositoryAdministrator.equals(session.getAgent()) && nonNull(accessService) &&
                !accessService.anyMatch(session, rdf.createIRI(TRELLIS_PREFIX + path),
                    iri -> ACL.Append.equals(iri) || ACL.Write.equals(iri))) {
            if (Trellis.AnonymousUser.equals(session.getAgent())) {
                throw new NotAuthorizedException("The client is not authorized");
            }
            throw new ForbiddenException("The client is forbidden from viewing this resource");
        }
    }

    protected void verifyCanControl(final Session session, final String path) {
        if (!Trellis.RepositoryAdministrator.equals(session.getAgent()) && nonNull(accessService) &&
                !accessService.canControl(session, rdf.createIRI(TRELLIS_PREFIX + path))) {
            if (Trellis.AnonymousUser.equals(session.getAgent())) {
                throw new NotAuthorizedException("The client is not authorized");
            }
            throw new ForbiddenException("The client is forbidden from viewing this resource");
        }
    }

    protected void verifyCanWrite(final Session session, final String path) {
        if (!Trellis.RepositoryAdministrator.equals(session.getAgent()) && nonNull(accessService) &&
                !accessService.canWrite(session, rdf.createIRI(TRELLIS_PREFIX + path))) {
            if (Trellis.AnonymousUser.equals(session.getAgent())) {
                throw new NotAuthorizedException("The client is not authorized");
            }
            throw new ForbiddenException("The client is forbidden from viewing this resource");
        }
    }

    protected void verifyCanRead(final Session session, final String path) {
        if (!Trellis.RepositoryAdministrator.equals(session.getAgent()) && nonNull(accessService) &&
                !accessService.canRead(session, rdf.createIRI(TRELLIS_PREFIX + path))) {
            if (Trellis.AnonymousUser.equals(session.getAgent())) {
                throw new NotAuthorizedException("The client is not authorized");
            }
            throw new ForbiddenException("The client is forbidden from viewing this resource");
        }
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
