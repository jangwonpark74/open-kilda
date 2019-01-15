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

import static org.openkilda.wfm.share.hubandspoke.Components.BOLT_COORDINATOR;
import static org.openkilda.wfm.share.hubandspoke.Components.BOLT_WORKER_PREFIX;

import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.tuple.Tuple;

import java.util.Map;

/**
 * Base class for bolts performing the role of hub that interacts with multiple workers (spokes). Defines three main
 * methods: onRequest(), onWorkerResponse() and onTimeout(). Helps to handle income external requests and specify the
 * ways how worker responses and timeouts should be processed.
 */
public abstract class HubBolt extends BaseRichBolt implements CoordinatorClient {

    protected transient OutputCollector collector;

    private final String hubSpoutComponent;
    private final boolean autoAck;
    private final int timeoutMs;

    public HubBolt(String spoutId, int timeoutMs, boolean autoAck) {
        this.hubSpoutComponent = spoutId;
        this.timeoutMs = timeoutMs;
        this.autoAck = autoAck;
    }

    @Override
    public void execute(Tuple input) {
        if (autoAck) {
            collector.ack(input);
        }
        if (hubSpoutComponent.equals(input.getSourceComponent())) {
            registerCallback(input.getStringByField(MessageTranslator.KEY_FIELD));

            onRequest(input);
        } else if (input.getSourceComponent().startsWith(BOLT_WORKER_PREFIX)) {
            onWorkerResponse(input);
        } else if (input.getSourceComponent().equals(BOLT_COORDINATOR)) {
            onTimeout(input);
        }
    }

    /**
     * Handler for income request. Define the main steps and functionality for current hub.
     * @param input income message.
     */
    protected abstract void onRequest(Tuple input);

    /**
     * Handler for all hub-related workers. Since hub might has unlimited number of workers this method handles all
     * responses from all workers.
     * @param input response from worker.
     */
    protected abstract void onWorkerResponse(Tuple input);

    /**
     * Handler for timeout for pending request and define the way how such case will be processed.
     * @param input tuple with request id.
     */
    protected abstract void onTimeout(Tuple input);

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declareCoordinatorStream(declarer);
    }

    @Override
    public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public int getDefaultTimeout() {
        return timeoutMs;
    }

    @Override
    public OutputCollector getOutputCollector() {
        return collector;
    }

}
