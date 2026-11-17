from pathlib import Path
import difflib
import hashlib
import json
import shutil
import subprocess
import tarfile
import tempfile

root = Path.cwd()
dest = root / 'deliverables/99-2-20261108'
state = json.loads((dest / 'state.json').read_text())
paths = state['paths'] + state['new_paths']
state['modified_hashes'] = {p: hashlib.sha256((root / p).read_bytes()).hexdigest() for p in paths}
with tarfile.open(dest / 'MODIFIED_FILE.tar', 'w') as archive:
    for p in paths:
        archive.add(root / p, arcname=p)
patch = []
for p in paths:
    old = (dest / 'baseline' / p).read_text() if p in state['paths'] else ''
    patch.extend(difflib.unified_diff(old.splitlines(True), (root / p).read_text().splitlines(True),
        fromfile='a/' + p if old else '/dev/null', tofile='b/' + p))
(dest / 'DIFF_FILE.patch').write_text(''.join(patch))
script = ['#!/bin/sh', 'set -eu',
    'target=${1:?Usage: ROLLBACK.sh /absolute/path/to/project-copy}',
    'case "$target" in /*) ;; *) echo "absolute target path required" >&2; exit 2;; esac',
    'hash() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d " " -f 1; }']
for p in paths:
    modified = state['modified_hashes'][p]
    original = state['baseline_hashes'].get(p, '')
    script.extend(['if [ -f "$target/' + p + '" ]; then', ' actual=$(hash "$target/' + p + '")',
        ' [ "$actual" = "' + modified + '" ] || [ "$actual" = "' + original
        + '" ] || { echo "refusing to overwrite concurrent edits: ' + p + '" >&2; exit 3; }', 'fi'])
for index, p in enumerate(paths):
    if p in state['new_paths']:
        script.append('rm -f "$target/' + p + '"')
    else:
        marker = 'WFH_99_2_BASELINE_' + str(index)
        script.extend(['mkdir -p "$target/' + str(Path(p).parent) + '"',
            'cat > "$target/' + p + '" <<\'' + marker + '\'',
            (dest / 'baseline' / p).read_text().rstrip('\n'), marker])
script.append('echo "ROLLBACK restored pristine 99-2 files; unrelated paths preserved"')
(dest / 'ROLLBACK.sh').write_text('\n'.join(script) + '\n')
(dest / 'ROLLBACK.sh').chmod(0o755)
candidate = Path(tempfile.mkdtemp(prefix='wfh-99-2-packaged-rollback-'))
shutil.copytree(Path(state['full_work']), candidate / 'server', ignore=shutil.ignore_patterns('target'))
for p in paths:
    target = candidate / p
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(root / p, target)
result = subprocess.run([str(dest / 'ROLLBACK.sh'), str(candidate)], capture_output=True, text=True)
for extension, value in [('stdout', result.stdout), ('stderr', result.stderr), ('exit', str(result.returncode))]:
    (dest / ('rollback-script.' + extension)).write_text(value)
assert result.returncode == 0, result.stderr
hashes = {p: hashlib.sha256((candidate / p).read_bytes()).hexdigest() for p in state['paths']}
assert hashes == state['baseline_hashes']
assert all(not (candidate / p).exists() for p in state['new_paths'])
(dest / 'rollback-restored-hashes.json').write_text(json.dumps(hashes, indent=2))
baseline_root = Path(state['runs']['baseline-final']).parent
for p in state.get('integration_paths', []):
    if not (baseline_root / p).exists():
        target = baseline_root / p
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(dest / 'baseline' / p, target)
assert all(hashlib.sha256((baseline_root / p).read_bytes()).hexdigest() == h
    for p, h in state['baseline_hashes'].items())
(dest / 'baseline-final-restored-hashes.json').write_text(json.dumps(state['baseline_hashes'], indent=2))
result = subprocess.run(['patch', '-p1', '--batch', '-i', str(dest / 'DIFF_FILE.patch')],
    cwd=candidate, capture_output=True, text=True)
for extension, value in [('stdout', result.stdout), ('stderr', result.stderr), ('exit', str(result.returncode))]:
    (dest / ('patch.' + extension)).write_text(value)
assert result.returncode == 0, result.stderr
assert all(hashlib.sha256((candidate / p).read_bytes()).hexdigest() == h
    for p, h in state['modified_hashes'].items())
with tarfile.open(dest / 'MODIFIED_FILE.tar') as archive:
    assert all(hashlib.sha256(archive.extractfile(p).read()).hexdigest() == h
        for p, h in state['modified_hashes'].items())
result = subprocess.run([str(dest / 'ROLLBACK.sh'), str(candidate)], capture_output=True, text=True)
assert result.returncode == 0
shutil.copyfile(dest / 'fixtures/SessionValidityRequestChainTest.java',
    candidate / 'server/src/test/java/com/workforcehub/auth/SessionValidityRequestChainTest.java')
state['runs']['rollback'] = str(candidate / 'server')
(dest / 'state.json').write_text(json.dumps(state, ensure_ascii=False, indent=2))
environment = {'PATH': '/opt/homebrew/bin:/usr/bin:/bin', 'HOME': str(Path.home()),
    'JAVA_HOME': '/Users/sailtrack/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home'}
with (dest / 'rollback.stdout').open('w') as stdout, (dest / 'rollback.stderr').open('w') as stderr:
    result = subprocess.run(state['command'], cwd=candidate / 'server', stdout=stdout, stderr=stderr, env=environment)
(dest / 'rollback.exit').write_text(str(result.returncode))
print('Packaged files:', len(paths), '; restored original hashes:', len(hashes))
print('Rollback verification exit:', result.returncode)
for line in (dest / 'rollback.stdout').read_text().splitlines():
    if 'Tests run:' in line or 'BUILD ' in line:
        print(line)
