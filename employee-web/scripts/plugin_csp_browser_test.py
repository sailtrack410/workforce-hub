#!/usr/bin/env python3
import argparse
import base64
import hashlib
import json
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import urlopen

from playwright.sync_api import Page, Route, sync_playwright


BASELINE_CSP = "script-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'"


def parse_args():
    workspace = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description='Verify employee plugin loading under CSP in Chromium.')
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--screenshot', type=Path, default=workspace / 'plugin-csp-employee.png')
    parser.add_argument('--blocked-screenshot', type=Path, default=workspace / 'plugin-csp-blocked-employee.png')
    parser.add_argument('--chromium-executable', type=Path)
    return parser.parse_args()


def api_data(path: str, integrity: str):
    if path == '/api/employee/auth/me':
        return {
            'userId': '1',
            'userType': 'EMPLOYEE',
            'displayName': 'CSP 验收用户',
            'loginName': 'csp.employee',
            'email': 'csp@example.invalid',
        }
    if path == '/api/employee/me/profile':
        return {
            'userId': '1',
            'employeeNo': 'CSP-001',
            'realName': 'CSP 验收用户',
            'primaryDeptName': '安全验证',
            'positionName': '测试岗位',
        }
    if path == '/api/employee/me/preferences':
        return {
            'theme': 'light',
            'messageDensity': 'cozy',
            'listWidth': 320,
            'signature': '',
            'presenceStatus': 'ONLINE',
            'notifSound': False,
            'notifDesktop': False,
            'notifMentionOnly': False,
            'notifUnreadBadge': True,
            'accentColor': 'indigo',
            'fontScale': 'medium',
            'bubbleStyle': 'rounded',
        }
    if path == '/api/employee/plugins/mine':
        return [{
            'id': 'hello-plugin',
            'name': 'Hello Plugin',
            'frontendEntry': '/plugins/hello-plugin/index.js',
            'integrity': integrity,
        }]
    if path == '/api/employee/files/quota':
        return {
            'personalQuotaBytes': 1048576,
            'personalUsedBytes': 0,
            'personalUsagePercent': 0,
            'uploadWritable': True,
        }
    if path in {'/api/employee/files', '/api/employee/files/recycle-bin'}:
        return {'list': [], 'total': 0, 'pageNo': 1, 'pageSize': 100}
    if path == '/api/employee/workflow/requests':
        return {'list': [], 'total': 0, 'pageNo': 1, 'pageSize': 20}
    if path == '/api/employee/me/manager':
        return None
    if path in {
        '/api/employee/me/subordinates',
        '/api/employee/me/colleagues',
        '/api/employee/contacts/friends',
        '/api/employee/contacts/dept-tree',
        '/api/employee/im/conversations',
        '/api/employee/leave/accounts',
        '/api/employee/leave/types',
        '/api/employee/attendance/daily-results',
        '/api/employee/attendance/exceptions',
    }:
        return []
    raise KeyError(path)


def install_api_mocks(page: Page, integrity: str, unexpected: list[str]):
    def handle(route: Route):
        path = urlparse(route.request.url).path
        try:
            data = api_data(path, integrity)
        except KeyError:
            unexpected.append(path)
            data = []
        body = json.dumps({'code': 0, 'message': 'OK', 'data': data}, ensure_ascii=False)
        route.fulfill(status=200, content_type='application/json; charset=utf-8', body=body)

    page.route('**/api/**', handle)


def install_violation_recorder(page: Page):
    page.add_init_script(
        """
        window.__CSP_VIOLATIONS__ = [];
        document.addEventListener('securitypolicyviolation', (event) => {
          window.__CSP_VIOLATIONS__.push({
            blockedURI: event.blockedURI,
            effectiveDirective: event.effectiveDirective,
            violatedDirective: event.violatedDirective,
          });
        });
        """
    )


def violations(page: Page):
    return page.evaluate('window.__CSP_VIOLATIONS__')


