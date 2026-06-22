import json
from datetime import datetime, timedelta
from pathlib import Path
from time import monotonic, sleep

from playwright.sync_api import expect, sync_playwright

OUT = Path(__file__).resolve().parent
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"


def shot(page, name):
    page.screenshot(path=OUT / name, full_page=True)


def login_admin(page):
    page.goto("http://127.0.0.1:5173/#/login", wait_until="networkidle")
    page.locator('input[autocomplete="current-password"]').fill("Admin@123456")
    page.get_by_role("button", name="登录").click()
    expect(page.get_by_text("概览").first).to_be_visible(timeout=15000)


def login_employee(page, login_name):
    page.goto("http://127.0.0.1:5174/#/messages", wait_until="networkidle")
    page.get_by_placeholder("员工登录名").fill(login_name)
    page.get_by_placeholder("密码").fill("Employee@123456")
    page.get_by_role("button", name="进入工作区").click()
    expect(page.get_by_text("待办").first).to_be_visible(timeout=15000)


def wait_status(page, request_id, status):
    deadline = monotonic() + 30
    last = None
    while monotonic() < deadline:
        payload = page.evaluate(
            """async (requestId) => {
                const response = await fetch(`/api/employee/workflow/requests/${requestId}`, { credentials: 'include' });
                return await response.json();
            }""",
            str(request_id),
        )
        last = payload.get("data", {}).get("status")
        if last == status:
            return payload
        sleep(0.5)
    raise AssertionError(f"request {request_id} expected {status}, got {last}")


def approve_latest_todo_for_reason(page, reason):
    todos = page.evaluate(
        """async () => {
            const response = await fetch('/api/employee/workflow/todos', { credentials: 'include' });
            return await response.json();
        }"""
    )
    target = next(item for item in todos["data"] if item["summary"] == reason)
    page.goto(f"http://127.0.0.1:5174/#/todos/workflow-todo-{target['todoId']}", wait_until="networkidle")
    expect(page.get_by_text(reason).or_(page.get_by_role("button", name="审批通过")).first).to_be_visible(timeout=15000)
    shot(page, "16-leader-clock-todo-before-approve.png")
    page.get_by_role("button", name="审批通过").first.click()
    expect(page.get_by_role("heading", name="审批通过")).to_be_visible(timeout=10000)
    page.get_by_placeholder("审批意见").fill("点击补测审批通过")
    shot(page, "17-leader-clock-approve-dialog.png")
    page.get_by_role("button", name="提交").click()
    wait_status(page, target["requestId"], "APPROVED")
    shot(page, "18-leader-clock-after-approve.png")
    return target


