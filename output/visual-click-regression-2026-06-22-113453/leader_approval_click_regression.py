from pathlib import Path
from time import monotonic, sleep
from playwright.sync_api import sync_playwright, expect

OUT = Path(__file__).resolve().parent
REQUEST_ID = "2068900531993300993"
TODO_ID = "2068900533406781441"
REASON = "点击驱动全链路 113459"


def login_employee(page, login_name, password):
    page.goto("http://127.0.0.1:5174/#/messages", wait_until="networkidle")
    page.get_by_placeholder("员工登录名").fill(login_name)
    page.get_by_placeholder("密码").fill(password)
    page.get_by_role("button", name="进入工作区").click()
    expect(page.get_by_text("待办").first).to_be_visible(timeout=15000)


def wait_request_status(page, status):
    deadline = monotonic() + 20
    last = None
    while monotonic() < deadline:
        payload = page.evaluate(
            """async (requestId) => {
                const response = await fetch(`/api/employee/workflow/requests/${requestId}`, { credentials: 'include' });
                return await response.json();
            }""",
            REQUEST_ID,
        )
        last = payload.get("data", {}).get("status")
        if last == status:
            return payload
        sleep(0.5)
    raise AssertionError(f"request status expected {status}, got {last}")


with sync_playwright() as p:
    browser = p.chromium.launch(
        headless=True,
        executable_path="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    )
    leader_context = browser.new_context(viewport={"width": 1440, "height": 960})
    page = leader_context.new_page()

    login_employee(page, "demo.leader", "Employee@123456")
    todos_payload = page.evaluate(
        """async () => {
            const response = await fetch('/api/employee/workflow/todos', { credentials: 'include' });
            return await response.json();
        }"""
    )
    target_todo = next(item for item in todos_payload["data"] if item["summary"] == REASON)
    page.goto(f"http://127.0.0.1:5174/#/todos/workflow-todo-{target_todo['todoId']}", wait_until="networkidle")
    expect(page.get_by_role("button", name="审批通过").first).to_be_visible(timeout=15000)
    page.screenshot(path=OUT / "07-leader-todo-visible-before-click-approve.png", full_page=True)

    page.get_by_role("button", name="审批通过").first.click()
    expect(page.get_by_role("heading", name="审批通过")).to_be_visible(timeout=10000)
    page.get_by_placeholder("审批意见").fill("点击驱动审批通过")
    page.screenshot(path=OUT / "08-leader-approval-dialog-before-submit.png", full_page=True)
    page.get_by_role("button", name="提交").click()
    wait_request_status(page, "APPROVED")
    page.screenshot(path=OUT / "09-leader-after-click-approve.png", full_page=True)

    employee_context = browser.new_context(viewport={"width": 1440, "height": 960})
    page2 = employee_context.new_page()
    login_employee(page2, "demo.employee1", "Employee@123456")
    page2.goto("http://127.0.0.1:5174/#/workbench/requests", wait_until="networkidle")
    expect(page2.get_by_text(REASON).or_(page2.get_by_text("已通过")).or_(page2.get_by_text("APPROVED")).first).to_be_visible(timeout=20000)
    page2.screenshot(path=OUT / "10-employee-request-approved-after-click-flow.png", full_page=True)

    status = wait_request_status(page2, "APPROVED")
    (OUT / "result.json").write_text(
        __import__("json").dumps(
            {
                "requestId": REQUEST_ID,
                "todoId": str(target_todo["todoId"]),
                "leaderClickedApprove": True,
                "finalStatus": status.get("data", {}).get("status"),
                "apiEnvelopeCode": status.get("code"),
                "screenshots": [
                    "01-admin-leave-type-created.png",
                    "02-admin-leave-type-updated.png",
                    "03-admin-leave-type-deleted.png",
                    "04-employee-leave-before-click-submit.png",
                    "05-employee-leave-after-click-submit-pending-review.png",
                    "06-employee-request-after-agent-pre-review.png",
                    "07-leader-todo-visible-before-click-approve.png",
                    "08-leader-approval-dialog-before-submit.png",
                    "09-leader-after-click-approve.png",
                    "10-employee-request-approved-after-click-flow.png",
                ],
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    browser.close()
