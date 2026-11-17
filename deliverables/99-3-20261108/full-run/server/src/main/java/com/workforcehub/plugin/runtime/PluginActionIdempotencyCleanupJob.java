package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginActionIdempotencyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 清理已过重放期的 HTTP 插件动作幂等记录。
 */
@Service
public class PluginActionIdempotencyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(PluginActionIdempotencyCleanupJob.class);
    private static final int BATCH_SIZE = 1000;
    private static final int MAX_BATCHES = 100;

    private final PluginActionIdempotencyRepository repository;

    public PluginActionIdempotencyCleanupJob(PluginActionIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Scheduled(
        fixedDelayString = "${workforce.plugin.action-idempotency.cleanup-delay-ms:600000}",
        initialDelayString = "${workforce.plugin.action-idempotency.cleanup-initial-delay-ms:600000}"
    )
    public void cleanupExpired() {
        try {
            for (int i = 0; i < MAX_BATCHES; i++) {
                if (repository.deleteExpired(BATCH_SIZE) < BATCH_SIZE) {
                    return;
                }
            }
            log.warn("HTTP 插件动作幂等记录单轮清理达到上限");
        } catch (RuntimeException e) {
            log.warn("HTTP 插件动作幂等记录清理失败", e);
        }
    }
}
