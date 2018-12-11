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

package org.openkilda.wfm.topology.discovery;

import org.openkilda.wfm.LaunchEnvironment;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.ping.bolt.InputDecoder;

import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.topology.TopologyBuilder;

public class DiscoveryTopology extends AbstractTopology<DiscoveryTopologyConfig> {
    public DiscoveryTopology(LaunchEnvironment env) {
        super(env, DiscoveryTopologyConfig.class);
    }

    /**
     * Discovery topology factory.
     */
    @Override
    public StormTopology createTopology() {
        TopologyBuilder topology = new TopologyBuilder();

        input(topology);
        // TODO

        return topology.createTopology();
    }

    private void input(TopologyBuilder topology) {
        KafkaSpout<String, String> spout = createKafkaSpout(
                topologyConfig.getKafkaSpeakerDiscoTopic(), ComponentId.INPUT.toString());
        topology.setSpout(ComponentId.INPUT.toString(), spout, scaleFactor);
    }

    private void inputDecoder(TopologyBuilder topology) {
        InputDecoder bolt = new InputDecoder();
        topology.setBolt(InputDecoder.BOLT_ID, bolt, scaleFactor)
                .shuffleGrouping(ComponentId.INPUT.toString());
    }

    /**
     * Discovery topology uploader.
     */
    public static void main(String[] args) {
        try {
            LaunchEnvironment env = new LaunchEnvironment(args);
            (new DiscoveryTopology(env)).setup();
        } catch (Exception e) {
            System.exit(handleLaunchException(e));
        }
    }
}
