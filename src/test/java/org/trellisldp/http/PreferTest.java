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

import static java.util.Optional.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * @author acoburn
 */
public class PreferTest {

    @Test
    public void testPrefer1() {
        final Prefer prefer = new Prefer("return=representation; include=\"http://example.org/test\"");
        assertEquals(of("representation"), prefer.getPreference());
        assertEquals(1L, prefer.getInclude().size());
        assertTrue(prefer.getInclude().contains("http://example.org/test"));
        assertTrue(prefer.getOmit().isEmpty());
        assertFalse(prefer.getHandling().isPresent());
        assertFalse(prefer.getWait().isPresent());
        assertFalse(prefer.getRespondAsync());
        assertFalse(prefer.getDepthNoroot());
    }

    @Test
    public void testPrefer2() {
        final Prefer prefer = new Prefer("return  =  representation;   include =  \"http://example.org/test\"");
        assertEquals(of("representation"), prefer.getPreference());
        assertEquals(1L, prefer.getInclude().size());
        assertTrue(prefer.getInclude().contains("http://example.org/test"));
        assertTrue(prefer.getOmit().isEmpty());
        assertFalse(prefer.getHandling().isPresent());
        assertFalse(prefer.getWait().isPresent());
        assertFalse(prefer.getRespondAsync());
        assertFalse(prefer.getDepthNoroot());
    }

    @Test
    public void testPrefer3() {
        final Prefer prefer = new Prefer("return=minimal");
        assertEquals(of("minimal"), prefer.getPreference());
        assertTrue(prefer.getInclude().isEmpty());
        assertTrue(prefer.getOmit().isEmpty());
        assertFalse(prefer.getHandling().isPresent());
        assertFalse(prefer.getWait().isPresent());
        assertFalse(prefer.getRespondAsync());
        assertFalse(prefer.getDepthNoroot());
    }

    @Test
    public void testPrefer4() {
        final Prefer prefer = new Prefer("return=other");
        assertTrue(prefer.getInclude().isEmpty());
        assertTrue(prefer.getOmit().isEmpty());
        assertFalse(prefer.getPreference().isPresent());
        assertFalse(prefer.getHandling().isPresent());
        assertFalse(prefer.getWait().isPresent());
        assertFalse(prefer.getRespondAsync());
        assertFalse(prefer.getDepthNoroot());
    }

    @Test
    public void testPrefer5() {
        final Prefer prefer = new Prefer("return=representation; omit=\"http://example.org/test\"");
        assertEquals(of("representation"), prefer.getPreference());
        assertTrue(prefer.getInclude().isEmpty());
        assertFalse(prefer.getOmit().isEmpty());
        assertTrue(prefer.getOmit().contains("http://example.org/test"));
        assertFalse(prefer.getHandling().isPresent());
        assertFalse(prefer.getWait().isPresent());
        assertFalse(prefer.getRespondAsync());
        assertFalse(prefer.getDepthNoroot());
    }

    @Test
    public void testPrefer6() {
        final Prefer prefer = new Prefer("handling=lenient; return=minimal");
        assertTrue(prefer.getInclude().isEmpty());
        assertTrue(prefer.getOmit().isEmpty());
        assertEquals(of("minimal"), prefer.getPreference());
        assertEquals(of("lenient"), prefer.getHandling());
        assertFalse(prefer.getWait().isPresent());
        assertFalse(prefer.getRespondAsync());
        assertFalse(prefer.getDepthNoroot());
    }

    @Test
    public void testPrefer7() {
        final Prefer prefer = new Prefer("respond-async; depth-noroot");
        assertTrue(prefer.getInclude().isEmpty());
        assertTrue(prefer.getOmit().isEmpty());
        assertFalse(prefer.getPreference().isPresent());
        assertFalse(prefer.getHandling().isPresent());
        assertFalse(prefer.getWait().isPresent());
        assertTrue(prefer.getRespondAsync());
        assertTrue(prefer.getDepthNoroot());
    }
}
