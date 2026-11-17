from pathlib import Path
import collections
import hashlib
import json
import xml.etree.ElementTree as ET

root = Path.cwd()
dest = root / 'deliverables/99-2-20261108'
state = json.loads((dest / 'state.json').read_text())
lines = ['99-2 SESSION VALIDITY - VERIFICATION LEDGER',
    'Implementation finished. Targeted regression passed. Full environment acceptance is NOT passed.',
    'TARGET: ' + str(root), '',
    'Changed symbols:',
    'SessionValidityService.isCurrent; SessionValidityFilter.doFilterInternal; SecurityConfig.filterChain;',
    'EmployeeUserDetailsService.loadUserByUsername; ImWebSocketAuthentication.check;',
    'ImWebSocketHandshakeInterceptor.beforeHandshake; ImWebSocketHandler connection/message entry points;',
    'ImWebSocketSessionRegistry sendToUserOrThrow and closeInvalidSessions.', '',
    'Policy and observed assertions:',
    '- Each authenticated request reloads current identity/status/roles/permissions through existing shared-database mappers.',
    '- Identity/account ID, password, allowed employee lifecycle, active org/account state, roles and authorities must match.',
    '- HTTP stale identity -> 401 and Session invalidation; database unavailable -> 503 without business execution.',
    '- Disabled/offboarded users cannot log in again (403; locked 423). RBAC changes require re-login; reduced rights remain 403.',
    '- Grants also require re-login. Unchanged users and current authorized requests remain usable.',
    '- WS handshake, establishment, incoming messages and outbound events revalidate. Invalid sockets close with policy violation 1008.',
    '- Idle sockets are checked by a fixed-delay 5-second job; query/scheduler latency is additional.',
    '- No local invalidation registry or successful broadcast is required for another instance to reject stale identity.',
    '- Already executing requests and already sent frames are not retroactively rolled back.', '',
    'Evidence limits:',
    '- Real LoginService, BCrypt providers, full Spring Security chain, login controllers and verifiable HTTP Session are used.',
    '- Persistence mappers, email and login-rate storage are test doubles. Status changes use current mapper records.',
    '- Cross-instance design is exercised with TWO independent Spring contexts and serialized/transferred SecurityContext.',
    '- This is not a live multi-process deployment, real Redis Session transfer, or real MySQL revocation integration.',
    '- WS uses actual interceptor/handler/registry with a verifiable WebSocketSession double, not a live network socket.',
    '- Idle scan is invoked directly in the regression; no exact wall-clock 5-second claim is made.',
    '- Credentials and .env were not read. Application configuration files were omitted from disposable build snapshots.',
    '- Therefore credential-dependent integration and application.yml contract tests are explicitly unverified/failed.',
    '- Existing IM database tests have updated principal-bearing socket fixtures, but real database execution remains blocked.',
    '- Shared checkout has concurrent work. All scoped source hashes match the packaged snapshot; unrelated work is preserved.',
    '- No user application was started/stopped; no Git commit/push; no Feishu completion state was written.', '',
    'Parallel integration:',
    '- Reported the AdminPermissionWebTest conflict to the source chat before editing it.',
    '- Adapted three explicit MVC slices with the new filter and an always-current validity service test double.',
    '- Those slices retain their authorization assertions; real revocation semantics are covered by the dedicated full-chain test.',
    '- AdminPermissionFilter, OrgManagementService and RbacService were NOT edited by this task.', '',
    'Execution corrections (retained evidence):',
    '- apply_patch rejected duplicate operations on one file; no bytes written; retried as one file block.',
    '- Missing hello-plugin public sources and the security route matrix in initial disposable snapshots were supplied.',
    '- Explicit MVC slices missing the new filter were repaired; no production bypass was introduced.',
    '- One retry began before collecting the earlier full-run completion, overlapping full-log writes.',
    '  Only this task-owned Maven/test subprocess in the exact disposable directory was terminated (exit 143).',
    '  full-cancelled.* is invalid intermediate evidence, not an acceptance result.',
    '  Final full mvn test was then executed separately after both earlier dispatches completed.',
    '- A long non-ASCII inline report command failed with a Python input encoding SyntaxError (exit 1).',
    '  Replaced with this ASCII Python report generator; no source files were affected.', '',
    'Acceptance input:', str(dest / 'fixtures/SessionValidityRequestChainTest.java'),
    'The same 18-case regression input and Maven command are used for BASELINE, MODIFIED and ROLLBACK.',
    'Rollback removes all new source/test files first; only the external acceptance test is then injected for verification.',
    'The external test fixture does not change restored business code.', '', 'FOUR ROLE PATHS:']
