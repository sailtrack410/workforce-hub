#!/usr/bin/env python3
"""Bootstrap repeatable demo data for employee-web local integration."""

from __future__ import annotations

import json
import os
import sys
import time
from dataclasses import dataclass
from datetime import datetime, timedelta
from typing import Any
from urllib import error, parse, request


BASE_URL = os.environ.get("WFH_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
ADMIN_LOGIN = os.environ.get("WFH_ADMIN_LOGIN", "admin.demo")
ADMIN_PASSWORD = os.environ.get("WFH_ADMIN_PASSWORD", "Admin@123456")
EMPLOYEE_PASSWORD = os.environ.get("WFH_EMPLOYEE_PASSWORD", "Employee@123456")
user_login_by_user_id: dict[int, str] = {}


@dataclass(frozen=True)
class DemoUserSpec:
    login_name: str
    real_name: str
    employee_no: str
    mobile: str
    user_id: int | None
    manager_login: str | None
    leave_minutes: int
    bind_default_email: bool = True


DEMO_USERS = (
    DemoUserSpec("demo.leader", "演示领导", "D22001", "13900002201", 22001, None, 1440),
    DemoUserSpec("demo.employee1", "演示员工一", "D22002", "13900002202", 22002, "demo.leader", 960),
    DemoUserSpec("demo.employee2", "演示员工二", "D22003", "13900002203", 22003, "demo.leader", 480, False),
)


class ApiError(RuntimeError):
    pass


class HttpClient:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")
        self.cookies: dict[str, str] = {}

    def clone(self) -> "HttpClient":
        other = HttpClient(self.base_url)
        other.cookies = dict(self.cookies)
        return other

    def request(self,
                method: str,
                path: str,
                payload: Any | None = None,
                params: dict[str, Any] | None = None,
                allow_biz_error: bool = False) -> dict[str, Any]:
        url = self.base_url + path
        if params:
            filtered = {k: v for k, v in params.items() if v is not None}
            if filtered:
                url += "?" + parse.urlencode(filtered)
        data = None
        headers = {"Accept": "application/json"}
        if payload is not None:
            data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if self.cookies:
            headers["Cookie"] = "; ".join(f"{k}={v}" for k, v in self.cookies.items())
        req = request.Request(url, data=data, headers=headers, method=method.upper())
        try:
            with request.urlopen(req, timeout=20) as resp:
                body = resp.read().decode("utf-8")
                self._capture_cookies(resp.headers)
        except error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            raise ApiError(f"HTTP {exc.code} {method} {path}: {raw}") from exc
        if not body:
            return {}
        parsed_body = json.loads(body)
        code = parsed_body.get("code")
        if not allow_biz_error and code not in (None, 0):
            raise ApiError(f"Biz error {code} {method} {path}: {parsed_body.get('message')}")
        return parsed_body

    def _capture_cookies(self, headers: Any) -> None:
        set_cookie_headers = headers.get_all("Set-Cookie") or []
        for item in set_cookie_headers:
            first = item.split(";", 1)[0]
            if "=" not in first:
                continue
            key, value = first.split("=", 1)
            self.cookies[key] = value


def log(message: str) -> None:
    print(message, flush=True)


def ensure_health(client: HttpClient) -> None:
    payload = client.request("GET", "/actuator/health")
    if payload.get("status") != "UP":
        raise ApiError(f"health not UP: {payload}")


def login_admin(client: HttpClient) -> None:
    client.request("POST", "/api/admin/auth/login", {
        "loginName": ADMIN_LOGIN,
        "password": ADMIN_PASSWORD,
    })


def login_employee(source: HttpClient | Any, login_name: str) -> HttpClient:
    base_url = source.base_url if isinstance(source, HttpClient) else BASE_URL
    client = HttpClient(base_url)
    client.request("POST", "/api/employee/auth/login", {
        "loginName": login_name,
        "password": EMPLOYEE_PASSWORD,
    })
    return client


def fetch_department_tree(admin: HttpClient) -> list[dict[str, Any]]:
    return admin.request("GET", "/api/admin/departments/tree").get("data") or []


def flatten_departments(nodes: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    result: dict[str, dict[str, Any]] = {}
    stack = list(nodes)
    while stack:
        node = stack.pop()
        result[node["deptCode"]] = node
        children = node.get("children") or []
        stack.extend(children)
    return result


def ensure_department(admin: HttpClient,
                      dept_code: str,
                      dept_name: str,
                      dept_type: str,
                      parent_id: int | None) -> int:
    tree = fetch_department_tree(admin)
    by_code = flatten_departments(tree)
    existing = by_code.get(dept_code)
    if existing:
        return int(existing["id"])
    resp = admin.request("POST", "/api/admin/departments", {
        "parentId": parent_id,
        "deptCode": dept_code,
        "deptName": dept_name,
        "deptType": dept_type,
        "status": "ACTIVE",
    })
    return int(resp["data"])


def ensure_position(admin: HttpClient,
                    position_code: str,
                    position_name: str,
                    category: str) -> int:
    items = admin.request("GET", "/api/admin/positions").get("data") or []
    for item in items:
        if item.get("positionCode") == position_code:
            return int(item["id"])
    resp = admin.request("POST", "/api/admin/positions", {
        "positionCode": position_code,
        "positionName": position_name,
        "positionCategory": category,
        "status": "ACTIVE",
    })
    return int(resp["data"])


def find_user(admin: HttpClient,
              *,
              keyword: str | None = None,
              manager_user_id: int | None = None) -> list[dict[str, Any]]:
    payload = admin.request("GET", "/api/admin/users", params={
        "pageNo": 1,
        "pageSize": 200,
        "keyword": keyword,
        "managerUserId": manager_user_id,
    })
    return (payload.get("data") or {}).get("list") or []


def get_user_detail(admin: HttpClient, user_id: int) -> dict[str, Any]:
    return admin.request("GET", f"/api/admin/users/{user_id}").get("data") or {}


def ensure_demo_user(admin: HttpClient,
                     spec: DemoUserSpec,
                     dept_id: int,
                     position_id: int,
                     manager_user_id: int | None) -> int:
    candidates = find_user(admin, keyword=spec.real_name)
    user_id = None
    for item in candidates:
        detail = get_user_detail(admin, int(item["userId"]))
        if detail.get("realName") == spec.real_name and detail.get("employeeNo") == spec.employee_no:
            user_id = int(detail["userId"])
            break
    if user_id is None and spec.user_id is not None:
        result = admin.request("GET", f"/api/admin/users/{spec.user_id}", allow_biz_error=True)
        if result.get("code") == 0 and (result.get("data") or {}).get("realName") == spec.real_name:
            user_id = int(result["data"]["userId"])
    body = {
        "employeeNo": spec.employee_no,
        "realName": spec.real_name,
        "gender": "MALE" if "领导" in spec.real_name else "FEMALE",
        "mobile": spec.mobile,
        "primaryDeptId": dept_id,
        "positionId": position_id,
        "managerUserId": manager_user_id,
        "employmentStatus": "ACTIVE",
        "hireDate": "2026-05-01",
        "status": "ACTIVE",
        "workLocation": "杭州",
    }
    if user_id is None:
        resp = admin.request("POST", "/api/admin/users", body)
        user_id = int(resp["data"])
        log(f"创建演示用户 {spec.login_name} -> userId={user_id}")
    else:
        update_body = dict(body)
        update_body.pop("employmentStatus", None)
        admin.request("PUT", f"/api/admin/users/{user_id}", update_body)
        detail = get_user_detail(admin, user_id)
        if detail.get("employmentStatus") != "ACTIVE":
            admin.request("PUT", f"/api/admin/users/{user_id}/employment-status", {
                "employmentStatus": "ACTIVE",
            })
        log(f"复用演示用户 {spec.login_name} -> userId={user_id}")
    admin.request("PUT", f"/api/admin/users/{user_id}/account", {
        "loginName": spec.login_name,
        "newPassword": EMPLOYEE_PASSWORD,
        "email": f"{spec.login_name}@example.com" if spec.bind_default_email else "",
        "accountStatus": "ACTIVE",
    })
    return user_id


def ensure_leave_type(admin: HttpClient) -> int:
    leave_types = admin.request("GET", "/api/admin/leave/types").get("data") or []
    for item in leave_types:
        if item.get("leaveCode") == "ANNUAL_DEMO":
            return int(item["leaveTypeId"])
    result = admin.request("POST", "/api/admin/leave/types", {
        "leaveCode": "ANNUAL_DEMO",
        "leaveName": "演示年假",
        "balanceMode": "DEDUCT",
        "status": "ACTIVE",
    }, allow_biz_error=True)
    if result.get("code") == 0:
        leave_type_id = int(result["data"]["leaveTypeId"])
        log(f"创建假种 ANNUAL_DEMO -> leaveTypeId={leave_type_id}")
        return leave_type_id
    if result.get("message") != "假种编码已存在":
        raise ApiError(f"创建假种失败: {result}")
    leave_types = admin.request("GET", "/api/admin/leave/types").get("data") or []
    for item in leave_types:
        if item.get("leaveCode") == "ANNUAL_DEMO":
            return int(item["leaveTypeId"])
    raise ApiError("假种 ANNUAL_DEMO 已存在，但后台查询仍未返回该假种")


def ensure_leave_balance(admin: HttpClient, user_id: int, leave_type_id: int, target_minutes: int) -> None:
    employee_client = login_employee(admin, user_login_by_user_id[user_id])
    accounts = employee_client.request("GET", "/api/employee/leave/accounts").get("data") or []
    current = next((item for item in accounts if int(item["leaveTypeId"]) == leave_type_id), None)
    available = int(current.get("availableMinutes", 0)) if current else 0
    frozen = int(current.get("frozenMinutes", 0)) if current else 0
    current_total = available + frozen
    delta = target_minutes - current_total
    if delta == 0:
        return
    if delta < 0 and available + delta < 0:
        raise ApiError(
            f"无法把 userId={user_id} 假期总额调整到 {target_minutes}: "
            f"available={available}, frozen={frozen}, delta={delta}"
        )
    admin.request("POST", "/api/admin/leave/accounts/adjustments", {
        "userId": user_id,
        "leaveTypeId": leave_type_id,
        "changeMinutes": delta,
        "reason": "初始化演示年假",
        "sourceTxnKey": f"demo-balance-total-{user_id}-{target_minutes}-{delta}",
    })
    log(f"调整余额 userId={user_id} total {current_total} -> {target_minutes}")


def list_templates(admin: HttpClient, process_code: str) -> list[dict[str, Any]]:
    return admin.request("GET", "/api/admin/workflow/templates", params={"processCode": process_code}).get("data") or []


def list_process_types(admin: HttpClient, process_code: str) -> list[dict[str, Any]]:
    return admin.request("GET", "/api/admin/workflow/process-types", params={"processCode": process_code}).get("data") or []


def ensure_workflow_template(admin: HttpClient,
                             process_code: str,
                             process_name: str,
                             biz_type: str,
                             template_code: str,
                             template_name: str,
                             flowable_definition_key: str,
                             rule_snapshot_json: str) -> None:
    templates = list_templates(admin, process_code)
    if templates:
        current = templates[0].get("currentVersion") or {}
        if current.get("status") == "PUBLISHED":
            return
        template_id = int(templates[0]["templateId"])
    else:
        process_type = admin.request("POST", "/api/admin/workflow/process-types", {
            "processCode": process_code,
            "processName": process_name,
            "bizType": biz_type,
            "status": "ACTIVE",
        }, allow_biz_error=True)
        if process_type.get("code") == 0:
            process_type_id = int(process_type["data"]["processTypeId"])
        elif process_type.get("message") == "流程编码已存在":
            process_types = list_process_types(admin, process_code)
            if not process_types:
                raise ApiError(f"流程类型已存在但查询为空 {process_code}")
            process_type_id = int(process_types[0]["processTypeId"])
        else:
            raise ApiError(f"创建流程类型失败 {process_type}")
        template_resp = admin.request("POST", "/api/admin/workflow/templates", {
            "processTypeId": process_type_id,
            "templateCode": template_code,
            "templateName": template_name,
            "status": "ACTIVE",
        }, allow_biz_error=True)
        if template_resp.get("code") == 0:
            template_id = int(template_resp["data"]["templateId"])
        elif template_resp.get("message") == "流程模板编码已存在":
            templates = list_templates(admin, process_code)
            template = next((item for item in templates if item.get("templateCode") == template_code), None)
            if template is None:
                raise ApiError(f"流程模板已存在但查询为空 {template_code}")
            template_id = int(template["templateId"])
        else:
            templates = list_templates(admin, process_code)
            if not templates:
                raise ApiError(f"创建流程模板失败 {template_resp}")
            template_id = int(templates[0]["templateId"])
    versions = admin.request("GET", f"/api/admin/workflow/templates/{template_id}/versions").get("data") or []
    published = next((item for item in versions if item.get("status") == "PUBLISHED"), None)
    if published:
        return
    draft = next((item for item in versions if item.get("versionNo") == 1), None)
    if draft:
        version_id = int(draft["versionId"])
    else:
        version_resp = admin.request("POST", f"/api/admin/workflow/templates/{template_id}/versions", {
            "versionNo": 1,
            "formSchemaJson": "{\"formKey\":\"demo\"}",
            "ruleSnapshotJson": rule_snapshot_json,
        })
        version_id = int(version_resp["data"]["versionId"])
    admin.request("POST", f"/api/admin/workflow/template-versions/{version_id}/publish", {
        "flowableDefinitionKey": flowable_definition_key,
    })
    log(f"发布流程模板 {process_code} -> versionId={version_id}")


def ensure_private_conversation(employee_client: HttpClient, leader_user_id: int) -> int:
    result = employee_client.request("POST", "/api/employee/im/private-conversations", {
        "targetUserId": leader_user_id,
    })
    conversation_id = int(result["data"]["conversationId"])
    messages = employee_client.request("GET", f"/api/employee/im/conversations/{conversation_id}/messages").get("data") or []
    if messages:
        return conversation_id
    employee_client.request("POST", f"/api/employee/im/conversations/{conversation_id}/messages", {
        "messageType": "TEXT",
        "text": "领导您好，今天的演示链路我这边已经准备好了。",
        "clientMessageId": "demo-private-msg-emp1-1",
    })
    leader_client = login_employee(employee_client, "demo.leader")
    leader_client.request("POST", f"/api/employee/im/conversations/{conversation_id}/messages", {
        "messageType": "TEXT",
        "text": "收到，待会我们按登录、会话、审批、Agent 的顺序演示。",
        "clientMessageId": "demo-private-msg-leader-1",
    })
    log(f"初始化私聊会话 conversationId={conversation_id}")
    return conversation_id


def ensure_workflow_demo(employee1: HttpClient, leader: HttpClient, leave_type_id: int) -> None:
    requests_page = employee1.request("GET", "/api/employee/workflow/requests", params={
        "pageNo": 1,
        "pageSize": 20,
    }).get("data") or {}
    existing = requests_page.get("list") or []
    todos = leader.request("GET", "/api/employee/workflow/todos").get("data") or []
    if existing and todos:
        return
    start_at = datetime.now().replace(minute=0, second=0, microsecond=0) + timedelta(days=1, hours=9)
    end_at = start_at + timedelta(hours=4)
    create_resp = employee1.request("POST", "/api/employee/workflow/leave-requests", {
        "leaveTypeId": leave_type_id,
        "startAt": start_at.strftime("%Y-%m-%d %H:%M:%S"),
        "endAt": end_at.strftime("%Y-%m-%d %H:%M:%S"),
        "durationMinutes": 240,
        "reason": "演示请假申请",
        "attachmentRefIds": [],
    })
    request_id = int(create_resp["data"]["requestId"])
    todos = leader.request("GET", "/api/employee/workflow/todos").get("data") or []
    task = next((item for item in todos if int(item["requestId"]) == request_id), None)
    if task is None:
        raise ApiError(f"未找到新建申请的审批待办 requestId={request_id}")
    log(f"初始化请假申请 requestId={request_id} taskId={task['taskId']}")


def list_admin_agents(admin: HttpClient) -> list[dict[str, Any]]:
    return (admin.request("GET", "/api/admin/agents", params={"pageNo": 1, "pageSize": 100}).get("data") or {}).get("list") or []


def ensure_agent(admin: HttpClient, employee_user_ids: list[int]) -> int:
    agents = list_admin_agents(admin)
    existing = next((item for item in agents if item.get("agentCode") == "DEMO_IM_AGENT"), None)
    if existing:
        agent_id = int(existing["agentId"])
    else:
        create_resp = admin.request("POST", "/api/admin/agents", {
            "agentCode": "DEMO_IM_AGENT",
            "agentName": "演示会话助手",
            "agentType": "QA",
            "identityMode": "SYSTEM_IDENTITY",
        })
        agent_id = int(create_resp["data"]["agentId"])
        log(f"创建演示 Agent -> agentId={agent_id}")
    detail = admin.request("GET", f"/api/admin/agents/{agent_id}").get("data") or {}
    version_id = detail.get("publishedVersionId")
    if not version_id:
        version_resp = admin.request("POST", f"/api/admin/agents/{agent_id}/versions", {
            "versionNo": 1,
            "modelConfigJson": json.dumps({}, ensure_ascii=False),
            "systemPrompt": "你是用于本地联调演示的企业助手，回复简洁直接。",
        }, allow_biz_error=True)
        if version_resp.get("code") == 0:
            version_id = int(version_resp["data"]["versionId"])
        else:
            raise ApiError(f"创建演示 Agent 版本失败 {version_resp}")
        admin.request("POST", f"/api/admin/agents/versions/{version_id}/publish")
        detail = admin.request("GET", f"/api/admin/agents/{agent_id}").get("data") or {}
        log(f"发布演示 Agent versionId={version_id}")
    if detail.get("status") != "ACTIVE":
        raise ApiError("演示 Agent 当前未启用，请先检查现有数据状态")
    for employee_user_id in employee_user_ids:
        acl_resp = admin.request("POST", f"/api/admin/agents/{agent_id}/acl", {
            "subjectType": "USER",
            "subjectId": employee_user_id,
            "permCode": "use",
            "status": "ACTIVE",
        }, allow_biz_error=True)
        if acl_resp.get("code") not in (0, 409):
            raise ApiError(f"授予 Agent ACL 失败 userId={employee_user_id} {acl_resp}")
    return agent_id


def await_agent_reply(employee_client: HttpClient, conversation_id: int, timeout_seconds: int = 20) -> None:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        messages = employee_client.request("GET", f"/api/employee/im/conversations/{conversation_id}/messages").get("data") or []
        if len(messages) >= 2:
            return
        time.sleep(1)
    raise ApiError(f"等待 Agent 回复超时 conversationId={conversation_id}")


def ensure_agent_conversation(employee_client: HttpClient, agent_id: int) -> int:
    resp = employee_client.request("POST", "/api/employee/im/agent-conversations", {
        "agentId": agent_id,
    })
    conversation_id = int(resp["data"]["conversationId"])
    messages = employee_client.request("GET", f"/api/employee/im/conversations/{conversation_id}/messages").get("data") or []
    if len(messages) >= 2:
        return conversation_id
    employee_client.request("POST", f"/api/employee/im/conversations/{conversation_id}/messages", {
        "messageType": "TEXT",
        "text": "帮我总结今天待办",
        "clientMessageId": "demo-agent-msg-001",
    })
    await_agent_reply(employee_client, conversation_id)
    log(f"初始化 Agent 会话 conversationId={conversation_id}")
    return conversation_id


def main() -> int:
    try:
        client = HttpClient(BASE_URL)
        ensure_health(client)
        login_admin(client)
        log(f"连接后端成功 {BASE_URL}")

        dept_root_id = ensure_department(client, "DEMO", "演示组织", "DEPARTMENT", 110)
        employee_dept_id = ensure_department(client, "DEMO-EMP", "演示员工组", "TEAM", dept_root_id)
        leader_position_id = ensure_position(client, "DEMO_LEADER", "演示领导", "MANAGER")
        employee_position_id = ensure_position(client, "DEMO_EMPLOYEE", "演示员工", "STAFF")

        leader_user_id = ensure_demo_user(client, DEMO_USERS[0], employee_dept_id, leader_position_id, None)
        employee1_user_id = ensure_demo_user(client, DEMO_USERS[1], employee_dept_id, employee_position_id, leader_user_id)
        employee2_user_id = ensure_demo_user(client, DEMO_USERS[2], employee_dept_id, employee_position_id, leader_user_id)

        user_ids = {
            "demo.leader": leader_user_id,
            "demo.employee1": employee1_user_id,
            "demo.employee2": employee2_user_id,
        }
        global user_login_by_user_id
        user_login_by_user_id = {value: key for key, value in user_ids.items()}

        leave_type_id = ensure_leave_type(client)
        for spec in DEMO_USERS:
            actual_user_id = user_ids[spec.login_name]
            ensure_leave_balance(client, actual_user_id, leave_type_id, spec.leave_minutes)

        ensure_workflow_template(
            client,
            "LEAVE",
            "请假流程",
            "LEAVE",
            "LEAVE_MANAGER_APPROVAL",
            "请假直属上级审批",
            "leave_manager_approval",
            json.dumps({"leaveCode": "ANNUAL_DEMO", "maxDurationMinutes": 1440}, ensure_ascii=False),
        )
        ensure_workflow_template(
            client,
            "CLOCK_CORRECTION",
            "补卡流程",
            "CLOCK_CORRECTION",
            "CLOCK_CORRECTION_MANAGER_APPROVAL",
            "补卡直属上级审批",
            "clock_correction_manager_approval",
            json.dumps({"allowBackfillDays": 30}, ensure_ascii=False),
        )

        employee1 = login_employee(client, "demo.employee1")
        leader = login_employee(client, "demo.leader")
        ensure_private_conversation(employee1, leader_user_id)
        ensure_workflow_demo(employee1, leader, leave_type_id)

        agent_id = ensure_agent(client, [employee1_user_id, employee2_user_id])
        ensure_agent_conversation(employee1, agent_id)

        log("演示数据引导完成。")
        log("已确保：demo 账号、假期余额、审批模板、私聊、请假待办、Agent 列表、IM-Agent 首轮回复。")
        return 0
    except Exception as exc:  # noqa: BLE001
        print(f"[bootstrap-demo-data] 失败: {exc}", file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    sys.exit(main())
