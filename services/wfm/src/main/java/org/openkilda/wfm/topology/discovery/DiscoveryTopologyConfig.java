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

import org.openkilda.wfm.topology.AbstractTopologyConfig;

import com.sabre.oss.conf4j.annotation.Configuration;
import com.sabre.oss.conf4j.annotation.Key;

public interface DiscoveryTopologyConfig extends AbstractTopologyConfig {
    default String getKafkaSpeakerDiscoTopic() {
        return getKafkaTopics().getSpeakerDiscoTopic();
    }

    default String getKafkaSpeakerTopic() {
        return getKafkaTopics().getSpeakerTopic();
    }

    @Key("isl.cost.when.port.down")
    int getIslCostWhenPortDown();

    @Configuration
    @Key("discovery")
    interface DiscoveryConfig {
        @Key("interval")
        int getDiscoveryInterval();

        @Key("timeout")
        int getDiscoveryTimeout();

        @Key("limit")
        int getDiscoveryLimit();

        @Key("keep.removed.isl")
        int getKeepRemovedIslTimeout();

        @Key("speaker-failure-timeout")
        int getDiscoverySpeakerFailureTimeout();

        @Key("dump-request-timeout-seconds")
        int getDiscoveryDumpRequestTimeout();
    }
}
