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

import static java.util.Objects.nonNull;
import static javax.ws.rs.core.HttpHeaders.LINK;
import static javax.ws.rs.core.Link.fromUri;
import static org.trellisldp.http.domain.HttpConstants.ACL_PROPERTY;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;

/**
 * @author acoburn
 */
public class WebAcHeaderFilter implements ContainerResponseFilter {

    @Override
    public void filter(final ContainerRequestContext req, final ContainerResponseContext res) throws IOException {
        final Object acl = req.getProperty(ACL_PROPERTY);
        if (nonNull(acl)) {
            res.getHeaders().add(LINK, fromUri((String) acl).rel(ACL_PROPERTY).build());
        }
    }
}
