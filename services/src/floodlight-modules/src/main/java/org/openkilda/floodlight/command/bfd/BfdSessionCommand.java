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
import org.openkilda.messaging.model.NoviBfdSession;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.action.OFActionNoviflowBfdStart;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;

import java.net.InetAddress;

abstract class BfdSessionCommand extends BfdCommand {
    private final NoviBfdSession bfdSession;

    public BfdSessionCommand(CommandContext context, NoviBfdSession bfdSession) {
        super(context, DatapathId.of(bfdSession.getTarget().getDatapath().toLong()));
        this.bfdSession = bfdSession;
    }

    protected OFPacketOut makeSessionConfigMessage(IOFSwitch sw) {
        OFFactory ofFactory = sw.getOFFactory();

        OFActionNoviflowBfdStart bfdStartAction = ofFactory.actions().buildNoviflowBfdStart()
                .setPortNo(bfdSession.getLogicalPortNumber())
                .setMyDisc(bfdSession.getDiscriminator())
                .setInterval(bfdSession.getIntervalMs() * 1000)
                .setMultiplier(bfdSession.getMultiplier())
                .setKeepAliveTimeout(((short) (bfdSession.isKeepOverDisconnect() ? 1 : 0)))
                .build();

        return ofFactory.buildPacketOut()
                .setInPort(OFPort.CONTROLLER)
                .setData(makeSessionConfigPayload(sw).serialize())
                .setActions(ImmutableList.of(bfdStartAction))
                .build();
    }

    private IPacket makeSessionConfigPayload(IOFSwitch sw) {
        final TransportPort udpPort = TransportPort.of(bfdSession.getUdpPortNumber());
        UDP l4 = new UDP()
                .setSourcePort(udpPort)
                .setDestinationPort(udpPort);

        InetAddress sourceIpAddress = switchManager.getSwitchIpAddress(sw);
        InetAddress destIpAddress = bfdSession.getTarget().getIpAddress();
        IPacket l3 = new IPv4()
                .setSourceAddress(IPv4Address.of(sourceIpAddress.getAddress()))
                .setDestinationAddress(IPv4Address.of(destIpAddress.getAddress()))
                .setProtocol(IpProtocol.UDP)
                .setPayload(l4);

        DatapathId remoteDatapath = DatapathId.of(bfdSession.getRemote().getDatapath().toLong());
        return new Ethernet()
                .setEtherType(EthType.IPv4)
                .setSourceMACAddress(switchManager.dpIdToMac(sw.getId()))
                .setDestinationMACAddress(switchManager.dpIdToMac(remoteDatapath))
                .setPayload(l3);
    }

    // getter & setters
    protected NoviBfdSession getBfdSession() {
        return bfdSession;
    }
}
