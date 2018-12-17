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
import org.openkilda.wfm.error.AbstractException;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;

public class SwitchPreloader extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.SWITCH_PRELOADER.toString();

    public static final String FIELD_ID_SWITCH_ID = SpeakerMonitor.FIELD_ID_SWITCH_ID;
    public static final Fields STREAM_FIELDS = new Fields(FIELD_ID_SWITCH_ID);

    private final PersistenceManager persistenceManager;
    private boolean workDone = false;

    public SwitchPreloader(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        if (workDone) {
            return;
        }

        String source = input.getSourceComponent();
        if (MonotonicTick.BOLT_ID.equals(source)) {
            workDone = true;
            // TODO
        } else {
            unhandledInput(input);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declare(STREAM_FIELDS);
    }

    public static class OutputAdapter extends AbstractOutputAdapter {
        public OutputAdapter(AbstractBolt owner, Tuple tuple) {
            super(owner, tuple);
        }

        public void prepopulateSwitch()
    }
}
