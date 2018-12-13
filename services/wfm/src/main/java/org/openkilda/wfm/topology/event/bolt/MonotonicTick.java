/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.event.bolt;

import org.openkilda.wfm.share.bolt.AbstractTick;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MonotonicTick extends AbstractTick {
    public static final String BOLT_ID = ComponentId.MONOTONIC_TICK.toString();

    private static final int TICK_PERIOD = 1;

    public MonotonicTick() {
        super(TICK_PERIOD);
    }
}