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
package org.trellisldp.http.domain;

import static java.lang.String.join;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import javax.ws.rs.WebApplicationException;

/**
 * A class representing an HTTP Prefer header
 *
 * @author acoburn
 *
 * @see <a href="https://tools.ietf.org/html/rfc7240">RFC 7240</a> and
 * <a href="https://www.iana.org/assignments/http-parameters/http-parameters.xhtml#preferences">IANA values</a>
 */
public class Prefer {

    public static final String PREFER_REPRESENTATION = "representation";

    public static final String PREFER_MINIMAL = "minimal";

    public static final String PREFER_STRICT = "strict";

    public static final String PREFER_LENIENT = "lenient";

    public static final String PREFER_RETURN = "return";

    public static final String PREFER_INCLUDE = "include";

    public static final String PREFER_OMIT = "omit";

    public static final String PREFER_HANDLING = "handling";

    public static final String PREFER_WAIT = "wait";

    private final Optional<String> preference;

    private final Optional<String> handling;

    private final Optional<Integer> wait;

    private final List<String> include;

    private final List<String> omit;

    private final Set<String> params = new HashSet<>();

    /**
     * Create a Prefer header representation
     * @param prefer the value of the Prefer header
     */
    public Prefer(final String prefer) {
        final Map<String, String> data = new HashMap<>();

        if (nonNull(prefer)) {
            stream(prefer.split(";")).map(String::trim).map(pref -> pref.split("=", 2)).forEach(x -> {
                if (x.length == 2) {
                    data.put(x[0].trim(), x[1].trim());
                } else {
                    this.params.add(x[0].trim());
                }
            });
        } else {
            throw new WebApplicationException(BAD_REQUEST);
        }

        this.preference = ofNullable(data.get(PREFER_RETURN))
            .filter(x -> x.equals(PREFER_MINIMAL) || x.equals(PREFER_REPRESENTATION));
        this.handling = ofNullable(data.get(PREFER_HANDLING))
            .filter(x -> x.equals(PREFER_LENIENT) || x.equals(PREFER_STRICT));
        this.wait = ofNullable(data.get(PREFER_WAIT)).map(Integer::parseInt);
        this.omit = parseParameter(data.get(PREFER_OMIT));
        this.include = parseParameter(data.get(PREFER_INCLUDE));
    }

    /**
     * Get the preferred return type
     * @return the preferred return type
     */
    public Optional<String> getPreference() {
        return preference;
    }

    /**
     * Get the handling type
     * @return the preferred handling type
     */
    public Optional<String> getHandling() {
        return handling;
    }

    /**
     * Get the value of the wait parameter, if set
     * @return the value of the wait parameter, if available
     */
    public Optional<Integer> getWait() {
        return wait;
    }

    /**
     * Identify whether the respond-async parameter was set
     * @return true if the respond-async parameter was set; false otherwise
     */
    public Boolean getRespondAsync() {
        return params.contains("respond-async");
    }

    /**
     * Identify whether the depth-noroot parameter was set
     * @return true if the depth-noroot parameter was set; false otherwise
     */
    public Boolean getDepthNoroot() {
        return params.contains("depth-noroot");
    }

    /**
     * Get the preferred include IRIs
     * @return the list of IRIs to be included in the representation
     */
    public List<String> getInclude() {
        return unmodifiableList(include);
    }

    /**
     * Get the preferred omit IRIs
     * @return the list of IRIs to be omitted from the representation
     */
    public List<String> getOmit() {
        return unmodifiableList(omit);
    }

    private static List<String> parseParameter(final String param) {
        return ofNullable(param).map(trimQuotes).map(x -> asList(x.split("\\s+"))).orElseGet(Collections::emptyList);
    }

    private static Function<String, String> trimQuotes = param ->
        param.startsWith("\"") && param.endsWith("\"") && param.length() > 1 ?
            param.substring(1, param.length() - 1) : param;

    /**
     * Build a Prefer object with a set of included IRIs
     * @param includes the IRIs to include
     * @return the Prefer object
     */
    public static Prefer ofInclude(final String... includes) {
        final List<String> iris = asList(includes);
        if (iris.isEmpty()) {
            return new Prefer(join("=", PREFER_RETURN, PREFER_REPRESENTATION));
        }
        return new Prefer(join("=", PREFER_RETURN, PREFER_REPRESENTATION) + "; " + PREFER_INCLUDE + "=\"" +
                iris.stream().collect(joining(" ")) + "\"");
    }

    /**
     * Build a Prefer object with a set of omitted IRIs
     * @param omits the IRIs to omit
     * @return the Prefer object
     */
    public static Prefer ofOmit(final String... omits) {
        final List<String> iris = asList(omits);
        if (iris.isEmpty()) {
            return new Prefer(join("=", PREFER_RETURN, PREFER_REPRESENTATION));
        }
        return new Prefer(join("=", PREFER_RETURN, PREFER_REPRESENTATION) + "; " + PREFER_OMIT + "=\"" +
                iris.stream().collect(joining(" ")) + "\"");
    }
}
