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

import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.error.NoFeatureException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.error.SwitchWriteException;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.messaging.model.Switch;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

abstract class BfdCommand extends Command {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final DatapathId target;

    protected final ISwitchManager switchManager;
    private final SessionService sessionService;
    protected final FeatureDetectorService featureDetector;

    public BfdCommand(CommandContext context, DatapathId target) {
        super(context);

        this.target = target;

        FloodlightModuleContext moduleContext = context.getModuleContext();
        switchManager = moduleContext.getServiceImpl(ISwitchManager.class);
        sessionService = moduleContext.getServiceImpl(SessionService.class);
        featureDetector = moduleContext.getServiceImpl(FeatureDetectorService.class);
    }

    @Override
    public Command call() throws Exception {
        try {
            IOFSwitch sw = switchManager.lookupSwitch(target);

            validate(sw);
            try (Session session = sessionService.open(sw)) {
                handle(session);
            }
        } catch (SwitchOperationException e) {
            logError(e);
        }

        return null;
    }

    protected void validate(IOFSwitch sw) throws NoFeatureException {
        checkSwitchCapabilities(sw);
    }

    protected abstract void handle(Session session) throws SwitchWriteException;

    protected void handleError(Throwable error) {
        try {
            errorDispatcher(error);
        } catch (Throwable e) {
            logError(e);
        }
    }

    protected void errorDispatcher(Throwable error) throws Throwable {
        throw error;
    }

    protected void scheduleErrorHandling(CompletableFuture<?> future) {
        future.whenComplete((result, error) -> {
            if (error == null) {
                return;
            }

            handleError(error);
        });
    }

    protected void checkSwitchCapabilities(IOFSwitch sw) throws NoFeatureException {
        Set<Switch.Feature> features = featureDetector.detectSwitch(sw);

        final Switch.Feature requiredFeature = Switch.Feature.BFD;
        if (!features.contains(requiredFeature)) {
            throw new NoFeatureException(sw.getId(), requiredFeature, features);
        }
    }

    private void logError(Throwable e) {
        log.error("Unable to perform BFD command {}: {}", getClass().getCanonicalName(), e.getMessage());
    }
}
