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

import javax.ws.rs.HeaderParam;

import org.trellisldp.http.domain.AcceptDatetime;
import org.trellisldp.http.domain.Prefer;
import org.trellisldp.http.domain.Range;
import org.trellisldp.http.domain.WantDigest;

/**
 * @author acoburn
 */
public class LdpGetRequest extends LdpBaseRequest {

    /**
     * An Accept-Datetime header
     */
    @HeaderParam("Accept-Datetime")
    public AcceptDatetime datetime;

    /**
     * A Prefer header
     */
    @HeaderParam("Prefer")
    public Prefer prefer;

    /**
     * A Want-Digest header
     */
    @HeaderParam("Want-Digest")
    public WantDigest digest;

    /**
     * A Range header
     */
    @HeaderParam("Range")
    public Range range;

}
