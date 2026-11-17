from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import sys

OUT = Path(__file__).resolve().parent
ROOT = OUT.parents[1]
STATE = json.loads((OUT / 'state.json').read_text())
MODE = sys.argv[1]
assert MODE in ['BASELINE', 'MODIFIED', 'ROLLBACK']
SOURCE = OUT / 'probe-runners' / MODE
assert not SOURCE.exists(), 'Reuse the recorded event instead of overwriting a run'
PRIOR = Path(json.loads((ROOT / 'deliverables/99-4-5-20261108/复核-final-执行.json').read_text())['snapshot'])
ENV = {'PATH': '/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin',
       'LANG': 'en_US.UTF-8',
       'JAVA_HOME': '/Users/sailtrack/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home'}
EVENTS = []
for relative in ['server/src', 'server/scripts', 'plugins', 'plugin-sdk']:
    if (PRIOR / relative).exists():
        shutil.copytree(PRIOR / relative, SOURCE / relative)
shutil.copyfile(ROOT / 'server/pom.xml', SOURCE / 'server/pom.xml')
for relative in STATE['production_paths']:
    origin = (OUT / 'baseline' if MODE == 'BASELINE' else OUT / 'candidate') / relative
    shutil.copyfile(origin, SOURCE / relative)


def execute(label, command, cwd=SOURCE / 'server'):
    print('COMMAND_EXECUTION', label, json.dumps(command), flush=True)
    stdout = OUT / (MODE + '-' + label + '.stdout')
    stderr = OUT / (MODE + '-' + label + '.stderr')
    with stdout.open('w') as out, stderr.open('w') as err:
        result = subprocess.run(command, cwd=cwd, env=ENV, stdout=out, stderr=err)
    EVENTS.append({'label': label, 'command': command, 'cwd': str(cwd), 'exit': result.returncode,
                   'stdout': str(stdout), 'stderr': str(stderr)})
    print(stdout.read_text()[-2200:], flush=True)
    assert result.returncode == 0, EVENTS[-1]


if MODE == 'ROLLBACK':
    execute('restore', [str(OUT / 'ROLLBACK.sh'), str(SOURCE)])
    assert all(hashlib.sha256((SOURCE / path).read_bytes()).hexdigest() == original
               for path, original in STATE['baseline_hashes'].items())

# The same evidence fixture is an input, installed after rollback, outside the changed production bytes.
fixture = 'server/src/test/java/com/workforcehub/ragflow/service/KnowledgeAccessTestFixture.java'
probe = 'server/src/test/java/com/workforcehub/ragflow/service/KnowledgeAclEvidenceProbe.java'
(SOURCE / fixture).parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(OUT / 'candidate' / fixture, SOURCE / fixture)
shutil.copyfile(OUT / 'evidence/KnowledgeAclEvidenceProbe.java', SOURCE / probe)
try:
    execute('probe', ['mvn', '-B', '-Dstyle.color=never', '-Dtest=KnowledgeAclEvidenceProbe', 'clean', 'test'])
finally:
    (OUT / (MODE + '-events.json')).write_text(json.dumps({'source': str(SOURCE), 'events': EVENTS,
        'production_hashes': {path: hashlib.sha256((SOURCE / path).read_bytes()).hexdigest()
                              for path in STATE['production_paths']}}, ensure_ascii=False, indent=2))
