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

import static org.junit.Assert.assertEquals;

import javax.ws.rs.WebApplicationException;

import org.junit.Test;

/**
 * @author acoburn
 */
public class DigestTest {

    @Test
    public void testDigest() {
        final Digest d = new Digest("md5=HUXZLQLMuI/KZ5KDcJPcOA==");
        assertEquals("md5", d.getAlgorithm());
        assertEquals("HUXZLQLMuI/KZ5KDcJPcOA==", d.getDigest());
    }

    @Test
    public void testDigest2() {
        final Digest d = new Digest("md5", "HUXZLQLMuI/KZ5KDcJPcOA==");
        assertEquals("md5", d.getAlgorithm());
        assertEquals("HUXZLQLMuI/KZ5KDcJPcOA==", d.getDigest());
    }

    @Test(expected = WebApplicationException.class)
    public void testInvalidDigest() {
        final Digest d = new Digest("blah");
    }
}
