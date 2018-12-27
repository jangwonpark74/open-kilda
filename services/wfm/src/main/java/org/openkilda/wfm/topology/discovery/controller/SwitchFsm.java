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

package org.openkilda.wfm.topology.discovery.controller;

import org.openkilda.messaging.model.SpeakerSwitchPortView;
import org.openkilda.messaging.model.SpeakerSwitchView;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.model.PortFacts;
import org.openkilda.wfm.topology.discovery.model.PortFacts.LinkStatus;
import org.openkilda.wfm.topology.discovery.model.SwitchInit;
import org.openkilda.wfm.topology.discovery.service.ISwitchReply;

import lombok.extern.slf4j.Slf4j;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class SwitchFsm extends AbstractStateMachine<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> {
    private final SwitchId switchId;

    private final Map<Integer, PortFacts> portByNumber = new HashMap<>();

    public static final StateMachineBuilder<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                SwitchFsm.class, SwitchFsmState.class, SwitchFsmEvent.class, SwitchFsmContext.class,
                // extra parameters
                SwitchId.class);

        builder.transition().from(SwitchFsmState.INIT).to(SwitchFsmState.PREPOPULATE).on(SwitchFsmEvent.HISTORY);
        builder.transition().from(SwitchFsmState.INIT).to(SwitchFsmState.OFFLINE).on(SwitchFsmEvent.OFFLINE);

        builder.transition().from(SwitchFsmState.PREPOPULATE).to(SwitchFsmState.INIT).on(SwitchFsmEvent.NEXT);

        builder.transition().from(SwitchFsmState.INIT).to(SwitchFsmState.SETUP).on(SwitchFsmEvent.ONLINE);
        builder.transition().from(SwitchFsmState.INIT).to(SwitchFsmState.SETUP).on(SwitchFsmEvent.MANAGED);

        builder.transition().from(SwitchFsmState.SETUP).to(SwitchFsmState.ONLINE).on(SwitchFsmEvent.NEXT);

        builder.transition().from(SwitchFsmState.ONLINE).to(SwitchFsmState.UNMANAGED).on(SwitchFsmEvent.UNMANAGED);
        builder.transition().from(SwitchFsmState.ONLINE).to(SwitchFsmState.OFFLINE).on(SwitchFsmEvent.OFFLINE);

        builder.transition().from(SwitchFsmState.ONLINE_PENDING).to(SwitchFsmState.PORT_PROXY)
                .on(SwitchFsmEvent.PORT_UP);
        builder.transition().from(SwitchFsmState.ONLINE_PENDING).to(SwitchFsmState.PORT_PROXY)
                .on(SwitchFsmEvent.PORT_DOWN);
        builder.transition().from(SwitchFsmState.ONLINE_PENDING).to(SwitchFsmState.PORT_ADD)
                .on(SwitchFsmEvent.PORT_ADD);
        builder.transition().from(SwitchFsmState.ONLINE_PENDING).to(SwitchFsmState.PORT_DEL)
                .on(SwitchFsmEvent.PORT_DEL);

        builder.transition().from(SwitchFsmState.PORT_PROXY).to(SwitchFsmState.ONLINE_PENDING).on(SwitchFsmEvent.NEXT);

        builder.transition().from(SwitchFsmState.PORT_ADD).to(SwitchFsmState.ONLINE_PENDING).on(SwitchFsmEvent.NEXT);

        builder.transition().from(SwitchFsmState.PORT_DEL).to(SwitchFsmState.ONLINE_PENDING).on(SwitchFsmEvent.NEXT);

        builder.defineSequentialStatesOn(SwitchFsmState.ONLINE,
                                         SwitchFsmState.ONLINE_PENDING, SwitchFsmState.PORT_PROXY,
                                         SwitchFsmState.PORT_ADD, SwitchFsmState.PORT_DEL);

        builder.transition()
                .from(SwitchFsmState.UNMANAGED).to(SwitchFsmState.MANAGED_DECISION).on(SwitchFsmEvent.MANAGED);

        builder.transition().from(SwitchFsmState.MANAGED_DECISION).to(SwitchFsmState.SETUP).on(SwitchFsmEvent.ONLINE);
        builder.transition().from(SwitchFsmState.MANAGED_DECISION).to(SwitchFsmState.OFFLINE).on(SwitchFsmEvent.OFFLINE);

        builder.transition().from(SwitchFsmState.OFFLINE).to(SwitchFsmState.SETUP).on(SwitchFsmEvent.ONLINE);

        builder.onEntry(SwitchFsmState.PREPOPULATE)
                .callMethod("prepopulateEnter");

        builder.onEntry(SwitchFsmState.SETUP)
                .callMethod("setupEnter");

        builder.onEntry(SwitchFsmState.PORT_ADD)
                .callMethod("portAddEnter");

        builder.onEntry(SwitchFsmState.PORT_DEL)
                .callMethod("portDelEnter");

        builder.onEntry(SwitchFsmState.PORT_PROXY)
                .callMethod("portProxyEnter");

        builder.onEntry(SwitchFsmState.UNMANAGED)
                .callMethod("unmanagedEnter");
        builder.onExit(SwitchFsmState.UNMANAGED)
                .callMethod("unmanagedExit");

        builder.onEntry(SwitchFsmState.MANAGED_DECISION)
                .callMethod("managementDecision");

        builder.onEntry(SwitchFsmState.OFFLINE)
                .callMethod("offlineEnter");
    }

    public static FsmExecutor<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> makeExecutor() {
        return new FsmExecutor<>(SwitchFsmEvent.NEXT);
    }

    public static SwitchFsm create(SwitchId switchId) {
        return builder.newStateMachine(SwitchFsmState.INIT, switchId);
    }

    public SwitchFsm(SwitchId switchId) {
        this.switchId = switchId;
    }

    private void prepopulateEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                                  SwitchFsmContext context) {
        SwitchInit switchInit = context.getPrecreateState();
        for (PortFacts port : switchInit.getPorts()) {
            portAdd(context, port);
        }
    }

    private void setupEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        SpeakerSwitchView initState = context.getInitState();

        Set<Integer> removedPorts = new HashSet<>(portByNumber.keySet());
        List<PortFacts> becomeUpPorts = new ArrayList<>();
        List<PortFacts> becomeDownPorts = new ArrayList<>();
        for (SpeakerSwitchPortView port : initState.getPorts()) {
            removedPorts.remove(port.getNumber());

            PortFacts actualPort = new PortFacts(port);
            PortFacts storedPort = portByNumber.get(port.getNumber());
            if (storedPort == null) {
                // port added
                portAdd(context, new PortFacts(port));
                continue;
            }

            if (storedPort.getLinkStatus() == actualPort.getLinkStatus()) {
                // ports state have not been changed
                continue;
            }

            switch (actualPort.getLinkStatus()) {
                case UP:
                    becomeUpPorts.add(actualPort);
                    break;
                case DOWN:
                    becomeDownPorts.add(actualPort);
                    break;
                default:
                    throw new IllegalArgumentException(String.format(
                            "Unsupported port admin state value %s (%s)",
                            actualPort.getLinkStatus(), actualPort.getLinkStatus().getClass().getName()));
            }
        }

        for (Integer portNumber : removedPorts) {
            PortFacts port = portByNumber.get(portNumber);
            portDel(context, port);
        }

        // emit "online = true" for all ports
        ISwitchReply output = context.getOutput();
        for (PortFacts port : portByNumber.values()) {
            output.sethOnlineStatus(switchId, port, true);
        }

        for (PortFacts port : becomeDownPorts) {
            port.setLinkStatus(LinkStatus.DOWN);
            output.syncPortLinkStatus(switchId, port);
        }

        for (PortFacts port : becomeUpPorts) {
            port.setLinkStatus(LinkStatus.UP);
            output.syncPortLinkStatus(switchId, port);
        }
    }


    private void portAddEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        PortFacts port = new PortFacts(context.getPortNumber());
        portAdd(context, port);
    }

    private void portDelEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        PortFacts port = new PortFacts(context.getPortNumber());
        portDel(context, port);
    }

    private void portProxyEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                                SwitchFsmContext context) {
        PortFacts port = portByNumber.get(context.getPortNumber());
        if (port == null) {
            log.error("Port {} is not listed into {}", context.getPortNumber(), switchId);
            return;
        }

        switch (event) {
            case PORT_UP:
                port.setLinkStatus(LinkStatus.UP);
                break;
            case PORT_DOWN:
                port.setLinkStatus(LinkStatus.DOWN);
                break;
            default:
                throw new IllegalArgumentException(String.format("Unexpected event %s received in state %s (%s)",
                                                                 event, to, getClass().getName()));
        }


        context.getOutput().syncPortLinkStatus(switchId, port);
    }

    private void unmanagedEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                                SwitchFsmContext context) {
        allPortsManagementStatusUpdate(context, false);
    }

    private void unmanagedExit(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        allPortsManagementStatusUpdate(context, true);
    }

    private void managedDecision(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event,
                                 SwitchFsmContext context) {
        SwitchFsmEvent routeEvent;
        if (context.isOnline()) {
            routeEvent = SwitchFsmEvent.ONLINE;
        } else {
            routeEvent = SwitchFsmEvent.OFFLINE;
        }
        fire(routeEvent, context);
    }

    private void offlineEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        ISwitchReply output = context.getOutput();
        for (PortFacts port : portByNumber.values()) {
            output.sethOnlineStatus(switchId, port, false);
        }
    }

    private void portAdd(SwitchFsmContext context, PortFacts portFacts) {
        context.getOutput().setupPortHandler(switchId, portFacts);
        portByNumber.put(portFacts.getPortNumber(), portFacts);
    }

    private void portDel(SwitchFsmContext context, PortFacts portFacts) {
        context.getOutput().removePortHandler(switchId, portFacts.getPortNumber());
        portByNumber.remove(portFacts.getPortNumber());
    }

    private void allPortsManagementStatusUpdate(SwitchFsmContext context, boolean status) {
        ISwitchReply output = context.getOutput();
        for (PortFacts port : portByNumber.values()) {
            output.setManagementStatus(switchId, port, status);
        }
    }
}
