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

import lombok.Value;

@Value
public class IslReference {
    private final Endpoint source;
    private final Endpoint dest;

    public IslReference(Endpoint source, Endpoint dest) {
        if (source.getDatapath().compareTo(dest.getDatapath()) < 0) {
            this.source = source;
            this.dest = dest;
        } else {
            this.source = dest;
            this.dest = source;
        }
    }
}