for name in ['MODIFIED_FILE.tar', 'DIFF_FILE.patch', 'VERIFICATION.txt', 'ROLLBACK.sh']:
    lines.append(str(dest / name))
lines.extend(['', 'Original SHA-256:'])
lines.extend(p + ' ' + h for p, h in state['baseline_hashes'].items())
lines.extend(['', 'Modified SHA-256:'])
lines.extend(p + ' ' + h for p, h in state['modified_hashes'].items())
restored = json.loads((dest / 'rollback-restored-hashes.json').read_text())
assert restored == state['baseline_hashes']
lines.extend(['', 'Rollback restored all ' + str(len(restored)) + ' original hashes exactly.',
    'All ' + str(len(state['new_paths'])) + ' newly created files were removed before injecting the acceptance input.',
    'Patch reconstruction exactly matches every archived/live modified file.',
    'ROLLBACK.sh accepts an absolute project-copy root, embeds pristine source, and refuses concurrent unknown hashes.', ''])
for label, filename, cwd, command in [
    ('BASELINE', 'baseline-final', state['runs']['baseline-final'], state['command']),
    ('MODIFIED', 'modified', state['work'], state['command']),
    ('ROLLBACK', 'rollback', state['runs']['rollback'], state['command']),
    ('RELATED', 'related', state['work'], state['related_command']),
    ('FULL', 'full', state['full_work'], ['mvn', '-B', '-Dstyle.color=never', 'test'])]:
    lines.extend(['=== ' + label + ' ===', 'cwd: ' + cwd, 'command: ' + ' '.join(command),
        'exit: ' + (dest / (filename + '.exit')).read_text(),
        'literal stdout:', (dest / (filename + '.stdout')).read_text(),
        'literal stderr:', (dest / (filename + '.stderr')).read_text()])
for label, filename, command in [
    ('ROLLBACK SCRIPT', 'rollback-script', str(dest / 'ROLLBACK.sh') + ' <isolated-project-copy>'),
    ('PATCH RECONSTRUCTION', 'patch', 'patch -p1 --batch -i ' + str(dest / 'DIFF_FILE.patch'))]:
    lines.extend(['=== ' + label + ' ===', 'command: ' + command,
        'exit: ' + (dest / (filename + '.exit')).read_text(),
        'literal stdout:', (dest / (filename + '.stdout')).read_text(),
        'literal stderr:', (dest / (filename + '.stderr')).read_text()])
failures = []
causes = collections.Counter()
for report in (Path(state['full_work']) / 'target/surefire-reports').glob('TEST-*.xml'):
    for case in ET.parse(report).getroot().findall('testcase'):
        for kind in ['failure', 'error']:
            failure = case.find(kind)
            if failure is None:
                continue
            chain = [x.strip() for x in (failure.text or '').splitlines() if x.startswith('Caused by:')]
            cause = chain[-1] if chain else failure.attrib.get('message', '').split(' for [')[0][:200]
            failures.append({'test': case.attrib.get('classname', '') + '.' + case.attrib.get('name', ''),
                'kind': kind, 'cause': cause})
            causes[cause] += 1
(dest / 'full-failures.json').write_text(json.dumps({'causes': dict(causes), 'tests': failures},
    ensure_ascii=True, indent=2))
lines.extend(['=== FINAL FULL-RUN ERROR GROUPS ===', json.dumps(dict(causes), ensure_ascii=True, indent=2)])
(dest / 'VERIFICATION.txt').write_text('\n'.join(lines) + '\n')
for p, expected in state['modified_hashes'].items():
    assert hashlib.sha256((root / p).read_bytes()).hexdigest() == expected, 'Concurrent change: ' + p
for name in ['MODIFIED_FILE.tar', 'DIFF_FILE.patch', 'VERIFICATION.txt', 'ROLLBACK.sh']:
    content = (dest / name).read_bytes()
    print(str(dest / name), 'bytes=' + str(len(content)), 'sha256=' + hashlib.sha256(content).hexdigest())
