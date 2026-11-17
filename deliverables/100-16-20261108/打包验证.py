from pathlib import Path
import base64
import difflib
import hashlib
import io
import json
import os
import subprocess
import tarfile
import tempfile

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[1]
STATE = json.loads((OUT / 'state.json').read_text())
CANDIDATE = OUT / 'candidate'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def archive(files):
    stream = io.BytesIO()
    with tarfile.open(fileobj=stream, mode='w:gz') as tar:
        for path, data in sorted(files.items()):
            member = tarfile.TarInfo(path)
            member.size = len(data)
            member.mode = 0o644
            tar.addfile(member, io.BytesIO(data))
    return stream.getvalue()


original = {}
modified = {}
for prefix in ['server/src/main/java', 'server/src/test/java']:
    for file in (CANDIDATE / prefix).rglob('*.java'):
        path = str(file.relative_to(CANDIDATE))
        if (ROOT / path).exists():
            old = (ROOT / path).read_bytes()
            if old == file.read_bytes():
                continue
            assert path in STATE['production_paths'], path
            assert digest(old) == STATE['baseline_hashes'][path], path
            original[path] = old
        else:
            assert path.startswith('server/src/test/java/'), path
        modified[path] = file.read_bytes()
assert set(original) == set(STATE['production_paths'])

(OUT / 'MODIFIED_FILE.tar.gz').write_bytes(archive(modified))
patch = ''.join(''.join(difflib.unified_diff(
    original.get(path, b'').decode().splitlines(True), data.decode().splitlines(True),
    fromfile='a/' + path if path in original else '/dev/null', tofile='b/' + path))
    for path, data in sorted(modified.items()))
(OUT / 'DIFF_FILE.patch').write_text(patch)
script = '#!/bin/sh\nset -eu\n[ "$#" -eq 1 ] && [ -d "$1" ] || { echo "usage: ROLLBACK.sh TARGET_COPY_DIRECTORY" >&2; exit 2; }\n'
for path in sorted(modified.keys() - original.keys()):
    script += 'rm -f -- "$1/' + path + '"\n'
script += "base64 -d <<'PRISTINE_BYTES' | tar -xz -C \"$1\"\n"
script += base64.b64encode(archive(original)).decode() + '\nPRISTINE_BYTES\n'
script += "printf '%s\\n' 'ROLLBACK: pristine source restored; added tests removed'\n"
(OUT / 'ROLLBACK.sh').write_text(script)
os.chmod(OUT / 'ROLLBACK.sh', 0o755)

work = Path(tempfile.mkdtemp(prefix='workforce-10016-artifacts-'))
for path, data in original.items():
    file = work / path
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_bytes(data)
events = []
for label, command in [('RECONSTRUCT', ['git', 'apply', str(OUT / 'DIFF_FILE.patch')]),
                       ('ROLLBACK', [str(OUT / 'ROLLBACK.sh'), str(work)]),
                       ('REAPPLY', ['git', 'apply', str(OUT / 'DIFF_FILE.patch')])]:
    result = subprocess.run(command, cwd=work, capture_output=True, text=True)
    events.append({'label': label, 'command': command, 'cwd': str(work), 'exit': result.returncode,
                   'stdout': result.stdout, 'stderr': result.stderr})
    assert result.returncode == 0, events[-1]
    expected = original if label == 'ROLLBACK' else modified
    assert all(digest((work / path).read_bytes()) == digest(data) for path, data in expected.items())
    if label == 'ROLLBACK':
        assert all(not (work / path).exists() for path in modified.keys() - original.keys())

manifest = {'target': str(ROOT), 'head': STATE['head'], 'files': {
    path: {'baseline': digest(original[path]) if path in original else None, 'modified': digest(data)}
    for path, data in sorted(modified.items())}, 'artifact_events': events}
(OUT / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
with (OUT / 'VERIFICATION.txt').open('wb') as log:
    log.write(('TARGET=' + str(ROOT) + '\nGOAL=100-16 知识库检索空间ACL\n').encode())
    log.write(json.dumps(manifest, ensure_ascii=False, indent=2).encode())
    log.write(b'\nBOUNDARY: service probes label exception status as HTTP_*, without a network server; embedding/vector use controlled doubles. Integration tests use disposable MySQL/Redis. No real credentials or user application processes.\n')
    for file in sorted(OUT.glob('*-events.json')):
        log.write(('\nEVENT_FILE=' + str(file) + '\n').encode())
        log.write(file.read_bytes())
    for file in sorted(OUT.glob('*.stdout')) + sorted(OUT.glob('*.stderr')):
        log.write(('\nLITERAL_OUTPUT_FILE=' + str(file) + '\n').encode())
        log.write(file.read_bytes())
    for final in sorted(OUT.glob('复核-*-执行.json')):
        log.write(('\nEXECUTION_METADATA_FILE=' + str(final) + '\n').encode())
        log.write(json.dumps(json.loads(final.read_text())['events'], ensure_ascii=False, indent=2).encode())
    for file in sorted(CANDIDATE.glob('专项*')):
        if file.is_file():
            log.write(('\nTARGETED_COMMAND=mvn -B -Dstyle.color=never -Dtest=RagflowKnowledgeAccessRepositoryTest,RagflowKnowledgeSearchAuthorizationTest,AgentToolKnowledgeAuthorizationTest,WorkflowContextOperatorTest test\nLITERAL_OUTPUT_FILE=' + str(file) + '\n').encode())
            log.write(file.read_bytes())
    log.write(b'\nRESTORED_HASHES=BASELINE_HASHES\nREAPPLIED_HASHES=MODIFIED_HASHES\n')

with tarfile.open(OUT / 'MODIFIED_FILE.tar.gz') as tar:
    assert set(tar.getnames()) == set(modified)
    for member in tar.getmembers():
        assert digest(tar.extractfile(member).read()) == manifest['files'][member.name]['modified']
assert (OUT / 'DIFF_FILE.patch').read_text() == patch
assert (OUT / 'VERIFICATION.txt').read_bytes().endswith(b'REAPPLIED_HASHES=MODIFIED_HASHES\n')
assert (OUT / 'ROLLBACK.sh').read_text() == script and os.access(OUT / 'ROLLBACK.sh', os.X_OK)
print('Reconstructed, rolled back, reapplied; all hashes match; four roles reopened. Files=' + str(len(modified)))
for name in ['MODIFIED_FILE.tar.gz', 'DIFF_FILE.patch', 'VERIFICATION.txt', 'ROLLBACK.sh']:
    print(OUT / name)
