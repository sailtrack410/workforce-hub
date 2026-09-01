package com.workforcehub.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.system.dto.NotificationChannelResponse;
import com.workforcehub.system.dto.NotificationChannelSaveRequest;
import com.workforcehub.system.dto.NotificationChannelTestRequest;
import com.workforcehub.system.dto.NotificationChannelTestResponse;
import com.workforcehub.system.repository.NotificationChannelRepository;
import com.workforcehub.system.repository.NotificationChannelRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import static com.workforcehub.common.util.JsonValues.write;

@Service
public class NotificationChannelService {

    private static final Logger log = LoggerFactory.getLogger(NotificationChannelService.class);

    private final NotificationChannelRepository repository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    public NotificationChannelService(NotificationChannelRepository repository,
                                      JdbcTemplate jdbcTemplate,
                                      AuditLogWriter auditLogWriter,
                                      ObjectMapper objectMapper) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogWriter = auditLogWriter;
        this.objectMapper = objectMapper;
    }

    public List<NotificationChannelResponse> listChannels() {
        repository.ensureEmailDefault();
        return repository.list().stream().map(this::toResponse).toList();
    }

    public NotificationChannelResponse getChannel(String channelCode) {
        NotificationChannelRow row = findSupportedChannel(channelCode);
        return toResponse(row);
    }

    @Transactional
    public NotificationChannelResponse updateChannel(String channelCode, NotificationChannelSaveRequest request) {
        NotificationChannelRow existing = findSupportedChannel(channelCode);
        Long operatorId = CurrentUserHolder.requireUserId();
        String secretValue = StringUtils.hasText(request.secretValue()) ? request.secretValue().trim() : existing.secretValue();
        repository.updateEmail(
            existing,
            request.enabled(),
            trim(request.host()),
            request.port(),
            trim(request.username()),
            secretValue,
            trim(request.fromAddress()),
            trim(request.fromName()),
            request.sslEnabled(),
            request.starttlsEnabled(),
            request.timeoutMillis() == null ? 5000 : request.timeoutMillis(),
            operatorId
        );
        NotificationChannelRow updated = repository.findByCode(existing.channelCode());
        auditLogWriter.write(
            "ADMIN",
            operatorId,
            actionType(existing.enabled(), updated.enabled()),
            "NOTIFY_CHANNEL",
            existing.id(),
            "SUCCESS",
            write(objectMapper, Map.of(
                "channelCode", existing.channelCode(),
                "enabled", updated.enabled(),
                "host", nullToEmpty(updated.host()),
                "port", updated.port() == null ? 0 : updated.port(),
                "hasSecret", StringUtils.hasText(updated.secretValue())
            ))
        );
        return toResponse(updated);
    }

    @Transactional
    public NotificationChannelTestResponse testChannel(String channelCode, NotificationChannelTestRequest request) {
        NotificationChannelRow existing = findSupportedChannel(channelCode);
        Long operatorId = CurrentUserHolder.requireUserId();
        String adminEmail = currentAdminEmail(operatorId);
        if (!StringUtils.hasText(adminEmail) || !adminEmail.equalsIgnoreCase(trim(request.recipient()))) {
            throw new BizException(400, "测试收件人必须是当前管理员邮箱");
        }
        if (existing.lastTestedAt() != null && existing.lastTestedAt().isAfter(LocalDateTime.now().minusSeconds(30))) {
            throw new BizException(429, "测试发送过于频繁");
        }
        String secretValue = StringUtils.hasText(request.secretValue()) ? request.secretValue().trim() : existing.secretValue();
        LocalDateTime testedAt = LocalDateTime.now();
        boolean success = false;
        String message;
        try {
            sendTestMail(request, secretValue);
            success = true;
            message = "测试成功";
        } catch (Exception e) {
            log.warn("通知渠道测试邮件发送失败 channelCode={}", channelCode, e);
            message = sanitizeMailError(e);
        }
        repository.updateTestResult(existing.id(), success, message);
        auditLogWriter.write(
            "ADMIN",
            operatorId,
            "NOTIFY_CHANNEL_TEST",
            "NOTIFY_CHANNEL",
            existing.id(),
            success ? "SUCCESS" : "FAIL",
            write(objectMapper, Map.of(
                "channelCode", existing.channelCode(),
                "recipient", request.recipient(),
                "host", trim(request.host()),
                "port", request.port() == null ? 0 : request.port(),
                "success", success,
                "message", message
            ))
        );
        return new NotificationChannelTestResponse(success, message, testedAt);
    }

    private NotificationChannelRow findSupportedChannel(String channelCode) {
        if (!"EMAIL_DEFAULT".equals(channelCode)) {
            throw new BizException(404, "通知渠道不存在");
        }
        return repository.ensureEmailDefault();
    }

    private NotificationChannelResponse toResponse(NotificationChannelRow row) {
        return new NotificationChannelResponse(
            row.channelCode(),
            row.channelType(),
            row.channelName(),
            row.enabled(),
            row.host(),
            row.port(),
            row.username(),
            StringUtils.hasText(row.secretValue()),
            row.fromAddress(),
            row.fromName(),
            row.sslEnabled(),
            row.starttlsEnabled(),
            row.timeoutMillis(),
            row.lastTestSuccess(),
            row.lastTestMessage(),
            row.lastTestedAt(),
            row.updatedAt()
        );
    }

    private void sendTestMail(NotificationChannelTestRequest request, String secretValue) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(trim(request.host()));
        sender.setPort(request.port() == null ? 25 : request.port());
        sender.setUsername(trim(request.username()));
        sender.setPassword(secretValue);

        int timeoutMillis = request.timeoutMillis() == null ? 5000 : request.timeoutMillis();
        Properties props = sender.getJavaMailProperties();
        props.put("mail.smtp.auth", String.valueOf(StringUtils.hasText(request.username()) || StringUtils.hasText(secretValue)));
        props.put("mail.smtp.ssl.enable", String.valueOf(request.sslEnabled()));
        props.put("mail.smtp.starttls.enable", String.valueOf(request.starttlsEnabled()));
        props.put("mail.smtp.connectiontimeout", String.valueOf(timeoutMillis));
        props.put("mail.smtp.timeout", String.valueOf(timeoutMillis));
        props.put("mail.smtp.writetimeout", String.valueOf(timeoutMillis));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(trim(request.fromAddress()));
        message.setTo(trim(request.recipient()));
        message.setSubject("Workforce Hub 通知测试");
        message.setText("通知配置测试");
        sender.send(message);
    }

    private String currentAdminEmail(Long adminId) {
        List<String> emails = jdbcTemplate.query("""
            SELECT email
            FROM admin_user
            WHERE id = ? AND is_deleted = 0
            """, (rs, rowNum) -> rs.getString("email"), adminId);
        return emails.isEmpty() ? null : emails.get(0);
    }

    private String sanitizeMailError(Exception e) {
        Throwable cursor = e;
        while (cursor != null) {
            if (cursor instanceof SocketTimeoutException) {
                return "端口超时";
            }
            if (cursor instanceof ConnectException) {
                return "主机不可达";
            }
            cursor = cursor.getCause();
        }
        String text = Objects.toString(e.getMessage(), "");
        String lower = text.toLowerCase();
        if (lower.contains("auth") || lower.contains("authentication")) {
            return "认证失败";
        }
        if (lower.contains("tls") || lower.contains("ssl")) {
            return "TLS配置错误";
        }
        if (lower.contains("sender") || lower.contains("from")) {
            return "发件人被拒";
        }
        return "测试失败";
    }

    private String actionType(boolean beforeEnabled, boolean afterEnabled) {
        if (!beforeEnabled && afterEnabled) {
            return "NOTIFY_CHANNEL_ENABLED";
        }
        if (beforeEnabled && !afterEnabled) {
            return "NOTIFY_CHANNEL_DISABLED";
        }
        return "NOTIFY_CHANNEL_UPDATED";
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
