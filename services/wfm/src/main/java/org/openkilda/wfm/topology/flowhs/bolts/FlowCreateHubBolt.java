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

package org.openkilda.wfm.topology.flowhs.bolts;

import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.CREATE_HUB_NB;
import static org.openkilda.wfm.topology.flowhs.FlowHsTopology.Stream.FLOW_INSTALLER_CREATE_HUB;

import org.openkilda.wfm.share.hubandspoke.HubBolt;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

@Slf4j
public class FlowCreateHubBolt extends HubBolt {

    public FlowCreateHubBolt(String spoutId, int timeoutMs, boolean autoAck) {
        super(spoutId, timeoutMs, autoAck);
    }

    @Override
    protected void onRequest(Tuple input) {

    }

    @Override
    protected void onWorkerResponse(Tuple input) {

    }

    @Override
    public void onTimeout(String key) {

    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        super.declareOutputFields(declarer);

        declarer.declareStream(FLOW_INSTALLER_CREATE_HUB.name(), MessageTranslator.STREAM_FIELDS);
        declarer.declareStream(CREATE_HUB_NB.name(), MessageTranslator.STREAM_FIELDS);
    }
}
