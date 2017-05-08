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

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.slf4j.LoggerFactory.getLogger;

import java.time.DateTimeException;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.slf4j.Logger;

/**
 * @author acoburn
 */
@Provider
public class DateTimeExceptionMapper implements ExceptionMapper<DateTimeException> {

    private static final Logger LOGGER = getLogger(DateTimeExceptionMapper.class);

    @Override
    public Response toResponse(final DateTimeException ex) {
        LOGGER.error("Invalid date provided: {}", ex.getMessage());
        return Response.status(BAD_REQUEST).entity("Invalid date provided: " + ex.getMessage()).build();
    }
}
