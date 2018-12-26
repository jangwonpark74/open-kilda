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

import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsm;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsmContext;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsmState;
import org.openkilda.wfm.topology.discovery.model.OperationMode;
import org.openkilda.wfm.topology.discovery.model.PortInit;
import org.openkilda.wfm.topology.discovery.model.SpeakerSharedSync;
import org.openkilda.wfm.topology.discovery.model.SwitchInit;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
public class DiscoveryService {
    private final PersistenceManager persistenceManager;

    private final Map<SwitchId, SwitchFsm> switchController = new HashMap<>();
    private final FsmExecutor<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> switchControllerExecutor
            = SwitchFsm.makeExecutor();

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

    public void switchPrecreate(SwitchInit init, ISwitchReply outputAdapter) {
        SwitchFsm switchFsm = SwitchFsm.create();

        SwitchFsmContext fsmContext = new SwitchFsmContext(outputAdapter);
        fsmContext.setPrecreateState(init);

        switchControllerExecutor.fire(switchFsm, SwitchFsmEvent.HISTORY, fsmContext);

        switchController.put(init.getSwitchId(), switchFsm);
    }

    public void switchRestoreManagement(SpeakerSwitchView switchView, ISwitchReply outputAdapter) {
        SwitchFsmContext fsmContext = new SwitchFsmContext(outputAdapter);
        fsmContext.setOnline(true);
        fsmContext.setInitState(switchView);

        SwitchFsm fsm = getSwitchFsmCreateIfAbsent(switchView.getDatapath());
        switchControllerExecutor.fire(fsm, SwitchFsmEvent.MANAGED, fsmContext);
    }

    public void switchSharedSync(SpeakerSharedSync sharedSync, ISwitchReply outputAdapter) {
        switch (sharedSync.getMode()) {
            case MANAGED_MODE:
                // Still connected switches will be handled by {@link switchRestoreManagement}, disconnected switches
                // must be handled here.
                detectOfflineSwitches(sharedSync.getKnownSwitches(), outputAdapter);
                break;
            case UNMANAGED_MODE:
                setAllSwitchesUnmanaged(outputAdapter);
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("Unsupported %s value %s", OperationMode.class.getName(), sharedSync.getMode()));
        }
    }

    public void switchEvent(SwitchInfoData payload, ISwitchReply outputAdapter) {
        SwitchFsmContext fsmContext = new SwitchFsmContext(outputAdapter);
        SwitchFsmEvent event = null;

        switch (payload.getState()) {
            case ACTIVATED:
                event = SwitchFsmEvent.ONLINE;
                fsmContext.setInitState(payload.getSwitchView());
                break;
            case DEACTIVATED:
                event = SwitchFsmEvent.OFFLINE;
                break;

            default:
                log.info("Ignore switch event {} (no need to handle it)", payload.getSwitchId());
                break;
        }

        if (event != null) {
            SwitchFsm fsm = getSwitchFsmCreateIfAbsent(payload.getSwitchId());
            switchControllerExecutor.fire(fsm, event, fsmContext);
        }
    }

    public void portEvent(PortInfoData payload, ISwitchReply outputAdapter) {
        SwitchFsm switchFsm = switchController.get(payload.getSwitchId());
        if (switchFsm == null) {
            throw new IllegalStateException(String.format("Switch not found for port event %s_%d %s",
                                                          payload.getSwitchId(), payload.getPortNo(),
                                                          payload.getState()));
        }

        SwitchFsmContext fsmContext = new SwitchFsmContext(outputAdapter);
        fsmContext.setPortNumber(payload.getPortNo());
        SwitchFsmEvent event = null;
        switch (payload.getState()) {
            case ADD:
                event = SwitchFsmEvent.PORT_ADD;
                break;
            case DELETE:
                event = SwitchFsmEvent.PORT_DEL;
                break;
            case UP:
                event = SwitchFsmEvent.PORT_UP;
                break;
            case DOWN:
                event = SwitchFsmEvent.PORT_DOWN;
                break;

            case OTHER_UPDATE:
            case CACHED:
                log.error("Invalid port event {}_{} - incomplete or deprecated",
                          payload.getSwitchId(), payload.getPortNo());
                break;

            default:
                log.info("Ignore port event {}_{} (no need to handle it)", payload.getSwitchId(), payload.getPortNo());
        }

        if (event != null) {
            switchControllerExecutor.fire(switchFsm, event, fsmContext);
        }
    }

    private void detectOfflineSwitches(Set<SwitchId> knownSwitches, ISwitchReply outputAdapter) {
        Set<SwitchId> extraSwitches = new HashSet<>(switchController.keySet());
        extraSwitches.removeAll(knownSwitches);

        SwitchFsmContext fsmContext = new SwitchFsmContext(outputAdapter);
        fsmContext.setOnline(false);
        for (SwitchId entryId : extraSwitches) {
            SwitchFsm switchFsm = switchController.remove(entryId);
            switchControllerExecutor.fire(switchFsm, SwitchFsmEvent.MANAGED, fsmContext);
        }
    }

    private void setAllSwitchesUnmanaged(ISwitchReply outputAdapter) {
        SwitchFsmContext fsmContext = new SwitchFsmContext(outputAdapter);
        for (SwitchFsm switchFsm : switchController.values()) {
            switchControllerExecutor.fire(switchFsm, SwitchFsmEvent.UNMANAGED, fsmContext);
        }
    }

    private SwitchFsm getSwitchFsmCreateIfAbsent(SwitchId datapath) {
        return switchController.computeIfAbsent(datapath, key -> SwitchFsm.create());
    }
}
