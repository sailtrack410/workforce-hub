package com.workforcehub.support;

import com.workforcehub.workflow.service.WorkflowAgentPreReviewTrigger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * workflow Agent 预审集成测试专用触发器。
 */
@TestConfiguration
public class WorkflowAgentPreReviewTestConfiguration {

    @Bean
    @Primary
    TestWorkflowAgentPreReviewTrigger testWorkflowAgentPreReviewTrigger() {
        return new TestWorkflowAgentPreReviewTrigger();
    }

    public static class TestWorkflowAgentPreReviewTrigger implements WorkflowAgentPreReviewTrigger {

        private boolean enabled = true;
        private Long nextAgentTaskId = 88001L;
        private Long lastRequestId;
        private String lastProcessTypeCode;
        private String lastBizType;
        private Long lastBizId;
        private Long lastApplicantUserId;

        @Override
        public Long triggerPreReview(Long requestId,
                                     String processTypeCode,
                                     String bizType,
                                     Long bizId,
                                     Long applicantUserId) {
            if (!enabled) {
                return null;
            }
            this.lastRequestId = requestId;
            this.lastProcessTypeCode = processTypeCode;
            this.lastBizType = bizType;
            this.lastBizId = bizId;
            this.lastApplicantUserId = applicantUserId;
            return nextAgentTaskId;
        }

        public void disable() {
            this.enabled = false;
            clearLastContext();
        }

        public void reset() {
            this.enabled = true;
            this.nextAgentTaskId = 88001L;
            clearLastContext();
        }

        private void clearLastContext() {
            this.lastRequestId = null;
            this.lastProcessTypeCode = null;
            this.lastBizType = null;
            this.lastBizId = null;
            this.lastApplicantUserId = null;
        }

        public Long getLastRequestId() {
            return lastRequestId;
        }

        public String getLastProcessTypeCode() {
            return lastProcessTypeCode;
        }

        public String getLastBizType() {
            return lastBizType;
        }

        public Long getLastBizId() {
            return lastBizId;
        }

        public Long getLastApplicantUserId() {
            return lastApplicantUserId;
        }
    }
}
