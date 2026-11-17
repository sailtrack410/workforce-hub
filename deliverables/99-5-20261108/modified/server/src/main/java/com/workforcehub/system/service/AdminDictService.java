package com.workforcehub.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.system.dto.DictItemResponse;
import com.workforcehub.system.dto.DictItemSaveRequest;
import com.workforcehub.system.repository.DictRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.workforcehub.common.util.JsonValues.write;
import static com.workforcehub.common.util.TextValues.trimToNull;

/**
 * 数据字典管理服务
 * 读多写少，内置按字典类型的内存标签缓存，写操作后失效对应类型
 */
@Service
public class AdminDictService {

    private final DictRepository repository;
    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    // key = dictType, value = (itemCode -> itemLabel)（仅启用条目）
    private final ConcurrentMap<String, Map<String, String>> labelCache = new ConcurrentHashMap<>();

    public AdminDictService(DictRepository repository, AuditLogWriter auditLogWriter, ObjectMapper objectMapper) {
        this.repository = repository;
        this.auditLogWriter = auditLogWriter;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> listTypes() {
        return repository.listTypes();
    }

    public List<DictItemResponse> listByType(String dictType, Integer status) {
        return repository.listByType(dictType, status).stream().map(this::toResponse).toList();
    }

    @Transactional
    public DictItemResponse create(DictItemSaveRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String dictType = request.dictType().trim();
        String itemCode = request.itemCode().trim();
        Map<String, Object> created = repository.insert(
            dictType,
            itemCode,
            request.itemLabel().trim(),
            request.sortNo() == null ? 0 : request.sortNo(),
            request.status() == null ? 1 : request.status(),
            trimToNull(request.remark()),
            operatorId
        );
        auditLogWriter.write(
            "ADMIN",
            operatorId,
            "DICT_CREATE",
            "SYS_DICT",
            (Long) created.get("id"),
            "SUCCESS",
            write(objectMapper, Map.of("dictType", dictType, "itemCode", itemCode))
        );
        labelCache.remove(dictType);
        return toResponse(created);
    }

    @Transactional
    public DictItemResponse update(Long id, DictItemSaveRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        Map<String, Object> existing = repository.getById(id);
        if (existing == null) {
            throw new BizException(404, "字典条目不存在");
        }
        Map<String, Object> updated = repository.update(
            id,
            request.itemLabel().trim(),
            request.sortNo() == null ? 0 : request.sortNo(),
            request.status() == null ? 1 : request.status(),
            trimToNull(request.remark()),
            operatorId
        );
        auditLogWriter.write(
            "ADMIN",
            operatorId,
            "DICT_UPDATE",
            "SYS_DICT",
            id,
            "SUCCESS",
            write(objectMapper, Map.of(
                "dictType", (String) existing.get("dictType"),
                "itemCode", (String) existing.get("itemCode"),
                "itemLabel", request.itemLabel().trim()
            ))
        );
        // 与 AdminAlertService 约定一致：写操作后失效对应类型的标签缓存
        labelCache.remove((String) existing.get("dictType"));
        return toResponse(updated);
    }

    @Transactional
    public void delete(Long id) {
        Long operatorId = CurrentUserHolder.requireUserId();
        Map<String, Object> existing = repository.getById(id);
        if (existing == null) {
            throw new BizException(404, "字典条目不存在");
        }
        repository.delete(id);
        auditLogWriter.write(
            "ADMIN",
            operatorId,
            "DICT_DELETE",
            "SYS_DICT",
            id,
            "SUCCESS",
            write(objectMapper, Map.of(
                "dictType", (String) existing.get("dictType"),
                "itemCode", (String) existing.get("itemCode")
            ))
        );
        labelCache.remove((String) existing.get("dictType"));
    }

    /**
     * 查启用条目显示名称（带缓存），查不到返回 null
     */
    public String label(String dictType, String code) {
        Map<String, String> labels = labelCache.computeIfAbsent(dictType, this::loadLabels);
        return labels.get(code);
    }

    /**
     * 查启用条目显示名称，查不到返回 fallback
     */
    public String label(String dictType, String code, String fallback) {
        String label = label(dictType, code);
        return StringUtils.hasText(label) ? label : fallback;
    }

    private Map<String, String> loadLabels(String dictType) {
        Map<String, String> labels = new ConcurrentHashMap<>();
        for (Map<String, Object> item : repository.listByType(dictType, 1)) {
            labels.put((String) item.get("itemCode"), (String) item.get("itemLabel"));
        }
        return labels;
    }

    private DictItemResponse toResponse(Map<String, Object> row) {
        Integer status = (Integer) row.get("status");
        return new DictItemResponse(
            (Long) row.get("id"),
            (String) row.get("dictType"),
            (String) row.get("itemCode"),
            (String) row.get("itemLabel"),
            (Integer) row.get("sortNo"),
            status,
            status != null && status == 1 ? "启用" : "停用",
            (String) row.get("remark"),
            (LocalDateTime) row.get("createdAt"),
            (LocalDateTime) row.get("updatedAt")
        );
    }
}
