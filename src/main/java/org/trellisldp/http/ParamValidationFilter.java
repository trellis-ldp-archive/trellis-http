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
import static java.util.Optional.ofNullable;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Link;

import org.trellisldp.http.domain.AcceptDatetime;
import org.trellisldp.http.domain.Digest;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.http.domain.Range;
import org.trellisldp.http.domain.Version;

/**
 * @author acoburn
 */
@PreMatching
public class ParamValidationFilter implements ContainerRequestFilter {

    @Override
    public void filter(final ContainerRequestContext ctx) throws IOException {
        ofNullable(ctx.getHeaderString("Accept-Datetime")).ifPresent(x -> {
            if (isNull(AcceptDatetime.valueOf(x))) {
                ctx.abortWith(status(BAD_REQUEST).build());
            }
        });

        ofNullable(ctx.getHeaderString("Prefer")).ifPresent(x -> {
            if (isNull(Prefer.valueOf(x))) {
                ctx.abortWith(status(BAD_REQUEST).build());
            }
        });

        ofNullable(ctx.getHeaderString("Range")).ifPresent(x -> {
            if (isNull(Range.valueOf(x))) {
                ctx.abortWith(status(BAD_REQUEST).build());
            }
        });

        ofNullable(ctx.getHeaderString("Link")).ifPresent(x -> {
            try {
                Link.valueOf(x);
            } catch (final IllegalArgumentException ex) {
                ctx.abortWith(status(BAD_REQUEST).build());
            }
        });

        ofNullable(ctx.getHeaderString("Digest")).ifPresent(x -> {
            if (isNull(Digest.valueOf(x))) {
                ctx.abortWith(status(BAD_REQUEST).build());
            }
        });

        ofNullable(ctx.getUriInfo().getQueryParameters().getFirst("version")).ifPresent(x -> {
            if (isNull(Version.valueOf(x))) {
                ctx.abortWith(status(BAD_REQUEST).build());
            }
        });
    }
}
