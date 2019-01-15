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

import static org.openkilda.wfm.share.hubandspoke.Components.STREAM_TO_BOLT_COORDINATOR;

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.share.hubandspoke.CoordinatorBolt.CoordinatorCommand;
import org.openkilda.wfm.topology.utils.MessageTranslator;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;

/**
 * Interface that helps to define callbacks and timeouts for asynchronous operations.
 */
interface CoordinatorClient {

    String COMMAND_FIELD = "command";
    String TIMEOUT_FIELD = "timeout_ms";

    /**
     * Should be called once operation is finished and callback/timer should be cancelled.
     * @param key request's identifier.
     */
    default void success(String key) {
        getOutputCollector().emit(STREAM_TO_BOLT_COORDINATOR, new Values(
                key, CoordinatorCommand.CANCEL_CALLBACK,
                0,
                null
        ));
    }

    /**
     * Add callback for operation that will be called when executions of command finishes. Default timout value will be
     * used.
     * @param key operation identifier.
     */
    default void registerCallback(String key) {
        registerCallback(key, getDefaultTimeout());
    }

    /**
     * Add callback with specified timeout value.
     * @param key operation identifier.
     * @param timeout how long coordinator waits for a response. If no response received - timeout error occurs.
     */
    default void registerCallback(String key, long timeout) {
        getOutputCollector().emit(STREAM_TO_BOLT_COORDINATOR, new Values(
                key, CoordinatorCommand.REQUEST_CALLBACK,
                timeout,
                key
        ));
    }

    default void declareCoordinatorStream(OutputFieldsDeclarer declarer) {
        declarer.declareStream(STREAM_TO_BOLT_COORDINATOR, new Fields(MessageTranslator.KEY_FIELD,
                COMMAND_FIELD, TIMEOUT_FIELD, AbstractBolt.FIELD_ID_CONTEXT));
    }

    int getDefaultTimeout();

    OutputCollector getOutputCollector();
}
