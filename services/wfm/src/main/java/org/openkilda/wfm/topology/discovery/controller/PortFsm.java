/*
 * Copyright 2018 Telstra Open Source
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

import org.openkilda.wfm.share.utils.FsmExecutor;
import org.openkilda.wfm.topology.discovery.model.Endpoint;

import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

public class PortFsm extends AbstractStateMachine<PortFsm, PortFsmState, PortFsmEvent, PortFsmContext> {
    private final Endpoint endpoint;
    private Endpoint remote;

    private boolean online = true;
    private boolean enabled = true;
    private boolean managed = true;

    private static final StateMachineBuilder<PortFsm, PortFsmState, PortFsmEvent, PortFsmContext> builder;

    static {
        builder = StateMachineBuilderFactory.create(
                PortFsm.class, PortFsmState.class, PortFsmEvent.class, PortFsmContext.class,
                // extra parameters
                Endpoint.class);

        builder.transition().from(PortFsmState.INIT).to(PortFsmState.PREPOPULATE).on(PortFsmEvent.HISTORY);
        builder.transition().from(PortFsmState.PREPOPULATE).to(PortFsmState.INIT).on(PortFsmEvent.NEXT);

        builder.onEntry(PortFsmState.PREPOPULATE)
                .callMethod("prepopulateEnter");
    }

    public static FsmExecutor<PortFsm, PortFsmState, PortFsmEvent, PortFsmContext> makeExecutor() {
        return new FsmExecutor<>(PortFsmEvent.NEXT);
    }

    public static PortFsm create(Endpoint endpoint) {
        return builder.newStateMachine(PortFsmState.INIT, endpoint);
    }

    public PortFsm(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    private void prepopulateEnter(PortFsmState from, PortFsmState to, PortFsmState event, PortFsmContext context) {
        remote = context.getRemote();
    }
}
