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

package org.openkilda.floodlight.command.bfd;

import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.switchmanager.SwitchManager;
import org.openkilda.messaging.model.NoviBfdCatch;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;

abstract class BfdCatchCommand extends BfdCommand {
    // because flow traffic always encapsulated it is safe to put
    // BFD catch rules below flow rules. All client traffic will be routed by flow
    // rules i.e. there is no risk to route client packages into our BFD session.
    protected static final int CATCH_RULES_PRIORITY = SwitchManager.FLOW_RULE_PRIORITY - 1000;

    private final NoviBfdCatch bfdCatch;

    public BfdCatchCommand(CommandContext context, NoviBfdCatch bfdCatch) {
        super(context, DatapathId.of(bfdCatch.getTarget().getDatapath().toLong()));

        this.bfdCatch = bfdCatch;
    }

    protected OFFlowMod.Builder applyCommonFlowModSettings(IOFSwitch sw, OFFlowMod.Builder messageBuilder) {
        return messageBuilder
                .setCookie(makeCookie(bfdCatch.getDiscriminator()))
                .setPriority(CATCH_RULES_PRIORITY)
                .setMatch(makeCatchRuleMatch(sw));
    }

    private Match makeCatchRuleMatch(IOFSwitch sw) {
        OFFactory ofFactory = sw.getOFFactory();

        DatapathId remoteDatapath = DatapathId.of(bfdCatch.getRemote().getDatapath().toLong());
        return ofFactory.buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(bfdCatch.getPhysicalPortNumber()))
                .setExact(MatchField.ETH_SRC, switchManager.dpIdToMac(remoteDatapath))
                .setExact(MatchField.ETH_DST, switchManager.dpIdToMac(sw.getId()))
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.UDP)
                .setExact(MatchField.UDP_DST, TransportPort.of(bfdCatch.getUdpPortNumber()))
                .build();
    }

    private static U64 makeCookie(int discriminator) {
        return U64.of(ISwitchManager.COOKIE_FLAG_SERVICE
                | ISwitchManager.COOKIE_FLAG_BFD_CATCH
                | discriminator);
    }

    // getter & setters
    protected NoviBfdCatch getBfdCatch() {
        return bfdCatch;
    }
}
