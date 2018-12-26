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

import org.openkilda.model.SwitchId;
import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.model.PortInit;
import org.openkilda.wfm.topology.discovery.model.SwitchInit;
import org.openkilda.wfm.topology.discovery.service.ISwitchReply;

import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

public class SwitchFsm extends AbstractStateMachine<SwitchFsm, SwitchFsmState, SwitchFsmEvent, SwitchFsmContext> {
    private final SwitchId switchId;

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

        builder.transition().from(SwitchFsmState.PORT_ADD).to(SwitchFsmEvent.ONLINE_PENDING).on(SwitchFsmEvent.NEXT);

        builder.transition().from(SwitchFsmState.PORT_DEL).to(SwitchFsmEvent.ONLINE_PENDING).on(SwitchFsmEvent.NEXT);

        builder.defineSequentialStatesOn(SwitchFsmState.ONLINE,
                                         SwitchFsmState.ONLINE_PENDING, SwitchFsmState.PORT_PROXY,
                                         SwitchFsmState.PORT_ADD, SwitchFsmState.PORT_DEL);

        builder.transition()
                .from(SwitchFsmState.UNMANAGED).to(SwitchFsmState.MANAGED_DECISION).on(SwitchFsmEvent.MANAGED);
        // MANAGED_DECISION will route to SETUP or OFFLINE

        builder.transition().from(SwitchFsmState.OFFLINE).to(SwitchFsmState.SETUP).on(SwitchFsmEvent.ONLINE);

        builder.onEntry(SwitchFsmState.PREPOPULATE)
                .callMethod("prepopulateEnter");

        builder.onEntry(SwitchFsmState.SETUP)
                .callMethod("setupEnter");

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

        ISwitchReply output = context.getOutput();
        for (PortInit portInit : switchInit.getPorts()) {
            output.setupPortHandler(switchId, portInit);
        }
    }

    private void setupEnter(SwitchFsmState from, SwitchFsmState to, SwitchFsmEvent event, SwitchFsmContext context) {
        // TODO
    }
}
