package com.workforcehub.system.repository;

import com.workforcehub.common.util.IdGenerator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据字典仓储
 */
@Repository
public class DictRepository {

    private final JdbcTemplate jdbcTemplate;

    public DictRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 字典类型列表：distinct dict_type + 各类型条目数
     */
    public List<Map<String, Object>> listTypes() {
        return jdbcTemplate.query("""
            SELECT dict_type, COUNT(*) AS item_count
            FROM sys_dict
            GROUP BY dict_type
            ORDER BY dict_type ASC
            """, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dictType", rs.getString("dict_type"));
            row.put("itemCount", rs.getInt("item_count"));
            return row;
        });
    }

    /**
     * 按字典类型列条目，status 可选过滤（null 查全部）
     */
    public List<Map<String, Object>> listByType(String dictType, Integer status) {
        if (status == null) {
            return jdbcTemplate.query("""
                SELECT id, dict_type, item_code, item_label, sort_no, status, remark, created_at, updated_at
                FROM sys_dict
                WHERE dict_type = ?
                ORDER BY sort_no ASC, id ASC
                """, (rs, rowNum) -> row(rs), dictType);
        }
        return jdbcTemplate.query("""
            SELECT id, dict_type, item_code, item_label, sort_no, status, remark, created_at, updated_at
            FROM sys_dict
            WHERE dict_type = ? AND status = ?
            ORDER BY sort_no ASC, id ASC
            """, (rs, rowNum) -> row(rs), dictType, status);
    }

    public Map<String, Object> insert(String dictType, String itemCode, String itemLabel,
                                      int sortNo, int status, String remark, Long operatorId) {
        long id = IdGenerator.nextId();
        jdbcTemplate.update("""
            INSERT INTO sys_dict
                (id, dict_type, item_code, item_label, sort_no, status, remark, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            dictType,
            itemCode,
            itemLabel,
            sortNo,
            status,
            remark,
            operatorId,
            operatorId
        );
        return getById(id);
    }

    public Map<String, Object> update(Long id, String itemLabel, int sortNo, int status,
                                      String remark, Long operatorId) {
        jdbcTemplate.update("""
            UPDATE sys_dict
            SET item_label = ?,
                sort_no = ?,
                status = ?,
                remark = ?,
                updated_by = ?
            WHERE id = ?
            """,
            itemLabel,
            sortNo,
            status,
            remark,
            operatorId,
            id
        );
        return getById(id);
    }

    public Map<String, Object> getById(Long id) {
        List<Map<String, Object>> rows = jdbcTemplate.query("""
            SELECT id, dict_type, item_code, item_label, sort_no, status, remark, created_at, updated_at
            FROM sys_dict
            WHERE id = ?
            """, (rs, rowNum) -> row(rs), id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM sys_dict WHERE id = ?", id);
    }

    /**
     * 查启用条目的显示名称，查不到返回 null
     */
    public String label(String dictType, String itemCode) {
        List<String> labels = jdbcTemplate.query("""
            SELECT item_label
            FROM sys_dict
            WHERE dict_type = ? AND item_code = ? AND status = 1
            """, (rs, rowNum) -> rs.getString("item_label"), dictType, itemCode);
        return labels.isEmpty() ? null : labels.get(0);
    }

    private Map<String, Object> row(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("dictType", rs.getString("dict_type"));
        row.put("itemCode", rs.getString("item_code"));
        row.put("itemLabel", rs.getString("item_label"));
        row.put("sortNo", rs.getInt("sort_no"));
        row.put("status", rs.getInt("status"));
        row.put("remark", rs.getString("remark"));
        LocalDateTime createdAt = rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime();
        LocalDateTime updatedAt = rs.getTimestamp("updated_at") == null ? null : rs.getTimestamp("updated_at").toLocalDateTime();
        row.put("createdAt", createdAt);
        row.put("updatedAt", updatedAt);
        return row;
    }
}
