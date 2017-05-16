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

import static org.junit.Assert.assertTrue;

import javax.ws.rs.WebApplicationException;

import org.junit.Test;

/**
 * @author acoburn
 */
public class RangeTest {

    @Test
    public void testRange() {
        final Range range = new Range("bytes=1-10");
        assertTrue(range.getFrom().equals(1));
        assertTrue(range.getTo().equals(10));
    }

    @Test(expected = WebApplicationException.class)
    public void testInvalidRange() {
        final Range range = new Range("bytes=10-1");
    }

    @Test(expected = WebApplicationException.class)
    public void testInvalidNumbers() {
        final Range range = new Range("bytes=1-15.5");
    }

    @Test(expected = WebApplicationException.class)
    public void testInvalidRange2() {
        final Range range = new Range("bytes=1-15, 20-24");
    }

    @Test(expected = WebApplicationException.class)
    public void testInvalidNumbers3() {
        final Range range = new Range("bytes=1-foo");
    }



    @Test(expected = WebApplicationException.class)
    public void testBadInput() {
        final Range range = new Range("blahblahblah");
    }

    @Test(expected = WebApplicationException.class)
    public void testNullInput() {
        final Range range = new Range(null);
    }
}