with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, executable_path=CHROME)
    result = {"screenshots": [], "checks": {}}

    admin = browser.new_context(viewport={"width": 1440, "height": 960}).new_page()
    login_admin(admin)

    admin.goto("http://127.0.0.1:5173/#/leave", wait_until="networkidle")
    expect(admin.get_by_text("余额调整")).to_be_visible(timeout=15000)
    admin.locator("tbody tr").first.click()
    admin.locator('input[type="number"]').fill("1")
    admin.locator("form.stack-form input").nth(2).fill(f"visual-adjust-{datetime.now().strftime('%H%M%S')}")
    admin.locator("textarea").fill("点击补测余额调整")
    shot(admin, "01-admin-leave-adjustment-before-submit.png")
    admin.get_by_role("button", name="提交调整").click()
    expect(admin.locator(".confirm-dialog").get_by_role("button", name="提交调整")).to_be_visible(timeout=10000)
    with admin.expect_response(lambda r: "/api/admin/leave/accounts/adjustments" in r.url and r.request.method == "POST") as adjust_response:
        admin.locator(".confirm-dialog").get_by_role("button", name="提交调整").click()
    adjust_payload = adjust_response.value.json()
    assert adjust_payload["code"] == 0, adjust_payload
    shot(admin, "02-admin-leave-adjustment-after-submit.png")
    result["checks"]["adminLeaveAdjustment"] = True

    stamp = datetime.now().strftime("%H%M%S")
    admin.goto("http://127.0.0.1:5173/#/departments", wait_until="networkidle")
    admin.get_by_placeholder("部门编码").fill(f"QA_DEP_{stamp}")
    admin.get_by_placeholder("部门名称").fill(f"可视化部门{stamp}")
    shot(admin, "03-admin-department-before-create.png")
    admin.get_by_role("button", name="新建").click()
    expect(admin.locator("strong", has_text=f"可视化部门{stamp}").first).to_be_visible(timeout=15000)
    admin.locator("strong", has_text=f"可视化部门{stamp}").first.click()
    admin.get_by_role("button", name="保存修改").click()
    shot(admin, "04-admin-department-created-and-save-clicked.png")
    result["checks"]["adminDepartmentCreateUpdate"] = True

    admin.goto("http://127.0.0.1:5173/#/positions", wait_until="networkidle")
    admin.get_by_placeholder("岗位编码").fill(f"QA_POS_{stamp}")
    admin.get_by_placeholder("岗位名称").fill(f"可视化岗位{stamp}")
    shot(admin, "05-admin-position-before-create.png")
    admin.get_by_role("button", name="新建").click()
    expect(admin.locator("td", has_text=f"可视化岗位{stamp}").first).to_be_visible(timeout=15000)
    admin.locator("td", has_text=f"可视化岗位{stamp}").first.click()
    admin.get_by_role("button", name="保存修改").click()
    shot(admin, "06-admin-position-created-and-save-clicked.png")
    result["checks"]["adminPositionCreateUpdate"] = True

    admin.goto("http://127.0.0.1:5173/#/users", wait_until="networkidle")
    admin.get_by_text("新建员工").click()
    admin.locator("details.create-section input").nth(0).fill(f"QA{stamp}")
    admin.locator("details.create-section input").nth(1).fill(f"可视化员工{stamp}")
    admin.locator("details.create-section select").nth(0).select_option(index=1)
    admin.locator("details.create-section select").nth(1).select_option(index=1)
    shot(admin, "07-admin-user-create-form-filled.png")
    with admin.expect_response(lambda r: "/api/admin/users" in r.url and r.request.method == "POST") as create_user_response:
        admin.locator("details.create-section").get_by_role("button", name="创建").click()
    create_user_payload = create_user_response.value.json()
    assert create_user_payload["code"] == 0, create_user_payload
    expect(admin.locator("td", has_text=f"可视化员工{stamp}").first).to_be_visible(timeout=15000)
    shot(admin, "08-admin-user-created.png")
    result["checks"]["adminUserCreate"] = True

    admin.goto("http://127.0.0.1:5173/#/agent-tasks", wait_until="networkidle")
    expect(admin.get_by_text("超时扫描")).to_be_visible(timeout=15000)
    shot(admin, "09-admin-agent-timeout-scan-before.png")
    admin.get_by_role("button", name="执行扫描").click()
    expect(admin.get_by_role("button", name="执行").last).to_be_visible(timeout=10000)
    admin.get_by_role("button", name="执行").last.click()
    expect(admin.get_by_text("扫描", exact=False).first).to_be_visible(timeout=20000)
    shot(admin, "10-admin-agent-timeout-scan-after.png")
    result["checks"]["adminAgentTimeoutScan"] = True

    admin.goto("http://127.0.0.1:5173/#/files", wait_until="networkidle")
    expect(admin.get_by_text("清理任务")).to_be_visible(timeout=15000)
    shot(admin, "11-admin-file-dryrun-before.png")
    admin.get_by_role("button", name="执行 Dry-run").click()
    expect(admin.get_by_role("button", name="执行 Dry-run").last).to_be_visible(timeout=10000)
    admin.get_by_role("button", name="执行 Dry-run").last.click()
    expect(admin.get_by_text("任务").first).to_be_visible(timeout=20000)
    shot(admin, "12-admin-file-dryrun-after.png")
    result["checks"]["adminFileDryRun"] = True

    admin.goto("http://127.0.0.1:5173/#/system", wait_until="networkidle")
    expect(admin.get_by_text("审计日志")).to_be_visible(timeout=15000)
    admin.get_by_placeholder("动作类型").fill("LOGIN")
    shot(admin, "13-admin-system-filter-before-query.png")
    admin.get_by_role("button", name="查询").click()
    expect(admin.get_by_text("审计日志")).to_be_visible(timeout=15000)
    shot(admin, "14-admin-system-filter-after-query.png")
    result["checks"]["adminSystemAuditFilter"] = True

    employee_context = browser.new_context(viewport={"width": 1440, "height": 960})
    employee = employee_context.new_page()
    login_employee(employee, "demo.employee1")
    employee.goto("http://127.0.0.1:5174/#/workbench/attendance", wait_until="networkidle")
    expect(employee.get_by_text("补卡申请")).to_be_visible(timeout=15000)
    now = datetime.now()
    reason = f"点击补测补卡 {stamp}"
    employee.locator('input[type="date"]').fill((now - timedelta(days=1)).strftime("%Y-%m-%d"))
    employee.locator('input[type="datetime-local"]').fill((now - timedelta(days=1)).strftime("%Y-%m-%dT09:00"))
    employee.locator("textarea").fill(reason)
    shot(employee, "15-employee-clock-correction-before-submit.png")
    employee.get_by_role("button", name="提交补卡").click()
    expect(employee.get_by_text("申请编号：")).to_be_visible(timeout=20000)
    shot(employee, "15b-employee-clock-correction-after-submit.png")
    clock_payload = employee.evaluate(
        """async () => {
            const response = await fetch('/api/employee/workflow/requests?pageNo=1&pageSize=10', { credentials: 'include' });
            return await response.json();
        }"""
    )
    clock_request = next(item for item in clock_payload["data"]["list"] if item["title"] == "演示员工一的补卡申请")

    leader_context = browser.new_context(viewport={"width": 1440, "height": 960})
    leader = leader_context.new_page()
    login_employee(leader, "demo.leader")
    approve_latest_todo_for_reason(leader, reason)

    employee.goto("http://127.0.0.1:5174/#/workbench/requests", wait_until="networkidle")
    wait_status(employee, clock_request["requestId"], "APPROVED")
    expect(employee.get_by_text("已通过").first).to_be_visible(timeout=15000)
    shot(employee, "19-employee-clock-request-approved.png")
    result["checks"]["employeeClockCorrectionFullChain"] = True

    employee.goto("http://127.0.0.1:5174/#/settings/preferences", wait_until="networkidle")
    expect(employee.get_by_text("显示设置")).to_be_visible(timeout=15000)
    employee.get_by_role("button", name="切换到深色").or_(employee.get_by_role("button", name="切换到浅色")).click()
    employee.get_by_role("button", name="紧凑").click()
    shot(employee, "20-employee-preferences-clicked.png")
    result["checks"]["employeePreferences"] = True

    result["screenshots"] = sorted(path.name for path in OUT.glob("*.png"))
    (OUT / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    browser.close()
