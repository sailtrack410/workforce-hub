package com.workforcehub.agent.scheduler;

import com.workforcehub.common.util.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.dto.AgentTaskTimeoutScanResponse;
import com.workforcehub.agent.repository.AgentExpiredTaskRow;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.AgentTaskTimeoutRepository;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.workflow.service.WorkflowAgentResultCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.workforcehub.common.util.JsonValues.write;
import static com.workforcehub.common.util.TextValues.safeSummary;

/**
 * Agent 等待人工确认任务的超时收口服务。
 */
@Service
public class AgentTaskTimeoutJobService {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskTimeoutJobService.class);

    private static final int MAX_BATCH_SIZE = 500;
    private static final String LOCK_NAME = "AGENT_TASK_TIMEOUT_SCAN";
    private static final String TIMEOUT_SUMMARY = "人工确认超时，任务已取消";
    private static final String WF_REQUEST_CONTEXT_TYPE = "WF_REQUEST";
    private static final Set<String> TRIGGER_TYPES = Set.of("SERVICE", "MANUAL", "SCHEDULED");

    private final AgentRepository agentRepository;
    private final AgentTaskTimeoutRepository timeoutRepository;
    private final ObjectProvider<WorkflowAgentResultCallback> workflowAgentResultCallbackProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final int scheduledBatchSize;
    private final boolean scheduledEnabled;

    public AgentTaskTimeoutJobService(AgentRepository agentRepository,
                                      AgentTaskTimeoutRepository timeoutRepository,
                                      ObjectProvider<WorkflowAgentResultCallback> workflowAgentResultCallbackProvider,
                                      ObjectMapper objectMapper,
                                      TransactionTemplate transactionTemplate,
                                      @Value("${workforce.agent.timeout-scan.batch-size:100}") int scheduledBatchSize,
                                      @Value("${workforce.agent.timeout-scan.enabled:true}") boolean scheduledEnabled) {
        this.agentRepository = agentRepository;
        this.timeoutRepository = timeoutRepository;
        this.workflowAgentResultCallbackProvider = workflowAgentResultCallbackProvider;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.scheduledBatchSize = scheduledBatchSize;
        this.scheduledEnabled = scheduledEnabled;
    }

    public long cancelExpiredWaitingTasks(int batchSize) {
        return runTimeoutScan("SERVICE", batchSize).getCancelledCount();
    }

    public AgentTaskTimeoutScanResponse runTimeoutScan(String triggerType, int batchSize) {
        int safeBatchSize = normalizeBatchSize(batchSize);
        String normalizedTriggerType = normalizeTriggerType(triggerType);
        LocalDateTime startedAt = LocalDateTime.now();
        String jobRunId = "AGT-TIMEOUT-" + IdGenerator.nextId();
        String ownerId = "agent-timeout-" + UUID.randomUUID();

        if (!createJobRunAndAcquireLock(jobRunId, ownerId, normalizedTriggerType, safeBatchSize, startedAt)) {
            String message = "已有实例正在执行AI任务超时扫描";
            return buildResponse(jobRunId, normalizedTriggerType, "SKIPPED", safeBatchSize, 0L, 0L, 1L, message, startedAt);
        }

        ScanStats stats = new ScanStats();
        try {
            ScanStats result = transactionTemplate.execute(status ->
                cancelExpiredWaitingTasksInTransaction(safeBatchSize, startedAt)
            );
            stats = result == null ? stats : result;
            finishJobRun(jobRunId, "SUCCESS", stats.scannedCount, stats.cancelledCount, 0L, null);
            return buildResponse(jobRunId, normalizedTriggerType, "SUCCESS", safeBatchSize, stats.scannedCount, stats.cancelledCount, 0L, null, startedAt);
        } catch (RuntimeException e) {
            String errorMessage = safeSummary(e.getMessage(), "Agent超时扫描失败");
            log.warn("Agent人工确认超时扫描批次失败，jobRunId={}", jobRunId, e);
            finishJobRun(jobRunId, "FAILED", stats.scannedCount, stats.cancelledCount, 0L, errorMessage);
            return buildResponse(jobRunId, normalizedTriggerType, "FAILED", safeBatchSize, stats.scannedCount, stats.cancelledCount, 0L, errorMessage, startedAt);
        } finally {
            releaseLockQuietly(ownerId);
        }
    }

    @Scheduled(
        fixedDelayString = "${workforce.agent.timeout-scan.fixed-delay-ms:60000}",
        initialDelayString = "${workforce.agent.timeout-scan.initial-delay-ms:60000}"
    )
    public void scheduledTimeoutScan() {
        if (!scheduledEnabled) {
            return;
        }
        try {
            runTimeoutScan("SCHEDULED", scheduledBatchSize);
        } catch (RuntimeException e) {
            log.warn("Agent人工确认超时定时扫描失败", e);
        }
    }

    private boolean createJobRunAndAcquireLock(String jobRunId,
                                               String ownerId,
                                               String triggerType,
                                               int safeBatchSize,
                                               LocalDateTime startedAt) {
        LocalDateTime lockUntil = startedAt.plusMinutes(5);
        Boolean acquired = transactionTemplate.execute(status -> {
            timeoutRepository.createTaskTimeoutJobRun(jobRunId, triggerType, safeBatchSize, ownerId, startedAt);

            if (!timeoutRepository.tryAcquireJobLock(LOCK_NAME, ownerId, startedAt, lockUntil)) {
                timeoutRepository.finishTaskTimeoutJobRun(jobRunId, "SKIPPED", 0L, 0L, 1L, "已有实例正在执行AI任务超时扫描");
                return false;
            }
            return true;
        });
        return Boolean.TRUE.equals(acquired);
    }

    private void finishJobRun(String jobRunId,
                              String status,
                              long scannedCount,
                              long cancelledCount,
                              long skippedCount,
                              String errorMessage) {
        transactionTemplate.executeWithoutResult(statusHolder ->
            timeoutRepository.finishTaskTimeoutJobRun(jobRunId, status, scannedCount, cancelledCount, skippedCount, errorMessage)
        );
    }

    private void releaseLockQuietly(String ownerId) {
        try {
            transactionTemplate.executeWithoutResult(status ->
                timeoutRepository.releaseJobLock(LOCK_NAME, ownerId, LocalDateTime.now())
            );
        } catch (RuntimeException e) {
            log.warn("Agent人工确认超时扫描锁释放失败，ownerId={}", ownerId, e);
        }
    }

    private ScanStats cancelExpiredWaitingTasksInTransaction(int safeBatchSize, LocalDateTime now) {
        String timeoutPayload = write(objectMapper, Map.of(
            "decision", "TIMEOUT",
            "status", "CANCELLED",
            "reason", TIMEOUT_SUMMARY
        ));
        List<AgentExpiredTaskRow> expiredTasks = timeoutRepository.listExpiredWaitingTasks(now, safeBatchSize);
        ScanStats stats = new ScanStats();
        stats.scannedCount = expiredTasks.size();
        long cancelledCount = 0L;
        for (AgentExpiredTaskRow expiredTask : expiredTasks) {
            if (timeoutRepository.cancelExpiredWaitingTask(expiredTask.taskId(), now, timeoutPayload)) {
                agentRepository.insertAuditLog(
                    IdGenerator.nextId(),
                    expiredTask.taskId(),
                    "TASK_TIMEOUT_CANCELLED",
                    "AGENT_TASK",
                    expiredTask.taskId(),
                    TIMEOUT_SUMMARY,
                    true
                );
                notifyWorkflowPreReviewTimeout(expiredTask, timeoutPayload);
                cancelledCount++;
            }
        }
        stats.cancelledCount = cancelledCount;
        return stats;
    }

    private void notifyWorkflowPreReviewTimeout(AgentExpiredTaskRow expiredTask, String timeoutPayload) {
        if (!WF_REQUEST_CONTEXT_TYPE.equals(expiredTask.contextType()) || expiredTask.contextId() == null) {
            return;
        }
        WorkflowAgentResultCallback callback = workflowAgentResultCallbackProvider.getIfAvailable();
        if (callback != null) {
            callback.onPreReviewTimeout(expiredTask.contextId(), expiredTask.taskId(), timeoutPayload);
        }
    }

    private int normalizeBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new BizException(400, "超时扫描批次大小必须大于0");
        }
        return Math.min(batchSize, MAX_BATCH_SIZE);
    }

    private String normalizeTriggerType(String triggerType) {
        String normalized = StringUtils.hasText(triggerType) ? triggerType.trim().toUpperCase() : "SERVICE";
        if (!TRIGGER_TYPES.contains(normalized)) {
            throw new BizException(400, "AI任务超时扫描触发类型不支持");
        }
        return normalized;
    }

    private AgentTaskTimeoutScanResponse buildResponse(String jobRunId,
                                                       String triggerType,
                                                       String jobStatus,
                                                       int batchSize,
                                                       long scannedCount,
                                                       long cancelledCount,
                                                       long skippedCount,
                                                       String errorMessage,
                                                       LocalDateTime startedAt) {
        AgentTaskTimeoutScanResponse response = new AgentTaskTimeoutScanResponse();
        response.setJobRunId(jobRunId);
        response.setTriggerType(triggerType);
        response.setJobStatus(jobStatus);
        response.setBatchSize(batchSize);
        response.setScannedCount(scannedCount);
        response.setCancelledCount(cancelledCount);
        response.setSkippedCount(skippedCount);
        response.setErrorMessage(errorMessage);
        response.setStartedAt(startedAt);
        response.setFinishedAt(LocalDateTime.now());
        return response;
    }

    private static class ScanStats {
        private long scannedCount;
        private long cancelledCount;
    }
}
