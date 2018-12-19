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

package org.openkilda.wfm.topology.discovery.service;

import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.discovery.bolt.SwitchHandler;
import org.openkilda.wfm.topology.discovery.model.PortInit;
import org.openkilda.wfm.topology.discovery.model.SwitchInit;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;

@Slf4j
public class DiscoveryService {
    private final PersistenceManager persistenceManager;

    public DiscoveryService(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    public void prepopulate(ISwitchPrepopulateReply reply) {
        for (SwitchInit persistent : loadPersistent()) {
            reply.prepopulateSwitch(persistent);
        }
    }

    private Collection<SwitchInit> loadPersistent() {
        RepositoryFactory repositoryFactory = persistenceManager.getRepositoryFactory();
        SwitchRepository switchRepository = repositoryFactory.createSwitchRepository();

        HashMap<SwitchId, SwitchInit> switchById = new HashMap<>();
        for (Switch switchEntry : switchRepository.findAll()) {
            SwitchId switchId = switchEntry.getSwitchId();
            switchById.put(switchId, new SwitchInit(switchId));
        }

        IslRepository islRepository = repositoryFactory.createIslRepository();
        for (Isl islEntry : islRepository.findAll()) {
            SwitchInit swInit = switchById.get(islEntry.getSrcSwitch().getSwitchId());
            if (swInit == null) {
                log.error("Orphaned ISL relation - {}-{} (read race condition?)",
                          islEntry.getSrcSwitch().getSwitchId(), islEntry.getSrcPort());
                continue;
            }

            try {
                NetworkEndpoint remote = new NetworkEndpoint(islEntry.getDestSwitch().getSwitchId(),
                                                             islEntry.getDestPort());
                swInit.addPort(new PortInit(islEntry.getSrcPort(), false, remote));
            } catch (IllegalArgumentException e) {
                log.error("Corrupter ISL relation endpoint(dest) - {}-{}",
                          islEntry.getDestSwitch().getSwitchId(), islEntry.getDestPort());
            }
        }

        return switchById.values();
    }

    public void switchAdd(SwitchInit init, SwitchHandler.OutputAdapter outputAdapter) {

    }
}
