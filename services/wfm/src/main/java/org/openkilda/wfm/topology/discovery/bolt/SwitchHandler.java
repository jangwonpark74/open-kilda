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

import org.openkilda.messaging.Message;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.Port;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.AbstractOutputAdapter;
import org.openkilda.wfm.error.PipelineException;
import org.openkilda.wfm.topology.discovery.model.SpeakerSharedSync;
import org.openkilda.wfm.topology.discovery.model.SwitchInit;
import org.openkilda.wfm.topology.discovery.service.DiscoveryService;
import org.openkilda.wfm.topology.discovery.service.ISwitchReply;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.utils.Utils;

public class SwitchHandler extends AbstractBolt {
    public static final String BOLT_ID = ComponentId.SWITCH_HANDLER.toString();

    public static final String FIELD_ID_SWITCH_ID = SpeakerMonitor.FIELD_ID_SWITCH_ID;
    public static final String FIELD_ID_PORT_NUMBER = "port-number";
    public static final String FIELD_ID_PAYLOAD = "payload";

    public static final String STREAM_PORT_ID = "ports";
    public static final Fields STREAM_PORT_FIELDS = new Fields(FIELD_ID_SWITCH_ID, FIELD_ID_PORT_NUMBER,
                                                               FIELD_ID_PAYLOAD, FIELD_ID_CONTEXT);


    private final PersistenceManager persistenceManager;

    private transient DiscoveryService discoveryService;

    public SwitchHandler(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    @Override
    protected void handleInput(Tuple input) throws PipelineException {
        String source = input.getSourceComponent();

        if (SpeakerMonitor.BOLT_ID.equals(source)) {
            handleSpeakerInput(input);
        } else if (SwitchPreloader.BOLT_ID.equals(source)) {
            handlePreloaderInput(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handleSpeakerInput(Tuple input) throws PipelineException {
        String stream = input.getSourceStreamId();

        if (Utils.DEFAULT_STREAM_ID.equals(stream)) {
            handleSpeakerMainStream(input);
        } else if (SpeakerMonitor.STREAM_REFRESH_ID.equals(stream)) {
            handleSpeakerRefreshStream(input);
        } else if (SpeakerMonitor.STREAM_SYNC_ID.equals(stream)) {
            handleSpeakerSyncStream(input);
        } else {
            unhandledInput(input);
        }
    }

    private void handlePreloaderInput(Tuple input) throws PipelineException {
        SwitchInit init = pullValue(input, SwitchPreloader.FIELD_ID_SWITCH_INIT, SwitchInit.class);
        discoveryService.switchPrecreate(init, new OutputAdapter(this, input));
    }

    private void handleSpeakerMainStream(Tuple input) throws PipelineException {
        Message message = pullValue(input, SpeakerMonitor.FIELD_ID_INPUT, Message.class);

        if (message instanceof InfoMessage) {
            processSpeakerEvent(input, ((InfoMessage) message).getData());
        } else {
            unhandledInput(input);
        }
    }

    private void handleSpeakerRefreshStream(Tuple input) throws PipelineException {
        SpeakerSwitchView switchView = pullValue(input, SpeakerMonitor.FIELD_ID_REFRESH, SpeakerSwitchView.class);
        discoveryService.switchRestoreManagement(switchView, new OutputAdapter(this, input));
    }

    private void handleSpeakerSyncStream(Tuple input) throws PipelineException {
        SpeakerSharedSync sharedSync = pullValue(input, SpeakerMonitor.FIELD_ID_SYNC, SpeakerSharedSync.class);
        discoveryService.switchSharedSync(sharedSync, new OutputAdapter(this, input));
    }

    private void processSpeakerEvent(Tuple input, InfoData event) {
        OutputAdapter outputAdapter = new OutputAdapter(this, input);
        if (event instanceof SwitchInfoData) {
            discoveryService.switchEvent((SwitchInfoData) event, outputAdapter);
        } else if (event instanceof PortInfoData) {
            discoveryService.portEvent((PortInfoData) event, outputAdapter);
        } else {
            unhandledInput(input);
        }
    }

    @Override
    protected void init() {
        discoveryService = new DiscoveryService(persistenceManager);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer streamManager) {
        streamManager.declareStream(STREAM_PORT_ID, STREAM_PORT_FIELDS);
    }

    public static class OutputAdapter extends AbstractOutputAdapter implements ISwitchReply {
        public OutputAdapter(AbstractBolt owner, Tuple tuple) {
            super(owner, tuple);
        }
    }
}
