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

package org.openkilda.wfm.share.hubandspoke;

import static java.util.Objects.requireNonNull;
import static org.openkilda.wfm.share.hubandspoke.Components.BOLT_COORDINATOR;

import org.openkilda.wfm.topology.utils.MessageTranslator;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class WorkerBolt extends BaseRichBolt implements CoordinatorClient {

    private Config workerConfig;
    private OutputCollector collector;

    private Map<String, Tuple> tasks = new HashMap<>();

    public WorkerBolt(Config config) {
        requireNonNull(config.getStreamToHub(), "Stream to hub bolt cannot be null");
        requireNonNull(config.getHubComponent(), "Hub bolt id cannot be null");
        requireNonNull(config.getWorkerSpoutComponent(), "Worker's spout id cannot be null");

        this.workerConfig = config;
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
        if (workerConfig.autoAck) {
            collector.ack(input);
        }
        String key = input.getStringByField(MessageTranslator.KEY_FIELD);
        String sender = input.getSourceComponent();

        if (workerConfig.getHubComponent().equals(sender)) {
            tasks.put(key, input);
            registerCallback(key);
            onHubRequest(input);
        } else if (tasks.containsKey(key)) {
            if (workerConfig.getWorkerSpoutComponent().equals(sender)) {
                success(key);
                onAsyncResponse(input);
            } else if (BOLT_COORDINATOR.equals(sender)) {
                onTimeout(key);
            }
        } else {
            log.error("Received unexpected tuple {}", input);
        }
    }

    @Override
    public int getDefaultTimeout() {
        return workerConfig.defaultTimeout;
    }

    @Override
    public OutputCollector getOutputCollector() {
        return collector;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declareStream(workerConfig.getStreamToHub(), true, MessageTranslator.FIELDS);

        declareCoordinatorStream(declarer);
    }

    protected abstract void onHubRequest(Tuple input);

    protected abstract void onAsyncResponse(Tuple input);

    protected abstract void onTimeout(String key);

    @Builder
    @Getter
    public static class Config {
        private String streamToHub;
        private String hubComponent;
        private String workerSpoutComponent;
        private int defaultTimeout = 100;
        private boolean autoAck = true;
    }
}
