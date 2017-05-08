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

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.nonNull;
import static java.util.Optional.ofNullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * @author acoburn
 *
 * @see <a href="https://tools.ietf.org/html/rfc7240">RFC 7240</a> and
 * <a href="https://www.iana.org/assignments/http-parameters/http-parameters.xhtml#preferences">IANA values</a>
 */
class Prefer {

    private final Optional<String> preference;

    private final Optional<String> handling;

    private final Optional<Integer> wait;

    private final List<String> include;

    private final List<String> omit;

    private final Set<String> params = new HashSet<>();

    /**
     * Create a Prefer header representation
     * @param prefer the Prefer header
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
        }

        this.preference = ofNullable(data.get("return")).filter(x -> x.equals("minimal") || x.equals("representation"));
        this.handling = ofNullable(data.get("handling")).filter(x -> x.equals("lenient") || x.equals("strict"));
        this.wait = ofNullable(data.get("wait")).map(Integer::parseInt);
        this.omit = parseParameter(data.get("omit"));
        this.include = parseParameter(data.get("include"));
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
        return ofNullable(param).map(trimQuotes).map(val -> asList(val.split("\\s+"))).orElse(emptyList());
    }

    private static Function<String, String> trimQuotes = param ->
        param.startsWith("\"") && param.endsWith("\"") && param.length() > 1 ?
            param.substring(1, param.length() - 1) : param;
}
