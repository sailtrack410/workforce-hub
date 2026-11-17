const { chromium } = require('/Users/sailtrack/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const crypto = require('node:crypto');

(async () => {
  const root = __dirname;
  const data = JSON.parse(fs.readFileSync(path.join(root, 'manifest.json'), 'utf8'));
  const result = { artifact: path.join(root, 'index.html'), browser: 'Microsoft Edge / Playwright', checks: [], errors: [], businessApplicationStarted: false };
  const browser = await chromium.launch({ headless: true, executablePath: '/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge', args: ['--allow-file-access-from-files'] });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  const check = (name, actual, expected) => { const ok = actual === expected; result.checks.push({ name, actual, expected, ok }); if (!ok) result.errors.push(name); };
  page.on('pageerror', err => result.errors.push(err.message));
  try {
    await page.goto(pathToFileURL(result.artifact).href);
    await page.waitForFunction(() => document.querySelector('#diagram').contentDocument?.querySelector('svg'));
    check('all-chain-buttons', await page.locator('[data-chain]').count(), data.chains.length);
    check('initial-iframe-svg', await page.locator('#diagram').evaluate(e => !!e.contentDocument.querySelector('svg')), true);
    await page.screenshot({ path: path.join(root, '总览-桌面.png') });
    await page.locator('[data-group="auth-org"]').click();
    check('group-filter', await page.locator('[data-chain]').count(), 9);
    await page.locator('#search').fill('找回');
    check('text-search', await page.locator('[data-chain]').count(), 1);
    await page.locator('#search').fill('');
    await page.locator('[data-group="all"]').click();
    await page.locator('[data-chain="im-delivery"]').click();
    await page.waitForFunction(() => document.querySelector('#diagram').contentDocument?.querySelector('svg') && document.querySelector('#diagram').src.includes('im-delivery.html'));
    check('chain-switch', await page.locator('#diagram').getAttribute('src'), 'im-agent-rag/im-delivery.html');
    await page.locator('[data-finding="IAR-09"]').click();
    check('finding-deeplink', await page.locator('#finding-IAR-09').getAttribute('open'), '');
    await page.locator('[data-issue-filter="P1"]').click();
    check('new-p1-filter', await page.locator('details.finding').count(), data.counts.p1);
    await page.screenshot({ path: path.join(root, '问题-桌面.png'), fullPage: true });
    await page.locator('[data-tab="coverage"]').click();
    check('module-coverage-rows', await page.locator('#coverage-body tr').count(), data.coverage.length);
    await page.locator('[data-cover="W01"]').first().click();
    check('coverage-to-diagram', await page.locator('#diagram').getAttribute('src'), 'file-plugin/W01.html');
    for (const [width, height] of [[1440, 900], [1920, 1080], [390, 844]]) {
      await page.setViewportSize({ width, height });
      check(`page-containment-${width}`, await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    }
    await page.screenshot({ path: path.join(root, '总览-窄屏.png'), fullPage: true });
    await page.setViewportSize({ width: 1440, height: 1000 });
    await page.locator('[data-chain="wf-leave-ai"]').click();
    await page.waitForFunction(() => document.querySelector('#diagram').contentDocument?.querySelector('svg') && document.querySelector('#diagram').src.includes('wf-leave-ai.html'));
    check('workflow-frame-load', await page.locator('#diagram').evaluate(e => !!e.contentDocument.querySelector('svg')), true);
    await page.screenshot({ path: path.join(root, '请假链路-总览.png') });
  } catch (error) { result.errors.push(error.stack); }
  finally { await browser.close(); }
  result.indexSha256 = crypto.createHash('sha256').update(fs.readFileSync(result.artifact)).digest('hex');
  result.ok = result.errors.length === 0;
  result.summary = `Overview checks: ${result.checks.filter(c => c.ok).length} passed, ${result.errors.length} failed`;
  fs.writeFileSync(path.join(root, '入口验证.json'), JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify(result));
  process.exitCode = result.ok ? 0 : 1;
})();
