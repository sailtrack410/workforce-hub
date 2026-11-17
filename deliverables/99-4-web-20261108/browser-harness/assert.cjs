async (page) => {
  const result = await page.evaluate(() => ({
    startAt: document.querySelector('#startAt').value,
    endAt: document.querySelector('#endAt').value,
    duration: document.querySelector('#duration').textContent,
    employeeRest: document.querySelector('#employee-rest').textContent,
    adminRest: document.querySelector('#admin-rest').textContent,
    request: JSON.parse(document.querySelector('#request').textContent),
    error: document.querySelector('#error').textContent,
  }));
  if (result.startAt !== '2026-05-07T09:00' || result.endAt !== '2026-05-07T12:00'
    || result.duration !== '180 分钟' || result.employeeRest !== '休息' || result.adminRest !== '休息'
    || result.request.startAt !== '2026-05-07T09:00:00' || result.request.endAt !== '2026-05-07T12:00:00'
    || result.error !== '') throw new Error(JSON.stringify(result));
  return { passed: true, controlledApiHarness: true, realBackendEndToEnd: false, ...result };
}
