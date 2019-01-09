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

package org.openkilda.wfm.topology.floodlightrouter.bolts;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.messaging.Message;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandMessage;
import org.openkilda.messaging.command.discovery.DiscoverIslCommandData;
import org.openkilda.messaging.info.InfoMessage;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.floodlightrouter.ComponentType;
import org.openkilda.wfm.topology.floodlightrouter.Stream;

import org.apache.storm.state.InMemoryKeyValueState;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseStatefulBolt;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

public class RouterBolt extends BaseStatefulBolt<InMemoryKeyValueState<SwitchId, String>> {

    private static final Logger logger = LoggerFactory.getLogger(RouterBolt.class);


    private final Set<String> floodlights;
    private transient TopologyContext context;
    private transient InMemoryKeyValueState<SwitchId, String> state;
    protected OutputCollector outputCollector;


    public RouterBolt(Set<String> floodlights) {
        this.floodlights = floodlights;
    }

    @Override
    public void execute(Tuple input) {
        String sourceComponent = input.getSourceComponent();

        try {
            String json = input.getValueByField(AbstractTopology.MESSAGE_FIELD).toString();
            Message message = MAPPER.readValue(json, Message.class);
            switch (sourceComponent) {
                case ComponentType.ROUTER_TOPO_DISCO_SPOUT:
                    processDiscoveryMessage(input, json, message);
                    break;
                case ComponentType.ROUTER_SPEAKER_KAFKA_SPOUT:
                    dispatchToSpeaker(input, json, message);
                    break;
                case ComponentType.SPEAKER_DISCO_KAFKA_SPOUT:
                    dispatchToDiscoSpeaker(input, json, message);
                    break;
                default:
                    break;
            }

            // outputCollector.emit(Stream.TOPO_DISCO, input, new Values(MAPPER.writeValueAsString(message)));
            System.out.println(message);
        } catch (Exception e) {
            logger.error("failed to process message");
        }


        outputCollector.ack(input);

    }

    private void processDiscoveryMessage(Tuple input, String json, Message message) {
        if (message instanceof InfoMessage) {
            InfoMessage infoMessage = (InfoMessage) message;
            if (infoMessage.getData() instanceof SwitchInfoData) {
                SwitchInfoData payload = (SwitchInfoData) infoMessage.getData();
                this.state.put(payload.getSwitchId(), infoMessage.getRegion());
            }
        }
        Values values = new Values(json);
        outputCollector.emit(Stream.TOPO_DISCO, input, values);

    }

    private void dispatchToSpeaker(Tuple input, String json, Message message) {
        for (String region : floodlights) {
            String stream = Stream.SPEAKER + "_" + region;
            Values values = new Values(json);
            outputCollector.emit(stream, input, values);
        }
    }

    private void dispatchToDiscoSpeaker(Tuple input, String json, Message message) {
        if (message instanceof CommandMessage) {
            CommandMessage commandMessage = (CommandMessage) message;
            CommandData commandData = commandMessage.getData();
            if (commandData instanceof DiscoverIslCommandData) {
                DiscoverIslCommandData data = (DiscoverIslCommandData) commandData;
                String region = this.state.get(data.getSwitchId());
                String stream = Stream.SPEAKER_DISCO + "_" + region;
                Values values = new Values(json);
                outputCollector.emit(stream, input, values);
            }
        }
    }

    private void handleInfoMessage(InfoMessage message) {
        System.out.println(message.getRegion());
    }

    @Override
    public void initState(InMemoryKeyValueState<SwitchId, String> state) {
        this.state = state;
    }

    @Override
    public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
        this.context = topologyContext;
        this.outputCollector = outputCollector;
        super.prepare(map, topologyContext, outputCollector);
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
        for (String region : floodlights) {
            outputFieldsDeclarer.declareStream(Stream.SPEAKER + "_" + region,
                    new Fields(AbstractTopology.MESSAGE_FIELD));
            outputFieldsDeclarer.declareStream(Stream.SPEAKER_DISCO + "_" + region,
                    new Fields(AbstractTopology.MESSAGE_FIELD));
        }
        outputFieldsDeclarer.declareStream(Stream.TOPO_DISCO, new Fields(AbstractTopology.MESSAGE_FIELD));
    }
}
