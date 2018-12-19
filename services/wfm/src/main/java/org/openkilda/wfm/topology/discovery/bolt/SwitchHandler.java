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

package org.openkilda.wfm.topology.discovery.bolt;

import org.openkilda.wfm.AbstractBolt;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

public class SwitchHandler extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.SWITCH_HANDLER.toString();

    public static final String FIELD_ID_SWITCH_ID = SpeakerMonitor.FIELD_ID_SWITCH_ID;
    public static final String FIELD_ID_PORT_NUMBER = "port-number";
    public static final String FIELD_ID_PAYLOAD = "payload";

    public static final String STREAM_PORT_ID = "ports";
    public static final Fields STREAM_PORT_FIELDS = new Fields(FIELD_ID_SWITCH_ID, FIELD_ID_PORT_NUMBER,
                                                               FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);

    @Override
    protected void handleInput(Tuple input) {

    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declareStream(STREAM_PORT_ID, STREAM_PORT_FIELDS);
    }
}
