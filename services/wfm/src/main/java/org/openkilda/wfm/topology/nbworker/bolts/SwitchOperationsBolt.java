/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.bolts;

import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.info.event.SwitchState;
import org.openkilda.messaging.model.SwitchId;
import org.openkilda.messaging.nbtopology.request.BaseRequest;
import org.openkilda.messaging.nbtopology.request.GetSwitchesRequest;
import org.openkilda.pce.provider.Auth;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

import java.util.ArrayList;
import java.util.List;

public class SwitchOperationsBolt extends NeoOperationsBolt {
    public SwitchOperationsBolt(Auth neoAuth) {
        super(neoAuth);
    }

    @Override
    List<? extends InfoData> processRequest(Tuple tuple, BaseRequest request, Session session) {
        List<? extends InfoData> result = null;
        if (request instanceof GetSwitchesRequest) {
            result = getSwitches(session);
        } else {
            unhandledInput(tuple);
        }

        return result;
    }

    private List<SwitchInfoData> getSwitches(Session session) {
        StatementResult result = session.run("MATCH (sw:switch) "
                + "RETURN "
                + "sw.name as name, "
                + "sw.address as address, "
                + "sw.hostname as hostname, "
                + "sw.description as description, "
                + "sw.controller as controller, "
                + "sw.state as state");
        List<SwitchInfoData> results = new ArrayList<>();
        for (Record record : result.list()) {
            String status = record.get("state").asString();
            SwitchState st = "active".equals(status) ? SwitchState.ACTIVATED : SwitchState.DEACTIVATED;

            results.add(SwitchInfoData.builder()
                    .switchId(new SwitchId(record.get("name").asString()))
                    .state(st)
                    .address(record.get("address").asString())
                    .controller(record.get("controller").asString())
                    .description(record.get("description").asString())
                    .hostname(record.get("hostname").asString())
                    .build());
        }
        log.debug("Found switches: {}", results.size());

        return results;
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields("response", "correlationId"));
    }
}
