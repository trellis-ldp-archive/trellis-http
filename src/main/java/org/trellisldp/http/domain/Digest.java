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

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;

import javax.ws.rs.WebApplicationException;

/**
 * A class representing an HTTP Digest header
 *
 * @author acoburn
 *
 * @see <a href="https://tools.ietf.org/html/rfc3230">RFC 3230</a>
 */
public class Digest {

    private final String algorithm;

    private final String digestValue;

    /**
     * Create a Digest header representation
     * @param value the value of the Digest header
     */
    public Digest(final String value) {
        final String[] parts = value.split("=", 2);
        if (parts.length == 2) {
            algorithm = parts[0];
            digestValue = parts[1];
        } else {
            throw new WebApplicationException("Invalid Digest header: " + value, BAD_REQUEST);
        }
    }

    /**
     * Create a Digest header representation
     * @param algorithm the algorithm
     * @param digestValue the digest
     */
    public Digest(final String algorithm, final String digestValue) {
        this.algorithm = algorithm;
        this.digestValue = digestValue;
    }

    /**
     * Get the algorithm
     * @return the algorithms
     */
    public String getAlgorithm() {
        return algorithm;
    }

    /**
     * Get the digest value
     * @return the digest
     */
    public String getDigest() {
        return digestValue;
    }
}
