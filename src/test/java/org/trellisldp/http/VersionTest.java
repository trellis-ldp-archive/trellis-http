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

import static org.junit.Assert.assertEquals;

import javax.ws.rs.WebApplicationException;

import org.junit.Test;

/**
 * @author acoburn
 */
public class VersionTest {

    @Test
    public void testVersion() {
        final Version v = new Version("1493646202676");
        assertEquals("2017-05-01T13:43:22.676Z", v.getInstant().toString());
        assertEquals("2017-05-01T13:43:22.676Z", v.toString());
    }

    @Test(expected = WebApplicationException.class)
    public void testInvalidVersion() {
        final Version v = new Version("blah");
    }

    @Test(expected = WebApplicationException.class)
    public void testBadValue() {
        final Version v = new Version("-13.12");
    }

    @Test(expected = WebApplicationException.class)
    public void testNullValue() {
        final Version v = new Version(null);
    }
}
