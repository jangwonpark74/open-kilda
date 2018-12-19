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

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.AbstractOutputAdapter;
import org.openkilda.wfm.topology.discovery.model.SwitchInit;
import org.openkilda.wfm.topology.discovery.service.DiscoveryService;
import org.openkilda.wfm.topology.discovery.service.ISwitchPrepopulateReply;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

public class SwitchPreloader extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.SWITCH_PRELOADER.toString();

    public static final String FIELD_ID_SWITCH_ID = SpeakerMonitor.FIELD_ID_SWITCH_ID;
    public static final String FIELD_ID_SWITCH_INIT = "switch-init";

    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_SWITCH_ID, FIELD_ID_SWITCH_INIT);

    private final PersistenceManager persistenceManager;
    private transient DiscoveryService discovery;

    private boolean workDone = false;

    public SwitchPreloader(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void handleInput(Tuple input) {
        if (workDone) {
            return;
        }

        String source = input.getSourceComponent();
        if (MonotonicTick.BOLT_ID.equals(source)) {
            workDone = true;
            discovery.prepopulate(new OutputAdapter(this, input));
        } else {
            unhandledInput(input);
        }
    }

    @Override
    protected void init() {
        discovery = new DiscoveryService(persistenceManager);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declare(STREAM_FIELDS);
    }

    public static class OutputAdapter extends AbstractOutputAdapter implements ISwitchPrepopulateReply {
        public OutputAdapter(AbstractBolt owner, Tuple tuple) {
            super(owner, tuple);
        }

        public void prepopulateSwitch(SwitchInit switchInit) {
            emit(new Values(switchInit.getSwitchId(), switchInit));
        }
    }
}
