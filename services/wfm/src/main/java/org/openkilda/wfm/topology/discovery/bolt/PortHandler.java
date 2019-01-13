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
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.discovery.model.PortCommand;
import org.openkilda.wfm.topology.discovery.model.PostponedPortCommand;
import org.openkilda.wfm.topology.discovery.service.DiscoveryService;
import org.openkilda.wfm.topology.discovery.service.IPortReply;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class PortHandler extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.PORT_HANDLER.toString();

    private final transient PersistenceManager persistenceManager;

    private transient DiscoveryService discoveryService;
    private transient List<PostponedPortCommand> postponedCommands;

    public PortHandler(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        String source = input.getSourceComponent();
        if (MonotonicTick.BOLT_ID.equals(source)) {
            handleMonotonicTick(input);
        } else if (SwitchHandler.BOLT_ID.equals(source)) {
            handleSwitchCommand(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleMonotonicTick(Tuple input) throws PipelineException {
        long tickTime = pullValue(input, MonotonicTick.FIELD_ID_TIME_MILLIS, Long.class);
        OutputAdapter outputAdapter = new OutputAdapter(this, input);
        for (Iterator<PostponedPortCommand> iterator = postponedCommands.iterator(); iterator.hasNext();) {
            PostponedPortCommand postponed = iterator.next();
            if (postponed.isApplicable(tickTime)) {
                PortCommand command = postponed.getCommand();
                command.apply(discoveryService, outputAdapter);
                iterator.remove();
            }
        }
        discoveryService.timerTick(tickTime);
    }

    private void handleSwitchCommand(Tuple input) throws PipelineException {
        PortCommand command = pullValue(input, SwitchHandler.FIELD_ID_PAYLOAD, PortCommand.class);
        OutputAdapter outputAdapter = new OutputAdapter(this, input);
        command.apply(discoveryService, outputAdapter);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        // TODO
    }

    @Override
    protected void init() {
        discoveryService = new DiscoveryService(persistenceManager);
        postponedCommands = new LinkedList<>();
    }

    static class OutputAdapter extends AbstractOutputAdapter implements IPortReply {
        OutputAdapter(PortHandler owner, Tuple tuple) {
            super(owner, tuple);
        }

        // TODO
    }
}