def main():
    args = parse_args()
    base_url = args.base_url.rstrip('/')
    parsed = urlparse(base_url)
    if parsed.scheme not in {'http', 'https'} or not parsed.netloc or parsed.path not in {'', '/'}:
        raise SystemExit('--base-url must be an HTTP(S) origin without a path')
    origin = f'{parsed.scheme}://{parsed.netloc}'
    root_url = f'{origin}/'

    workspace = Path(__file__).resolve().parents[2]
    manifest = json.loads(
        (workspace / 'plugins/hello-plugin/src/main/resources/plugin.json').read_text(encoding='utf-8')
    )
    with urlopen(f'{root_url}plugins/hello-plugin/index.js', timeout=10) as response:
        if response.status != 200:
            raise AssertionError(f'plugin preflight returned HTTP {response.status}')
        plugin_bytes = response.read()
    integrity = 'sha384-' + base64.b64encode(hashlib.sha384(plugin_bytes).digest()).decode('ascii')
    if manifest['frontendSha384'] != integrity:
        raise AssertionError('served plugin digest does not match the manifest source file')
    args.screenshot.parent.mkdir(parents=True, exist_ok=True)
    args.blocked_screenshot.parent.mkdir(parents=True, exist_ok=True)

    with sync_playwright() as playwright:
        if args.chromium_executable and not args.chromium_executable.is_file():
            raise SystemExit(f'Chromium executable not found: {args.chromium_executable}')
        browser = playwright.chromium.launch(
            headless=True,
            executable_path=str(args.chromium_executable) if args.chromium_executable else None,
        )

        positive_context = browser.new_context(viewport={'width': 1440, 'height': 1000})
        positive = positive_context.new_page()
        positive_unexpected = []
        positive_page_errors = []
        install_api_mocks(positive, integrity, positive_unexpected)
        install_violation_recorder(positive)
        positive.on('pageerror', lambda error: positive_page_errors.append(str(error)))
        plugin_statuses = []
        positive.on(
            'response',
            lambda response: plugin_statuses.append(response.status)
            if urlparse(response.url).path == '/plugins/hello-plugin/index.js'
            else None,
        )
        document_response = positive.goto(
            f'{root_url}#/workbench/hello-plugin',
            wait_until='domcontentloaded',
        )
        positive.wait_for_load_state('networkidle')
        positive.locator('main.employee-shell').wait_for(state='visible')
        positive.locator('.main-pane h2.workspace-title').filter(has_text='Hello Plugin').wait_for(state='visible')
        positive.locator('.hello-plugin').wait_for(state='visible')
        positive.screenshot(path=str(args.screenshot), full_page=True)

        if document_response is None:
            raise AssertionError('main document response is missing')
        if document_response.headers.get('content-security-policy') != BASELINE_CSP:
            raise AssertionError('main document CSP does not match the baseline')
        if plugin_statuses != [200]:
            raise AssertionError(f'plugin response statuses are {plugin_statuses!r}, expected [200]')
        script = positive.locator("script[src='/plugins/hello-plugin/index.js']")
        if script.count() != 1:
            raise AssertionError(f'plugin script count is {script.count()}, expected 1')
        if script.get_attribute('integrity') != integrity:
            raise AssertionError('plugin script integrity does not match the manifest source file')
        if positive.locator('.hello-plugin').text_content().strip() != 'Hello Plugin':
            raise AssertionError('plugin content did not render as expected')
        if violations(positive):
            raise AssertionError(f'positive page emitted CSP violations: {violations(positive)!r}')
        if positive_page_errors:
            raise AssertionError(f'positive page emitted page errors: {positive_page_errors!r}')
        if positive_unexpected:
            raise AssertionError(f'positive page called unexpected APIs: {positive_unexpected!r}')
        positive_context.close()

        blocked_context = browser.new_context(viewport={'width': 1440, 'height': 1000})
        blocked = blocked_context.new_page()
        blocked_unexpected = []
        blocked_page_errors = []
        install_api_mocks(blocked, integrity, blocked_unexpected)
        install_violation_recorder(blocked)
        blocked.on('pageerror', lambda error: blocked_page_errors.append(str(error)))

        blocked.route(
            f'{root_url}plugins/hello-plugin/index.js',
            lambda route: route.fulfill(
                status=302,
                headers={'Location': 'https://csp-blocked.invalid/hello-plugin.js'},
            ),
        )
        blocked_document_response = blocked.goto(
            f'{root_url}#/workbench/hello-plugin',
            wait_until='domcontentloaded',
        )
        blocked.wait_for_load_state('networkidle')
        try:
            blocked.locator('main.employee-shell').wait_for(state='visible')
            blocked.locator('.main-pane h2.workspace-title').filter(has_text='OA 申请').wait_for(state='visible')
        except Exception as error:
            raise AssertionError(
                f'blocked host did not remain available; url={blocked.url}, '
                f'violations={violations(blocked)!r}, pageErrors={blocked_page_errors!r}'
            ) from error
        blocked.screenshot(path=str(args.blocked_screenshot), full_page=True)

        blocked_violations = violations(blocked)
        if blocked_document_response is None:
            raise AssertionError('blocked main document response is missing')
        if blocked_document_response.headers.get('content-security-policy') != BASELINE_CSP:
            raise AssertionError('blocked main document CSP does not match the baseline')
        blocked_matches = [
            item for item in blocked_violations
            if (
                '/plugins/hello-plugin/index.js' in item['blockedURI']
                or 'csp-blocked.invalid' in item['blockedURI']
            )
        ]
        if not blocked_matches:
            raise AssertionError(f'plugin CSP violation was not captured: {blocked_violations!r}')
        if any(item['effectiveDirective'] != 'script-src-elem' for item in blocked_matches):
            raise AssertionError(f'plugin was blocked by an unexpected directive: {blocked_matches!r}')
        if blocked.locator('.hello-plugin').count() != 0:
            raise AssertionError('blocked plugin unexpectedly rendered')
        if urlparse(blocked.url).fragment != '/workbench/oa':
            raise AssertionError(f'blocked plugin did not fall back to OA: {blocked.url}')
        if blocked.evaluate("Boolean(window.__WORKFORCE_HOST__?.getPlugin('hello-plugin'))"):
            raise AssertionError('blocked plugin unexpectedly registered in the host SDK')
        if blocked_page_errors:
            raise AssertionError(f'blocked page emitted page errors: {blocked_page_errors!r}')
        if blocked_unexpected:
            raise AssertionError(f'blocked page called unexpected APIs: {blocked_unexpected!r}')
        blocked_context.close()

        admin_context = browser.new_context(viewport={'width': 1440, 'height': 1000})
        admin = admin_context.new_page()
        admin_page_errors = []
        install_violation_recorder(admin)
        admin.on('pageerror', lambda error: admin_page_errors.append(str(error)))
        admin.route(
            '**/api/admin/auth/me',
            lambda route: route.fulfill(
                status=401,
                content_type='application/json; charset=utf-8',
                body=json.dumps({'code': 401, 'message': 'Unauthorized', 'data': None}),
            ),
        )
        admin_document_response = admin.goto(f'{root_url}admin/#/login', wait_until='domcontentloaded')
        admin.wait_for_load_state('networkidle')
        admin.locator('main.login-page h1').filter(has_text='管理端登录').wait_for(state='visible')
        if admin_document_response is None:
            raise AssertionError('admin document response is missing')
        if admin_document_response.headers.get('content-security-policy') != BASELINE_CSP:
            raise AssertionError('admin document CSP does not match the baseline')
        if violations(admin):
            raise AssertionError(f'admin page emitted CSP violations: {violations(admin)!r}')
        if admin_page_errors:
            raise AssertionError(f'admin page emitted page errors: {admin_page_errors!r}')
        admin_context.close()
        browser.close()

    print('CSP browser tests: 3 passed')
    print('Positive: plugin HTTP 200, SRI matched, 0 CSP violations')
    print('Negative: plugin blocked, violation captured, host fell back to OA')
    print('Admin: login page loaded with the baseline CSP and 0 violations')


if __name__ == '__main__':
    main()
