from pathlib import Path
import hashlib
import json
import tarfile
import xml.etree.ElementTree as ET

root = Path.cwd()
dest = root / 'deliverables/99-2-20261108'
state = json.loads((dest / 'state.json').read_text())
runtime = json.loads((dest / 'integration-runtime.json').read_text())
assert runtime['owned_fixture_stopped']
lines = ['', '=== LIVE MYSQL / REDIS FOLLOW-UP - CURRENT EVIDENCE SUPERSEDES EARLIER ENVIRONMENT LIMITS ===',
    'Scope: adapt only 5 integration test files to the already implemented 99-2 validity contract.',
    'No production source was changed in this follow-up, and no session validity bypass/mock was introduced in database integration.',
    'Changes:',
    '- Attendance/IM offboarding assertions now require HTTP401/code100401 and retain DB no-punch/no-message/no-sequence-advance assertions.',
    '- Revoked/inactive plugin permission and disabled admin require HTTP401, then repeated use remains401; action handler never executes.',
    '- Permission revocation permits real re-login but the new session still gets403 on the plugin action.',
    '- Disabled admin cannot log in again (403).',
    '- Deleted/disabled/offboarded employee cases each log in with a valid DB identity first and see6 plugins, then get401/no data after invalidation.',
    '- HTTP plugin lifecycle/upload fixture now loads the actual current admin through AdminUserDetailsService (DB identity/password/roles/permissions).',
    '- Existing plugin lifecycle, dependency, signature rejection, unload and recovery assertions remain active.',
    '', 'Fixture boundary:',
    'New task-owned MySQL datadir and Redis instance on dynamic loopback ports; only bundled binaries reused.',
    'Generated disposable fixture login only. No real .env, credentials, user application or user database was accessed.',
    'Source + tracked resource templates + public seed/plugin fixtures were copied to a disposable snapshot; .env was excluded.',
    'Tests use actual MySQL/Redis and actual Spring HTTP/Security/Session chain; live network WebSocket and multi-process deployment remain unverified.',
    'Source-review feedback BASELINE is the pre-compatibility 99-2 state; original transaction BASELINE is the state before the production fix.',
    'These are different, explicitly labeled checkpoints. Original 18-case revocation input remains unchanged.', '',
    'Current fixture runtime metadata: ' + str(dest / 'integration-runtime.json'),
    'Current snapshot root: ' + runtime['snapshot'],
    'Owned fixture cleanup confirmed both ports closed.', '', 'Current role paths:']
for role in ['MODIFIED_FILE.tar', 'DIFF_FILE.patch', 'VERIFICATION.txt', 'ROLLBACK.sh']:
    lines.append(str(dest / role))
lines.extend(['', 'Current source hashes:'])
for path, expected in state['modified_hashes'].items():
    assert hashlib.sha256((root / path).read_bytes()).hexdigest() == expected, path
    lines.append(path + ' ' + expected)
with tarfile.open(dest / 'MODIFIED_FILE.tar') as archive:
    for path, expected in state['modified_hashes'].items():
        assert hashlib.sha256(archive.extractfile(path).read()).hexdigest() == expected
restored = json.loads((dest / 'rollback-restored-hashes.json').read_text())
assert restored == state['baseline_hashes']
lines.extend(['', 'Current rollback: all ' + str(len(restored)) + ' original file hashes restored, all4 new files removed.',
    'Diff reconstruction verified all ' + str(len(state['modified_hashes'])) + ' modified file hashes, equal to live source and archive.',
    'Original BASELINE and ROLLBACK behavior: old HTTP identity retained access and WS retained delivery/heartbeat.',
    'Original matching18-case BASELINE/ROLLBACK each has16 failures/1 error (absent scan entry) and exit1.',
    'MODIFIED behavior: changed identity401/session invalidation; invalid WS closes1008 and does not deliver/handle;18 checks pass.', ''])
for label in ['integration-mysql-init', 'integration-mysql-start', 'integration-redis-start',
    'integration-baseline', 'integration-modified', 'integration-full', 'integration-redis-stop', 'integration-mysql-stop']:
    event = json.loads((dest / (label + '.event.json')).read_text())
    lines.extend(['=== ' + label.upper() + ' ===', 'command: ' + json.dumps(event['command']),
        'cwd: ' + event['cwd'], 'exit: ' + str(event['exit']),
        'literal stdout:', (dest / event['stdout']).read_text(),
        'literal stderr:', (dest / event['stderr']).read_text()])
