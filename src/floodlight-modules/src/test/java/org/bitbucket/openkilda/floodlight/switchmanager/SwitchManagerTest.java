package org.bitbucket.openkilda.floodlight.switchmanager;

import com.google.common.util.concurrent.Futures;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.SwitchDescription;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.restserver.IRestApiService;
import org.bitbucket.openkilda.floodlight.message.command.encapsulation.OutputCommands;
import org.bitbucket.openkilda.floodlight.message.command.encapsulation.PushSchemeOutputCommands;
import org.bitbucket.openkilda.messaging.payload.response.OutputVlanType;
import org.easymock.*;
import org.junit.Before;
import org.junit.Test;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.types.*;

import static org.bitbucket.openkilda.floodlight.Constants.*;
import static org.bitbucket.openkilda.floodlight.message.command.encapsulation.PushSchemeOutputCommands.*;
import static org.easymock.EasyMock.*;
import static org.junit.Assert.*;

/**
 * Created by atopilin on 10/04/2017.
 */
public class SwitchManagerTest {
    private static final OutputCommands scheme = new PushSchemeOutputCommands();
    private static final FloodlightModuleContext context = new FloodlightModuleContext();
    private static final long cookie = 123L;
    private static final String cookieHex = "7B";
    private SwitchManager switchManager;
    private IOFSwitchService ofSwitchService;
    private IRestApiService restApiService;
    private IOFSwitch iofSwitch;
    private DatapathId dpid;

    @Before
    public void setUp() throws FloodlightModuleException {
        ofSwitchService = createMock(IOFSwitchService.class);
        restApiService = createMock(IRestApiService.class);
        iofSwitch = createMock(IOFSwitch.class);
        dpid = createMock(DatapathId.class);

        context.addService(IRestApiService.class, restApiService);
        context.addService(IOFSwitchService.class, ofSwitchService);

        switchManager = new SwitchManager();
        switchManager.init(context);
    }

    @Test
    public void installDefaultRules() throws Exception {
        // TODO
    }

    @Test
    public void installIngressFlowReplaceAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installIngressFlow(dpid, cookieHex, inputPort,
                outputPort, inputVlanId, transitVlanId, OutputVlanType.REPLACE, meterId);

