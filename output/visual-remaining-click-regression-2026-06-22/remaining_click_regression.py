import json
from datetime import datetime
from pathlib import Path
from time import sleep

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


def login_employee(page):
    page.goto("http://127.0.0.1:5174/#/files", wait_until="networkidle")
    page.get_by_placeholder("员工登录名").fill("demo.employee1")
    page.get_by_placeholder("密码").fill("Employee@123456")
    page.get_by_role("button", name="进入工作区").click()
    expect(page.get_by_text("文档").first).to_be_visible(timeout=15000)


def click_confirm(page):
    page.get_by_role("button", name="确定").or_(page.get_by_role("button", name="确认")).last.click()


stamp = datetime.now().strftime("%H%M%S")
for old_file in [*OUT.glob("*.png"), *OUT.glob("*.txt"), OUT / "result.json"]:
    if old_file.exists():
        old_file.unlink()
admin_upload = OUT / f"ragflow-admin-upload-{stamp}.txt"
employee_upload = OUT / f"employee-file-upload-{stamp}.txt"
admin_upload.write_text(f"ragflow visual upload {stamp}\n", encoding="utf-8")
employee_upload.write_text(f"employee visual upload {stamp}\n", encoding="utf-8")

with sync_playwright() as p:
    browser = p.chromium.launch(headless=True, executable_path=CHROME)
    result = {"checks": {}, "screenshots": []}

    admin_context = browser.new_context(viewport={"width": 1440, "height": 960})
    admin = admin_context.new_page()
    login_admin(admin)

    admin.goto("http://127.0.0.1:5173/#/ragflow", wait_until="networkidle")
    expect(admin.get_by_text("知识库").first).to_be_visible(timeout=15000)
    if admin.locator("tbody tr").count() == 0:
        admin.get_by_role("button", name="新建知识库").click()
        expect(admin.get_by_role("heading", name="新建知识库")).to_be_visible(timeout=10000)
        admin.get_by_placeholder("如 公司制度知识库").fill(f"可视化知识库{stamp}")
        admin.get_by_placeholder("如 COMPANY_POLICY").fill(f"VISUAL_KB_{stamp}")
        admin.locator("textarea").fill("可视化点击补测")
        admin.get_by_role("combobox").select_option("PUBLIC")
        admin.get_by_role("button", name="创建").click()
        expect(admin.locator("tbody tr").first).to_be_visible(timeout=15000)
    admin.locator("tbody tr").first.locator("button", has_text="进入").click()
    expect(admin.get_by_text("上传文件").first).to_be_visible(timeout=15000)
    shot(admin, "01-admin-ragflow-before-upload.png")
    admin.locator('input[type="file"]').set_input_files(str(admin_upload))
    expect(admin.get_by_text(admin_upload.name)).to_be_visible(timeout=10000)
    with admin.expect_response(lambda r: "/api/admin/files/confirm-upload" in r.url and r.request.method == "POST") as admin_upload_response:
        admin.get_by_role("button", name="上传文件").click()
    assert admin_upload_response.value.json()["code"] == 0
    expect(admin.get_by_text(admin_upload.name)).to_be_visible(timeout=20000)
    shot(admin, "02-admin-ragflow-after-upload.png")
    result["checks"]["adminBinaryUploadViaRagflow"] = True

    row = admin.locator("tbody tr", has_text=admin_upload.name).first
    with admin.expect_response(lambda r: "/reingest" in r.url and r.request.method == "POST") as reingest_response:
        row.get_by_label("重新入库").click()
    assert reingest_response.value.json()["code"] == 0
    sleep(1)
    shot(admin, "03-admin-ragflow-after-reingest-click.png")
    result["checks"]["adminRagflowReingest"] = True

    admin.once("dialog", lambda dialog: dialog.accept())
    with admin.expect_response(lambda r: "/api/admin/ragflow/spaces/" in r.url and "/documents/" in r.url and r.request.method == "DELETE") as delete_response:
        row.get_by_label("删除文件").click()
    assert delete_response.value.json()["code"] == 0
    expect(admin.get_by_text(admin_upload.name)).not_to_be_visible(timeout=15000)
    shot(admin, "04-admin-ragflow-after-delete-click.png")
    result["checks"]["adminRagflowDelete"] = True

    draft_version = admin.evaluate(
        """async () => {
            const templatesResponse = await fetch('/api/admin/workflow/templates?processCode=LEAVE', { credentials: 'include' });
            const templatesPayload = await templatesResponse.json();
            const template = templatesPayload.data[0];
            const versionsResponse = await fetch(`/api/admin/workflow/templates/${template.templateId}/versions`, { credentials: 'include' });
            const versionsPayload = await versionsResponse.json();
            const nextVersionNo = Math.max(0, ...versionsPayload.data.map((item) => item.versionNo)) + 1;
            const createResponse = await fetch(`/api/admin/workflow/templates/${template.templateId}/versions`, {
                method: 'POST',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    versionNo: nextVersionNo,
                    formSchemaJson: '{}',
                    ruleSnapshotJson: JSON.stringify({ visual: true, stamp: String(Date.now()) })
                })
            });
            const createPayload = await createResponse.json();
            if (createPayload.code !== 0) throw new Error(createPayload.message || 'create draft failed');
            return {
                templateName: template.templateName,
                versionNo: nextVersionNo,
                versionId: createPayload.data.versionId
            };
        }"""
    )
    admin.goto("http://127.0.0.1:5173/#/workflow", wait_until="networkidle")
    expect(admin.get_by_text("模板版本").first).to_be_visible(timeout=15000)
    admin.locator("article", has_text=draft_version["templateName"]).first.click()
    row = admin.locator("article", has_text=f"v{draft_version['versionNo']} · 草稿").first
    expect(row.get_by_role("button", name="发布")).to_be_visible(timeout=15000)
    shot(admin, "05-admin-workflow-template-publish-before-click.png")
    with admin.expect_response(lambda r: f"/api/admin/workflow/template-versions/{draft_version['versionId']}/publish" in r.url and r.request.method == "POST") as publish_response:
        row.get_by_role("button", name="发布").click()
    assert publish_response.value.json()["code"] == 0
    expect(admin.locator("article", has_text=f"v{draft_version['versionNo']} · 已发布")).to_be_visible(timeout=15000)
    shot(admin, "06-admin-workflow-template-publish-after-click.png")
    result["checks"]["adminWorkflowTemplatePublishClick"] = True

    employee_context = browser.new_context(viewport={"width": 1440, "height": 960}, accept_downloads=True)
    employee = employee_context.new_page()
    login_employee(employee)
    shot(employee, "07-employee-files-before-upload.png")
    employee.locator('input[type="file"]').set_input_files(str(employee_upload))
    expect(employee.get_by_text(employee_upload.name)).to_be_visible(timeout=10000)
    employee.locator(".upload-dialog").get_by_role("button", name="上传").click()
    expect(employee.get_by_text("上传成功").or_(employee.get_by_text(employee_upload.name))).to_be_visible(timeout=20000)
    employee.locator(".upload-dialog").get_by_role("button", name="取消").click()
    expect(employee.locator(".upload-dialog")).not_to_be_visible(timeout=10000)
    shot(employee, "08-employee-files-after-upload.png")
    result["checks"]["employeeBinaryUpload"] = True

    file_row = employee.locator(".file-row", has_text=employee_upload.name).first
    file_row.get_by_role("button", name="预览").click()
    expect(employee.get_by_text(employee_upload.name).first).to_be_visible(timeout=15000)
    shot(employee, "09-employee-file-preview-clicked.png")
    result["checks"]["employeeFilePreview"] = True
    employee.locator(".preview-dialog").get_by_label("关闭").click()
    expect(employee.locator(".preview-dialog")).not_to_be_visible(timeout=10000)

    with employee.expect_download(timeout=15000) as download_event:
        file_row.get_by_role("button", name="下载").click()
    download_event.value.path()
    shot(employee, "10-employee-file-download-clicked.png")
    result["checks"]["employeeFileDownload"] = True

    file_row.get_by_role("button", name="删除").click()
    expect(employee.get_by_text(employee_upload.name)).not_to_be_visible(timeout=15000)
    employee.get_by_role("button", name="回收站").click()
    expect(employee.get_by_text(employee_upload.name)).to_be_visible(timeout=15000)
    shot(employee, "11-employee-file-delete-clicked.png")
    result["checks"]["employeeFileDelete"] = True

    result["screenshots"] = sorted(path.name for path in OUT.glob("*.png"))
    (OUT / "result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    browser.close()
