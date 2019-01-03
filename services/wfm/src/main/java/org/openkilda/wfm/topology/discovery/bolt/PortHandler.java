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

package org.openkilda.wfm.topology.discovery.bolt;

import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.AbstractOutputAdapter;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.topology.discovery.service.IPortReply;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

public class PortHandler extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.PORT_HANDLER.toString();

    private final transient PersistenceManager persistenceManager;

    public PortHandler(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String source = input.getSourceComponent();
        if (SwitchHandler.BOLT_ID.equals(source)) {
            handleSwitchCommand(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleSwitchCommand(Tuple input) {
        // TODO
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        // TODO
    }

    public static class OutputAdapter extends AbstractOutputAdapter implements IPortReply {
        public OutputAdapter(AbstractBolt owner, Tuple tuple) {
            super(owner, tuple);
        }
    }
}
