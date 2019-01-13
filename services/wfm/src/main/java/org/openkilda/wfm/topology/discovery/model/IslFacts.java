/* Copyright 2019 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.discovery.model;

import org.openkilda.messaging.info.event.IslInfoData;

import lombok.Value;

import java.io.Serializable;

@Value
public class IslFacts implements Serializable {
    private Endpoint source;
    private Endpoint dest;

    private long speed;
    private long availableBandwidth;

    public IslFacts(IslInfoData speakerIslData) {
        source = new Endpoint(speakerIslData.getSource());
        dest = new Endpoint(speakerIslData.getDestination());

        speed = speakerIslData.getSpeed();
        availableBandwidth = speakerIslData.getAvailableBandwidth();
    }
}
