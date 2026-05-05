package com.workforcehub.file.service;

import com.workforcehub.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 下载票据状态机执行服务。
 */
@Service
public class DownloadTicketExecutionService {

    private final JdbcTemplate jdbcTemplate;

    public DownloadTicketExecutionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void claimActiveTicket(Long ticketId) {
        int updated = jdbcTemplate.update(
            "UPDATE sys_download_ticket SET status = 'USED', updated_at = NOW() WHERE id = ? AND status = 'ACTIVE'",
            ticketId
        );
        if (updated == 0) {
            throw new BizException(409, "下载票据已使用或已失效");
        }
    }

    public void expireActiveTicket(Long ticketId) {
        jdbcTemplate.update(
            "UPDATE sys_download_ticket SET status = 'EXPIRED', updated_at = NOW() WHERE id = ? AND status = 'ACTIVE'",
            ticketId
        );
    }
}
