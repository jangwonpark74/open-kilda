package org.openkilda.wfm.topology.flow.service;

import org.openkilda.model.*;
import org.openkilda.persistence.TransactionManager;
import org.openkilda.persistence.repositories.*;

import java.util.List;

public class FlowService {
    private TransactionManager transactionManager;
    private FlowRepository flowRepository;
    private FlowSegmentRepository flowSegmentRepository;
    private SwitchRepository switchRepository;
    private IslRepository islRepository;

    public FlowService(TransactionManager transactionManager, RepositoryFactory repositoryFactory) {
        this.transactionManager = transactionManager;
        flowRepository = repositoryFactory.getFlowRepository();
        flowSegmentRepository = repositoryFactory.getFlowSegmentRepository();
        switchRepository = repositoryFactory.getSwitchRepository();
        islRepository = repositoryFactory.getIslRepository();
    }


    public void createFlow(FlowPair flowPair) {
        transactionManager.begin();
        try {
            createFlowForPair(flowPair);
            transactionManager.commit();
        } catch (Exception e) {
          transactionManager.rollback();
        }
    }

    private void createFlowForPair(FlowPair flowPair) {
        Flow flow = flowPair.getForward();
        processCreateFlow(flow);
        flow = flowPair.getReverse();
        processCreateFlow(flow);
    }

    private void processCreateFlow(Flow flow) {
        flowSegmentRepository.deleteFlowSegments(flow);
        Switch srcSwitch = switchRepository.findBySwitchId(flow.getSrcSwitchId());
        Switch dstSwitch = switchRepository.findBySwitchId(flow.getDestSwitchId());
        flow.setSrcSwitch(srcSwitch);
        flow.setDestSwitch(dstSwitch);
        flow.setLastUpdated(Long.valueOf(System.currentTimeMillis()/1000).toString());
        flowRepository.createOrUpdate(flow);
        flowSegmentRepository.mergeFlowSegments(flow);
        islRepository.updateIslBandwidth(flow);
    }

    public void deleteFlow(String flowId) {
        transactionManager.begin();
        try {
            Iterable<Flow> flows = flowRepository.findById(flowId);
            for (Flow flow : flows) {
                processDeleteFlow(flow);
            }
            transactionManager.commit();

        } catch (Exception e) {
            transactionManager.rollback();
        }
    }
    private void deleteFlowForPair(FlowPair flowPair) {
        Flow flow = flowPair.getForward();
        processDeleteFlow(flow);
        flow = flowPair.getReverse();
        processDeleteFlow(flow);
    }

    private void processDeleteFlow(Flow flow) {
        flowSegmentRepository.deleteFlowSegments(flow);
        flowRepository.delete(flow);
    }

    public void updateFlow(FlowPair flowPair) {
        transactionManager.begin();
        try {
            deleteFlowForPair(flowPair);
            createFlowForPair(flowPair);
        } catch (Exception e) {
            transactionManager.rollback();
        }
    }
}
