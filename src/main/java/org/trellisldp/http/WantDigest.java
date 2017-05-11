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

import static java.lang.Float.parseFloat;
import static java.util.Arrays.stream;
import static java.util.Map.Entry.comparingByValue;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A class representing an HTTP Want-Digest header
 *
 * @author acoburn
 *
 * @see <a href="https://tools.ietf.org/html/rfc3230">RFC 3230</a>
 */
public class WantDigest {

    private final List<String> algorithms;

    /**
     * Create a Want-Digest header representation
     * @param wantDigest the value of the Want-Digest header
     */
    public WantDigest(final String wantDigest) {
        final Map<String, Float> algs = new HashMap<>();
        if (nonNull(wantDigest)) {
            stream(wantDigest.split(",")).map(String::trim).forEach(alg -> {
                final String[] parts = alg.split(";", 2);
                final String key = parts[0];
                if (parts.length == 2) {
                    algs.put(key, getValue(parts[1]));
                } else {
                    algs.put(key, 1.0f);
                }
            });
        }
        this.algorithms = algs.entrySet().stream().sorted(comparingByValue())
            .map(Map.Entry::getKey).map(String::toUpperCase).collect(toList());
    }

    /**
     * Fetch the list of specified algorithms in preference order
     * @return the algorithms
     */
    public List<String> getAlgorithms() {
        return algorithms;
    }

    private float getValue(final String val) {
        if (nonNull(val)) {
            try {
                return parseFloat(val);
            } catch (final NumberFormatException ex) {
                return 0.0f;
            }
        }
        return 1.0f;
    }

}
