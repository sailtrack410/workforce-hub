from pathlib import Path
import base64
import hashlib
import io
import json
import os
import subprocess
import tarfile
import tempfile

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
HEAD = '9667f659e46a3c83273a7dc813e1ccd4073f1a03'


def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def archive(files):
    stream = io.BytesIO()
    with tarfile.open(fileobj=stream, mode='w:gz') as tar:
        for path, data in sorted(files.items()):
            info = tarfile.TarInfo(path)
            info.size = len(data)
            info.mode = 0o644
            tar.addfile(info, io.BytesIO(data))
    return stream.getvalue()


assert git('rev-parse', 'HEAD').decode().strip() == HEAD
execution = json.loads((OUT / '复核-final-执行.json').read_text())
assert next(event for event in execution['events'] if event['label'] == 'full')['exit'] == 0
paths = set(git('diff', '--name-only', HEAD, '--', 'server', 'employee-web', 'admin-web').decode().splitlines())
paths.update(git('ls-files', '--others', '--exclude-standard', '--', 'server', 'employee-web', 'admin-web').decode().splitlines())
assert paths
assert all(path.startswith(('server/src/', 'employee-web/src/', 'admin-web/src/')) for path in paths)
assert not any('EvidenceTest' in path for path in paths)
original = {}
modified = {}
for path in sorted(paths):
    modified[path] = (ROOT / path).read_bytes()
    entry = subprocess.run(['git', 'show', f'{HEAD}:{path}'], cwd=ROOT, capture_output=True)
    if entry.returncode == 0:
        original[path] = entry.stdout
    if path.startswith('server/'):
        assert sha(modified[path]) == execution['source_hashes'][path], path

(OUT / 'MODIFIED_FILE.tar.gz').write_bytes(archive(modified))
patch = git('diff', '--binary', HEAD, '--', *sorted(original))
for path in sorted(paths - original.keys()):
    result = subprocess.run(['git', 'diff', '--no-index', '--binary', '--', '/dev/null', path], cwd=ROOT, capture_output=True)
    assert result.returncode == 1 and result.stdout
    patch += result.stdout
(OUT / 'DIFF_FILE.patch').write_bytes(patch)

encoded = base64.b64encode(archive(original)).decode()
script = '#!/bin/sh\nset -eu\n[ "$#" -eq 1 ] && [ -d "$1" ] || { echo "usage: ROLLBACK.sh TARGET_COPY_DIRECTORY" >&2; exit 2; }\n'
for path in sorted(paths - original.keys()):
    script += 'rm -f -- "$1/' + path + '"\n'
script += "base64 -d <<'PRISTINE_BYTES' | tar -xz -C \"$1\"\n" + encoded + '\nPRISTINE_BYTES\n'
script += "printf '%s\\n' 'ROLLBACK: pristine bytes restored; added files removed'\n"
(OUT / 'ROLLBACK.sh').write_text(script)
os.chmod(OUT / 'ROLLBACK.sh', 0o755)

work = Path(tempfile.mkdtemp(prefix='workforce-9945-artifacts-'))
for path, data in original.items():
    target = work / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
events = []


def execute(label, command):
    result = subprocess.run(command, cwd=work, capture_output=True)
    events.append({'label': label, 'command': command, 'cwd': str(work), 'exit': result.returncode,
                   'stdout': result.stdout.decode(), 'stderr': result.stderr.decode()})
    assert result.returncode == 0, events[-1]


execute('RECONSTRUCT', ['git', 'apply', str(OUT / 'DIFF_FILE.patch')])
assert all(sha((work / path).read_bytes()) == sha(data) for path, data in modified.items())
execute('ROLLBACK', [str(OUT / 'ROLLBACK.sh'), str(work)])
assert all(sha((work / path).read_bytes()) == sha(data) for path, data in original.items())
assert all(not (work / path).exists() for path in paths - original.keys())
execute('REAPPLY', ['git', 'apply', str(OUT / 'DIFF_FILE.patch')])
assert all(sha((work / path).read_bytes()) == sha(data) for path, data in modified.items())
manifest = {path: {'baseline': sha(original[path]) if path in original else None,
                   'modified': sha(modified[path])} for path in sorted(paths)}
(OUT / 'manifest.json').write_text(json.dumps({'head': HEAD, 'target': str(ROOT), 'files': manifest,
    'artifact_events': events}, ensure_ascii=False, indent=2))

with (OUT / 'VERIFICATION.txt').open('wb') as record:
    record.write(('TARGET=' + str(ROOT) + '\nBASELINE_COMMIT=' + HEAD + '\nGOAL=99-4,99-5\n').encode())
    record.write(json.dumps(manifest, ensure_ascii=False, indent=2).encode())
    for task in ['99-4-20261108', '99-4-web-20261108', '99-5-20261108']:
        file = ROOT / 'deliverables' / task / 'VERIFICATION.txt'
        record.write(('\n\nTASK_EVIDENCE=' + str(file) + '\n').encode())
        record.write(file.read_bytes())
    record.write(b'\n\nFINAL_COMBINED_EXECUTION\n')
    record.write(json.dumps(execution['events'], ensure_ascii=False, indent=2).encode())
    for channel in ['stdout', 'stderr']:
        record.write(('\nLITERAL_FINAL_' + channel.upper() + '\n').encode())
        record.write((OUT / ('复核-final-full.' + channel)).read_bytes())
    record.write(b'\n\nARTIFACT_EXECUTION\n')
    record.write(json.dumps(events, ensure_ascii=False, indent=2).encode())
    record.write(b'\nRESTORED_HASHES=BASELINE_HASHES\nREAPPLIED_HASHES=MODIFIED_HASHES\n')

# Reopen every role and verify that the archive describes the frozen workspace.
with tarfile.open(OUT / 'MODIFIED_FILE.tar.gz') as tar:
    assert set(tar.getnames()) == paths
    for member in tar.getmembers():
        assert sha(tar.extractfile(member).read()) == manifest[member.name]['modified']
assert (OUT / 'DIFF_FILE.patch').read_bytes() == patch
assert (OUT / 'VERIFICATION.txt').read_bytes().endswith(b'REAPPLIED_HASHES=MODIFIED_HASHES\n')
assert os.access(OUT / 'ROLLBACK.sh', os.X_OK) and (OUT / 'ROLLBACK.sh').read_text() == script
assert all((ROOT / path).read_bytes() == data for path, data in modified.items())
print('Reconstruction, rollback, reapply and all four reopened roles verified; files=' + str(len(paths)))
for name in ['MODIFIED_FILE.tar.gz', 'DIFF_FILE.patch', 'VERIFICATION.txt', 'ROLLBACK.sh']:
    print(str(OUT / name))