        assertEquals(
                scheme.ingressReplaceFlowMod(inputPort, outputPort, inputVlanId, transitVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installIngressFlowPopAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installIngressFlow(dpid, cookieHex, inputPort,
                outputPort, inputVlanId, transitVlanId, OutputVlanType.POP, meterId);

        assertEquals(
                scheme.ingressPopFlowMod(inputPort, outputPort, inputVlanId, transitVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installIngressFlowPushAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installIngressFlow(dpid, cookieHex, inputPort,
                outputPort, 0, transitVlanId, OutputVlanType.PUSH, meterId);

        assertEquals(
                scheme.ingressPushFlowMod(inputPort, outputPort, transitVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installIngressFlowNoneAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installIngressFlow(dpid, cookieHex, inputPort,
                outputPort, 0, transitVlanId, OutputVlanType.NONE, meterId);

        assertEquals(
                scheme.ingressNoneFlowMod(inputPort, outputPort, transitVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installEgressFlowNoneAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressFlow(dpid, cookieHex, inputPort, outputPort, transitVlanId, 0, OutputVlanType.NONE);

        assertEquals(
                scheme.egressNoneFlowMod(inputPort, outputPort, transitVlanId, cookie),
                capture.getValue());
    }

    @Test
    public void installEgressFlowPushAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressFlow(dpid, cookieHex, inputPort, outputPort, transitVlanId, outputVlanId, OutputVlanType.PUSH);

        assertEquals(
                scheme.egressPushFlowMod(inputPort, outputPort, transitVlanId, outputVlanId, cookie),
                capture.getValue());
    }

    @Test
    public void installEgressFlowPopAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressFlow(dpid, cookieHex, inputPort, outputPort, transitVlanId, 0, OutputVlanType.POP);

        assertEquals(
                scheme.egressPopFlowMod(inputPort, outputPort, transitVlanId, cookie),
                capture.getValue());
    }

    @Test
    public void installEgressFlowReplaceAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installEgressFlow(dpid, cookieHex, inputPort, outputPort, transitVlanId, outputVlanId, OutputVlanType.REPLACE);

        assertEquals(
                scheme.egressReplaceFlowMod(inputPort, outputPort, transitVlanId, outputVlanId, cookie),
                capture.getValue());
    }

    @Test
    public void installTransitFlow() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installTransitFlow(dpid, cookieHex, inputPort, outputPort, transitVlanId);

        assertEquals(
                scheme.transitFlowMod(inputPort, outputPort, transitVlanId, cookie),
                capture.getValue());
    }

    @Test
    public void installOneSwitchFlowReplaceAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installOneSwitchFlow(dpid, cookieHex, inputPort,
                outputPort, inputVlanId, outputVlanId, OutputVlanType.REPLACE, meterId);

        assertEquals(
                scheme.oneSwitchReplaceFlowMod(inputPort, outputPort, inputVlanId, outputVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installOneSwitchFlowPushAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installOneSwitchFlow(dpid, cookieHex, inputPort, outputPort, 0, outputVlanId, OutputVlanType.PUSH, meterId);

        assertEquals(
                scheme.oneSwitchPushFlowMod(inputPort, outputPort, outputVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installOneSwitchFlowPopAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installOneSwitchFlow(dpid, cookieHex, inputPort, outputPort, inputVlanId, 0, OutputVlanType.POP, meterId);

        assertEquals(
                scheme.oneSwitchPopFlowMod(inputPort, outputPort, inputVlanId, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void installOneSwitchFlowNoneAction() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();

        switchManager.installOneSwitchFlow(dpid, cookieHex, inputPort, outputPort, 0, 0, OutputVlanType.NONE, meterId);

        assertEquals(
                scheme.oneSwitchNoneFlowMod(inputPort, outputPort, meterId, cookie),
                capture.getValue());
    }

    @Test
    public void dumpFlowTable() throws Exception {
        // TODO
    }

    @Test
    public void dumpMeters() throws Exception {
        // TODO
    }

    @Test
    public void installBandwidthMeter() throws Exception {
        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);

        expect(iofSwitch.write(scheme.installMeter(bandwidth, burstSize, meterId))).andReturn(true);

        replay(ofSwitchService);
        replay(iofSwitch);

        switchManager.installMeter(dpid, bandwidth, burstSize, meterId);
    }

    @Test
    public void deleteFlow() throws Exception {
        Capture<OFFlowMod> capture = prepareForInstallTest();
        switchManager.deleteFlow(dpid, cookieHex);
        final OFFlowMod actual = capture.getValue();
        assertEquals(OFFlowModCommand.DELETE, actual.getCommand());
        assertEquals(Long.valueOf(cookieHex, 16).longValue(), actual.getCookie().getValue());
        assertEquals(SwitchManager.NON_SYSTEM_MASK, actual.getCookieMask());
    }

    @Test
    public void deleteMeter() {
        final Capture<OFMeterMod> capture = prepareForMeterTest();
        switchManager.deleteMeter(dpid, meterId);
        final OFMeterMod meterMod = capture.getValue();
        assertEquals(meterMod.getCommand(), OFMeterModCommand.DELETE);
        assertEquals(meterMod.getMeterId(), meterId);
    }

    @Test
    public void testPortStats() {
        Capture<OFPortStatsRequest> capture = EasyMock.newCapture();

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.writeStatsRequest(capture(capture))).andReturn(Futures.immediateFuture(null));
        EasyMock.expectLastCall();

        replay(ofSwitchService);
        replay(iofSwitch);

        switchManager.requestPortStats(dpid);
        OFPortStatsRequest request = capture.getValue();
        assertEquals(request.getPortNo(), OFPort.ANY);
    }

    @Test
    public void testFlowStats() {
        Capture<OFFlowStatsRequest> capture = EasyMock.newCapture();

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.writeStatsRequest(capture(capture))).andReturn(Futures.immediateFuture(null));
        EasyMock.expectLastCall();

        replay(ofSwitchService);
        replay(iofSwitch);

        switchManager.requestFlowStats(dpid);
        OFFlowStatsRequest request = capture.getValue();
        assertEquals(request.getCookie(), U64.ZERO);
        assertEquals(request.getCookieMask(), SwitchManager.SYSTEM_MASK);
    }

    @Test
    public void testMeterConfigStats() {
        Capture<OFMeterConfigStatsRequest> capture = EasyMock.newCapture();

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.writeStatsRequest(capture(capture))).andReturn(Futures.immediateFuture(null));
        EasyMock.expectLastCall();

        replay(ofSwitchService);
        replay(iofSwitch);

        switchManager.requestMeterConfigStats(dpid);
        OFMeterConfigStatsRequest request = capture.getValue();
        assertEquals(request.getMeterId(), SwitchManager.OFPM_ALL);
    }

    private Capture<OFFlowMod> prepareForInstallTest() {
        Capture<OFFlowMod> capture = EasyMock.newCapture();

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.getSwitchDescription()).andStubReturn(new SwitchDescription());
        expect(iofSwitch.write(capture(capture))).andReturn(true);
        EasyMock.expectLastCall();

        replay(ofSwitchService);
        replay(iofSwitch);

        return capture;
    }

    private Capture<OFMeterMod> prepareForMeterTest() {
        Capture<OFMeterMod> capture = EasyMock.newCapture();

        expect(ofSwitchService.getSwitch(dpid)).andStubReturn(iofSwitch);
        expect(iofSwitch.getOFFactory()).andStubReturn(ofFactory);
        expect(iofSwitch.write(capture(capture))).andReturn(true);
        EasyMock.expectLastCall();

        replay(ofSwitchService);
        replay(iofSwitch);

        return capture;
    }
}
