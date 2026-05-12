package com.workforcehub.org.service;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.org.domain.EmpUserPreference;
import com.workforcehub.org.dto.EmployeePreferenceResponse;
import com.workforcehub.org.dto.UpdateEmployeePreferenceRequest;
import com.workforcehub.org.mapper.EmpUserPreferenceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 员工系统偏好云端持久化。
 * - 主题、会话密度、列表宽度。
 * - 写时合并：仅覆盖请求中提供的字段。
 * - 读时返回默认值，避免首次登录时端上需要兜底。
 */
@Service
public class EmployeePreferenceService {

    public static final String DEFAULT_THEME = "light";
    public static final String DEFAULT_DENSITY = "cozy";
    public static final int DEFAULT_LIST_WIDTH = 400;
    public static final int MIN_LIST_WIDTH = 384;
    public static final int MAX_LIST_WIDTH = 520;

    private final EmpUserPreferenceMapper preferenceMapper;

    public EmployeePreferenceService(EmpUserPreferenceMapper preferenceMapper) {
        this.preferenceMapper = preferenceMapper;
    }

    public EmployeePreferenceResponse getCurrent() {
        ensureEmployeeContext();
        Long userId = CurrentUserHolder.requireUserId();
        EmpUserPreference row = preferenceMapper.selectById(userId);
        return toResponse(row);
    }

    @Transactional(rollbackFor = Exception.class)
    public EmployeePreferenceResponse updateCurrent(UpdateEmployeePreferenceRequest request) {
        ensureEmployeeContext();
        Long userId = CurrentUserHolder.requireUserId();
        EmpUserPreference existing = preferenceMapper.selectById(userId);
        boolean inserting = existing == null;
        EmpUserPreference entity = inserting ? new EmpUserPreference() : existing;
        if (inserting) {
            entity.setUserId(userId);
            entity.setTheme(DEFAULT_THEME);
            entity.setMessageDensity(DEFAULT_DENSITY);
            entity.setListWidth(DEFAULT_LIST_WIDTH);
        }
        if (request != null) {
            if (request.getTheme() != null) {
                entity.setTheme(request.getTheme());
            }
            if (request.getMessageDensity() != null) {
                entity.setMessageDensity(request.getMessageDensity());
            }
            if (request.getListWidth() != null) {
                int width = clampListWidth(request.getListWidth());
                entity.setListWidth(width);
            }
        }
        if (inserting) {
            preferenceMapper.insert(entity);
        } else {
            preferenceMapper.updateById(entity);
        }
        return toResponse(entity);
    }

    private EmployeePreferenceResponse toResponse(EmpUserPreference row) {
        EmployeePreferenceResponse response = new EmployeePreferenceResponse();
        response.setTheme(row != null && row.getTheme() != null ? row.getTheme() : DEFAULT_THEME);
        response.setMessageDensity(row != null && row.getMessageDensity() != null ? row.getMessageDensity() : DEFAULT_DENSITY);
        response.setListWidth(row != null && row.getListWidth() != null ? clampListWidth(row.getListWidth()) : DEFAULT_LIST_WIDTH);
        return response;
    }

    private int clampListWidth(int width) {
        return Math.max(MIN_LIST_WIDTH, Math.min(MAX_LIST_WIDTH, width));
    }

    private void ensureEmployeeContext() {
        if (!CurrentUserHolder.isEmployee()) {
            throw new BizException(403, "仅员工端支持读取个人偏好");
        }
    }
}
