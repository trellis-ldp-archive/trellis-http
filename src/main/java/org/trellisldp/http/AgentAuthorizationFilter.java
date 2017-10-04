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
import static javax.ws.rs.Priorities.AUTHENTICATION;
import static org.trellisldp.http.domain.HttpConstants.SESSION_PROPERTY;
import static org.trellisldp.vocabulary.Trellis.RepositoryAdministrator;

import java.io.IOException;

import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.SecurityContext;

import org.trellisldp.api.AgentService;
import org.trellisldp.http.impl.HttpSession;

/**
 * @author acoburn
 */
@PreMatching
@Priority(AUTHENTICATION - 100)
public class AgentAuthorizationFilter implements ContainerRequestFilter {

    private final AgentService agentService;
    private final String adminRole;

    /**
     * Create an authorization filter
     * @param agentService the agent service
     * @param adminRole the admin role
     */
    public AgentAuthorizationFilter(final AgentService agentService, final String adminRole) {
        this.agentService = agentService;
        this.adminRole = adminRole;
    }

    @Override
    public void filter(final ContainerRequestContext ctx) throws IOException {
        final SecurityContext sec = ctx.getSecurityContext();
        if (isNull(sec.getUserPrincipal())) {
            ctx.setProperty(SESSION_PROPERTY, new HttpSession());
        } else if (sec.isUserInRole(adminRole)) {
            ctx.setProperty(SESSION_PROPERTY, new HttpSession(RepositoryAdministrator));
        } else {
            ctx.setProperty(SESSION_PROPERTY, new HttpSession(agentService.asAgent(sec.getUserPrincipal().getName())));
        }
    }
}
