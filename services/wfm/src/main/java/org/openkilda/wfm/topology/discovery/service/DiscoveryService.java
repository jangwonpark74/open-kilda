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

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PortInfoData;
import org.openkilda.messaging.info.event.SwitchInfoData;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.Isl;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.IslRepository;
import org.openkilda.persistence.repositories.RepositoryFactory;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.controller.PortFsm;
import org.openkilda.wfm.topology.discovery.controller.PortFsmContext;
import org.openkilda.wfm.topology.discovery.controller.PortFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.PortFsmState;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsm;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsmContext;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsmEvent;
import org.openkilda.wfm.topology.discovery.controller.SwitchFsmState;
import org.openkilda.wfm.topology.discovery.model.Endpoint;
import org.openkilda.wfm.topology.discovery.model.IslFacts;
import org.openkilda.wfm.topology.discovery.model.IslReference;
import org.openkilda.wfm.topology.discovery.model.OperationMode;
import org.openkilda.wfm.topology.discovery.model.PortFacts;
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
    private final Map<Endpoint, PortFsm> portController = new HashMap<>();

    private final FsmExecutor<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> switchControllerExecutor
            = SwitchFsm.makeExecutor();
    private final FsmExecutor<PortFsm, PortFsmState, PortFsmEvent, PortFsmContext> portControllerExecutor
            = PortFsm.makeExecutor();

    public DiscoveryService(PersistenceManager persistenceManager) {
        this.persistenceManager = persistenceManager;
    }

    // -- {@link SwitchPreloader} responsibility --

    public void prepopulate(ISwitchPrepopulateReply reply) {
        for (SwitchInit persistent : loadPersistent()) {
            reply.prepopulateSwitch(persistent);
        }
    }

    // -- SwitchHandler --

    public void switchPrepopulate(SwitchInit init, ISwitchReply outputAdapter) {
        SwitchFsm switchFsm = SwitchFsm.create(init.getSwitchId());

        SwitchFsmContext fsmContext = new SwitchFsmContext(outputAdapter);
        fsmContext.setPrecreateState(init);

        switchControllerExecutor.fire(switchFsm, SwitchFsmEvent.HISTORY, fsmContext);

        switchController.put(init.getSwitchId(), switchFsm);
    }

    public void switchRestoreManagement(SpeakerSwitchView switchView, ISwitchReply outputAdapter) {
        SwitchFsmContext fsmContext = new SwitchFsmContext(outputAdapter);
        fsmContext.setOnline(true);
        fsmContext.setInitState(switchView);

        SwitchFsm fsm = locateSwitchFsmCreateIfAbsent(switchView.getDatapath());
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
            SwitchFsm fsm = locateSwitchFsmCreateIfAbsent(payload.getSwitchId());
            switchControllerExecutor.fire(fsm, event, fsmContext);
        }
    }

    public void switchIslDiscovery(IslInfoData payload, ISwitchReply outputAdapter) {
        IslFacts islFacts = new IslFacts(payload);
        SwitchFsmContext fsmContext = new SwitchFsmContext(outputAdapter);
        fsmContext.setIslFacts(islFacts);

        IslReference reference = islFacts.getReference();
        SwitchFsm switchFsm = locateSwitchFsm(reference.getDest().getDatapath());
        switchControllerExecutor.fire(switchFsm, SwitchFsmEvent.ISL_DISCOVERY, fsmContext);
    }

    public void switchPortEvent(PortInfoData payload, ISwitchReply outputAdapter) {
        SwitchFsm switchFsm = locateSwitchFsm(payload.getSwitchId());
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

    // -- PortHandler --

    public void portSetup(PortFacts portFacts, IPortReply outputAdapter) {
        Endpoint endpoint = portFacts.getEndpoint();
        PortFsm portFsm = PortFsm.create(endpoint);

        Endpoint remote = portFacts.getRemote();
        if (remote != null) {
            PortFsmContext fsmContext = new PortFsmContext(outputAdapter);
            fsmContext.setRemote(remote);
            portControllerExecutor.fire(portFsm, PortFsmEvent.HISTORY, fsmContext);
        }

        portController.put(endpoint, portFsm);
    }

    // -- private --

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
                Endpoint remote = new Endpoint(islEntry.getDestSwitch().getSwitchId(), islEntry.getDestPort());
                swInit.addPort(new PortFacts(new Endpoint(swInit.getSwitchId(), islEntry.getSrcPort()), remote));
            } catch (IllegalArgumentException e) {
                log.error("Corrupter ISL relation endpoint(dest) - {}-{}",
                          islEntry.getDestSwitch().getSwitchId(), islEntry.getDestPort());
            }
        }

        return switchById.values();
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

    private SwitchFsm locateSwitchFsm(SwitchId datapath) {
        SwitchFsm switchFsm = switchController.get(datapath);
        if (switchFsm == null) {
            throw new IllegalStateException(String.format("Switch FSM not found (%s).", datapath));
        }
        return switchFsm;
    }

    private SwitchFsm locateSwitchFsmCreateIfAbsent(SwitchId datapath) {
        return switchController.computeIfAbsent(datapath, key -> SwitchFsm.create(datapath));
    }
}
