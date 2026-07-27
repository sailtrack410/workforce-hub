package com.workforcehub.plugin.controller.employee;

import com.workforcehub.common.util.CurrentUserHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class EmployeePluginService {

    private static final Pattern VALID_PLUGIN_ID = Pattern.compile("^[a-z0-9-]{1,64}$");
    private static final Pattern VALID_SRI = Pattern.compile("^sha384-[A-Za-z0-9+/]{64}$");

    private static final String SQL = """
        SELECT DISTINCT p.plugin_id, p.name, p.frontend_sha384
        FROM sys_plugin p
        JOIN sys_plugin_visibility v ON v.plugin_id = p.plugin_id
        JOIN org_user cu ON cu.id = ?
          AND cu.is_deleted = 0
          AND cu.status = 'ACTIVE'
          AND cu.employment_status IN ('PROBATION','ACTIVE','PENDING_OFFBOARD')
        WHERE p.status = 'ACTIVE'
          AND p.type = 'UI'
          AND (
            v.scope_type = 'ALL'
            OR (v.scope_type = 'USER' AND v.scope_value = CAST(cu.id AS CHAR))
            OR (v.scope_type = 'DEPT' AND v.scope_value = CAST(cu.primary_dept_id AS CHAR))
            OR (v.scope_type = 'POSITION' AND v.scope_value = CAST(cu.position_id AS CHAR))
            OR (v.scope_type = 'MANAGER' AND (
              EXISTS (SELECT 1 FROM org_user sub
                      WHERE sub.manager_user_id = cu.id
                        AND sub.is_deleted = 0
                        AND sub.status = 'ACTIVE'
                        AND sub.employment_status IN ('PROBATION','ACTIVE','PENDING_OFFBOARD'))
              OR EXISTS (SELECT 1 FROM org_department_leader dl
                         WHERE dl.user_id = cu.id
                           AND dl.status = 'ACTIVE')
            ))
          )
        ORDER BY p.name, p.plugin_id
        """;

    private record PluginCandidate(String id, String name, String integrity) {}

    private final JdbcTemplate jdbc;

    public EmployeePluginService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<EmployeePluginResponse> listVisiblePlugins() {
        Long userId = CurrentUserHolder.requireUserId();

        List<PluginCandidate> candidates = jdbc.query(SQL, (rs, rowNum) ->
            new PluginCandidate(
                rs.getString("plugin_id"),
                rs.getString("name"),
                rs.getString("frontend_sha384")
            ), userId);

        return candidates.stream()
            .filter(c -> c.id() != null && VALID_PLUGIN_ID.matcher(c.id()).matches())
            .filter(c -> c.integrity() != null && VALID_SRI.matcher(c.integrity()).matches())
            .map(c -> new EmployeePluginResponse(
                c.id(), c.name(),
                "/plugins/" + c.id() + "/index.js",
                c.integrity()
            ))
            .toList();
    }
}
