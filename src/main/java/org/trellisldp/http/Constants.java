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

import static java.util.Collections.unmodifiableMap;
import static javax.ws.rs.core.Response.Status.NOT_ACCEPTABLE;

import java.util.HashMap;
import java.util.Map;

/**
 * @author acoburn
 */
final class Constants {

    public static final String APPLICATION_LINK_FORMAT = "application/link-format";

    public static final String ACCEPT_DATETIME = "Accept-Datetime";

    public static final String ACCEPT_PATCH = "Accept-Patch";

    public static final String ACCEPT_POST = "Accept-Post";

    public static final String DIGEST = "Digest";

    public static final String MEMENTO_DATETIME = "Memento-Datetime";

    public static final String PREFER = "Prefer";

    public static final String PREFERENCE_APPLIED = "Preference-Applied";

    public static final String TRELLIS_PREFIX = "trellis:";

    public static final String VARY = "Vary";

    public static final String WANT_DIGEST = "Want-Digest";

    public static final Map<String, Object> NOT_ACCEPTABLE_ERROR = unmodifiableMap(new HashMap<String, Object>() { {
        put("code", NOT_ACCEPTABLE.getStatusCode());
        put("message", "HTTP " + NOT_ACCEPTABLE.getStatusCode() + " " + NOT_ACCEPTABLE.getReasonPhrase());
    }});

    private Constants() {
        // prevent instantiation
    }
}