lines.extend(['=== UPDATED ROLLBACK ===', 'command: ' + str(dest / 'ROLLBACK.sh') + ' <separate-disposable-copy>',
    'script exit: ' + (dest / 'rollback-script.exit').read_text(),
    'literal script stdout:', (dest / 'rollback-script.stdout').read_text(),
    'literal script stderr:', (dest / 'rollback-script.stderr').read_text(),
    'matching regression command: ' + ' '.join(state['command']),
    'regression cwd: ' + state['runs']['rollback'], 'regression exit: ' + (dest / 'rollback.exit').read_text(),
    'literal regression stdout:', (dest / 'rollback.stdout').read_text(),
    'literal regression stderr:', (dest / 'rollback.stderr').read_text()])
failures = []
for path in (Path(runtime['snapshot']) / 'server/target/surefire-reports').glob('TEST-*.xml'):
    for case in ET.parse(path).getroot().findall('testcase'):
        for kind in ['failure', 'error']:
            error = case.find(kind)
            if error is not None:
                failures.append({'class': case.attrib.get('classname'), 'test': case.attrib.get('name'),
                    'kind': kind, 'message': error.attrib.get('message'), 'detail': error.text})
(dest / 'integration-full-failures.json').write_text(json.dumps(failures, ensure_ascii=False, indent=2))
lines.extend(['=== LIVE FULL-RUN REMAINING FAILURES ===', json.dumps(failures, ensure_ascii=True, indent=2),
    'This task-owned snapshot run is historical; it was started before the source chat confirmed its final current-source run.',
    'No failure was silently removed. Current-source final acceptance below supersedes historical snapshot failures.'])
source = root / 'deliverables/99-1-20261108'
final = json.loads((source / '复核-final-执行.json').read_text())
summary = json.loads((source / '来源复核-最终统计.json').read_text())
full = next(event for event in final['events'] if event['label'] == 'full')
assert full['exit'] == 0
assert summary['totals'] == {'tests': 1229, 'failures': 0, 'errors': 0, 'skipped': 2}
differences = []
checked = {}
for path, expected in final['source_hashes'].items():
    if not (path.endswith('.java') or '/resources/db/' in path or path.endswith('admin-permission-matrix.tsv')):
        continue
    actual = hashlib.sha256((root / path).read_bytes()).hexdigest()
    checked[path] = actual
    if actual != expected:
        differences.append(path)
assert not differences, differences
historical_differences = []
for path, current in checked.items():
    snapshot_path = Path(runtime['snapshot']) / path
    if snapshot_path.exists() and hashlib.sha256(snapshot_path.read_bytes()).hexdigest() != current:
        historical_differences.append(path)
for path, expected in state['modified_hashes'].items():
    assert final['source_hashes'][path] == expected
for extension in ['stdout', 'stderr']:
    content = (source / ('复核-final-full.' + extension)).read_bytes()
    (dest / ('source-final-full.' + extension)).write_bytes(content)
(dest / 'source-final-current-hashes.json').write_text(json.dumps(
    {'checked_files': checked, 'differences': differences, 'totals': summary['totals'],
     'full_event': full, 'source_metadata': str(source / '复核-final-执行.json')}, indent=2))
lines.extend(['=== SOURCE CHAT FINAL CURRENT-SOURCE ACCEPTANCE ===',
    'Provenance: independently executed by the source chat, not claimed as an execution of this chat.',
    'Opened original command metadata, raw stdout/stderr and totals, and independently compared current Java/migration/matrix hashes.',
    'Checked current files: ' + str(len(checked)) + '; differences: 0. All18 scoped archive hashes equal the tested source snapshot.',
    'Historical task-owned snapshot differs from final current source in: ' + json.dumps(historical_differences),
    'command: ' + json.dumps(full['command']), 'cwd: ' + full['cwd'], 'exit: ' + str(full['exit']),
    'Tests run: 1229, Failures: 0, Errors: 0, Skipped: 2; BUILD SUCCESS.',
    'Only existing real SMTP and real LLM smoke checks were skipped.',
    'literal source-final stdout:', (dest / 'source-final-full.stdout').read_text(),
    'literal source-final stderr:', (dest / 'source-final-full.stderr').read_text(),
    'Source-final execution metadata: ' + str(source / '复核-final-执行.json'),
    'Source-final totals: ' + str(source / '来源复核-最终统计.json'),
    'Current hash comparison: ' + str(dest / 'source-final-current-hashes.json'),
    'Real network WS and live multi-process deployment remain outside the executed tests.',
    'Implementation complete, awaiting final source-chat material review. No Feishu completion state changed.'])
with (dest / 'VERIFICATION.txt').open('a') as ledger:
    ledger.write('\n'.join(lines) + '\n')
for role in ['MODIFIED_FILE.tar', 'DIFF_FILE.patch', 'VERIFICATION.txt', 'ROLLBACK.sh']:
    path = dest / role
    data = path.read_bytes()
    print(str(path) + ' bytes=' + str(len(data)) + ' sha256=' + hashlib.sha256(data).hexdigest())
print('Remaining full-run failure count: ' + str(len(failures)))
