package com.workforcehub.agent.scheduler;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.repository.AgentExpiredTaskRow;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.common.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Agent 等待人工确认任务的超时收口服务。
 */
@Service
public class AgentTaskTimeoutJobService {

    private static final int MAX_BATCH_SIZE = 500;
    private static final String TIMEOUT_SUMMARY = "人工确认超时，任务已取消";

    private final AgentRepository agentRepository;
    private final ObjectMapper objectMapper;

    public AgentTaskTimeoutJobService(AgentRepository agentRepository, ObjectMapper objectMapper) {
        this.agentRepository = agentRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public long cancelExpiredWaitingTasks(int batchSize) {
        int safeBatchSize = normalizeBatchSize(batchSize);
        LocalDateTime now = LocalDateTime.now();
        String timeoutPayload = writeJson(Map.of(
            "status", "CANCELLED",
            "reason", TIMEOUT_SUMMARY
        ));
        List<AgentExpiredTaskRow> expiredTasks = agentRepository.listExpiredWaitingTasks(now, safeBatchSize);
        long cancelledCount = 0L;
        for (AgentExpiredTaskRow expiredTask : expiredTasks) {
            if (agentRepository.cancelExpiredWaitingTask(expiredTask.taskId(), now, timeoutPayload)) {
                agentRepository.insertAuditLog(
                    IdWorker.getId(),
                    expiredTask.taskId(),
                    "TASK_TIMEOUT_CANCELLED",
                    "AGENT_TASK",
                    expiredTask.taskId(),
                    TIMEOUT_SUMMARY,
                    true
                );
                cancelledCount++;
            }
        }
        return cancelledCount;
    }

    private int normalizeBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new BizException(400, "超时扫描批次大小必须大于0");
        }
        return Math.min(batchSize, MAX_BATCH_SIZE);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(500, "JSON序列化失败");
        }
    }
}
