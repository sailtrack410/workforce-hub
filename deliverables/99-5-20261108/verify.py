#!/usr/bin/env python3
"""Disposable MySQL, deterministic clock injection, and identical three-role process inputs."""
import base64
import concurrent.futures
import difflib
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tarfile
import tempfile
import time

ROOT = Path(__file__).resolve().parent
RUNTIME = Path(tempfile.mkdtemp(prefix='workforce-id-995-'))
MYSQL = Path('/tmp/workforce-quota-2033-runtime/mysql-8.4.7-macos15-arm64/bin')
JAVA = Path('/Users/sailtrack/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home')
ENV = dict(os.environ, JAVA_HOME=str(JAVA))
EVENTS = []
with socket.socket() as sock:
    sock.bind(('127.0.0.1', 0))
    PORT = sock.getsockname()[1]
URL = f'jdbc:mysql://127.0.0.1:{PORT}/id_995_test?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true'


def command(label, args, cwd=ROOT, expected=0):
    result = subprocess.run([str(arg) for arg in args], cwd=cwd, env=ENV, capture_output=True)
    out = ROOT / f'{label}.stdout'
    err = ROOT / f'{label}.stderr'
    out.write_bytes(result.stdout)
    err.write_bytes(result.stderr)
    event = dict(label=label, command=[str(arg) for arg in args], cwd=str(cwd),
                 stdout=str(out), stderr=str(err), exit=result.returncode)
    EVENTS.append(event)
    (ROOT / 'events.json').write_text(json.dumps(dict(runtime=str(RUNTIME), url=URL, events=EVENTS), indent=2))
    if expected is not None and result.returncode != expected:
        raise RuntimeError(f'{label}: exit={result.returncode}; see {out} and {err}')
    return result


def start_mysql(label):
    command(label, [MYSQL / 'mysqld', '--no-defaults', f'--basedir={MYSQL.parent}',
                   f'--datadir={RUNTIME / "data"}', '--bind-address=127.0.0.1', f'--port={PORT}',
                   f'--socket={RUNTIME / "mysql.sock"}', f'--pid-file={RUNTIME / "mysql.pid"}',
                   f'--log-error={RUNTIME / "mysql.log"}', '--mysqlx=OFF', '--daemonize'])


def stop_mysql(label):
    command(label, [MYSQL / 'mysqladmin', '--no-defaults', f'--socket={RUNTIME / "mysql.sock"}', '-uroot', 'shutdown'])


def process_suite(role, source, classpath):
    all_ids = []
    for mode, initial, step in [('frozen', 1700000000000, 0), ('backward', 1600000000000, -1000),
                                ('forward', 1800000000000, 1000)]:
        def process(index):
            result = command(f'{role}-{mode}-{index}', [JAVA / 'bin/java',
                f'-javaagent:{ROOT / "fixtures/clock-agent.jar"}', f'-Did.clock.millis={initial}',
                f'-Did.clock.step={step}', '-cp', classpath,
                'com.workforcehub.common.util.IdGeneratorProcessProbe', URL, '1800'], source / 'server')
            ids = [int(line[3:]) for line in result.stdout.decode().splitlines() if line.startswith('ID=')]
            assert len(ids) == 1800, (role, mode, index, len(ids))
            return ids
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
            for ids in executor.map(process, range(4)):
                all_ids.extend(ids)
        # Both JVM and storage restart; no issued range may be reused after a durable commit.
        stop_mysql(f'{role}-{mode}-mysql-stop')
        start_mysql(f'{role}-{mode}-mysql-restart')
        all_ids.extend(process(4))
    duplicates = len(all_ids) - len(set(all_ids))
    summary = dict(role=role, processes=15, ids=len(all_ids), unique=len(set(all_ids)), duplicates=duplicates,
                   clocks=['frozen', 'backward', 'forward'], mysql_restarts=3)
    assert (duplicates == 0) == (role == 'MODIFIED'), summary
    return summary


try:
    command('mysql-initialize', [MYSQL / 'mysqld', '--no-defaults', '--initialize-insecure',
                                f'--basedir={MYSQL.parent}', f'--datadir={RUNTIME / "data"}'])
    start_mysql('mysql-start')
    command('fixture-compile', [JAVA / 'bin/javac', 'ClockAgent.java', 'ClockFixture.java'], ROOT / 'fixtures')
    command('fixture-jar', [JAVA / 'bin/jar', 'cfm', 'clock-agent.jar', 'MANIFEST.MF',
                            'ClockAgent.class', 'ClockAgent$1.class', 'ClockFixture.class'], ROOT / 'fixtures')
    command('modified-tests', ['mvn', '-B', '-Dstyle.color=never', f'-Did.mysql.url={URL}',
                              '-Dtest=IdGeneratorTest,IdGeneratorMysqlTest', 'test',
                              'dependency:build-classpath', '-Dmdep.outputFile=target/test-classpath.txt',
                              '-Dmdep.includeScope=test'], ROOT / 'modified/server')
    dependencies = (ROOT / 'modified/server/target/test-classpath.txt').read_text().strip()
    command('baseline-compile', ['mvn', '-B', '-Dstyle.color=never', '-DskipTests', 'test-compile'], ROOT / 'baseline/server')
    rollback = ROOT / 'rollback-run'
    if rollback.exists():
        shutil.rmtree(rollback)
    shutil.copytree(ROOT / 'modified', rollback, ignore=shutil.ignore_patterns('target'))
    command('rollback-apply', [ROOT / 'ROLLBACK.sh', rollback])
    manifest = json.loads((ROOT / 'manifest.json').read_text())
    restored = {}
    for filename, entry in manifest.items():
        path = rollback / filename
        actual = hashlib.sha256(path.read_bytes()).hexdigest() if path.exists() else None
        assert actual == entry['baseline'], (filename, actual, entry['baseline'])
        restored[filename] = actual
    probe = Path('server/src/test/java/com/workforcehub/common/util/IdGeneratorProcessProbe.java')
    (rollback / probe).parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT / 'modified' / probe, rollback / probe)
    command('rollback-compile', ['mvn', '-B', '-Dstyle.color=never', '-DskipTests', 'test-compile'], rollback / 'server')
    summaries = []
    for role, source in [('BASELINE', ROOT / 'baseline'), ('MODIFIED', ROOT / 'modified'), ('ROLLBACK', rollback)]:
        classpath = f'{source / "server/target/classes"}:{source / "server/target/test-classes"}:{ROOT / "fixtures"}:{dependencies}'
        summaries.append(process_suite(role, source, classpath))
    modified = ROOT / 'modified'
    with tarfile.open(ROOT / 'MODIFIED_FILE.tar.gz', 'w:gz') as tar:
        for filename in manifest:
            tar.add(modified / filename, arcname=filename)
    patch = []
    for filename, entry in manifest.items():
        original = ROOT / 'baseline' / filename
        before = original.read_text().splitlines(True) if entry['baseline'] else []
        after = (modified / filename).read_text().splitlines(True)
        patch.extend(difflib.unified_diff(before, after, fromfile=f'a/{filename}' if entry['baseline'] else '/dev/null', tofile=f'b/{filename}'))
    (ROOT / 'DIFF_FILE.patch').write_text(''.join(patch))
    report = dict(summary=summaries, restored_hashes=restored, manifest=manifest, events=EVENTS,
                  clock_fixture='Java agent replaces only IdGenerator System.currentTimeMillis with a deterministic frozen/step clock; source bytes remain unchanged',
                  boundary='Generated disposable MySQL only; no existing credentials or application processes accessed')
    (ROOT / 'result.json').write_text(json.dumps(report, indent=2))
    ledger = ['TARGET=server/src/main/java/com/workforcehub/common/util/IdGenerator.java',
              'CHANGED_SYMBOL=IdGenerator.nextId; IdGenerator.nextIdString remains static String',
              json.dumps(summaries), 'RESTORED_HASHES=' + json.dumps(restored)]
    for event in EVENTS:
        ledger.extend(['\nEVENT=' + event['label'], 'COMMAND=' + json.dumps(event['command']), 'CWD=' + event['cwd'],
                       'EXIT=' + str(event['exit']), 'STDOUT_BEGIN', Path(event['stdout']).read_text(errors='replace'),
                       'STDOUT_END', 'STDERR_BEGIN', Path(event['stderr']).read_text(errors='replace'), 'STDERR_END'])
    (ROOT / 'VERIFICATION.txt').write_text('\n'.join(ledger))
    print(json.dumps(summaries, indent=2), flush=True)
finally:
    if (RUNTIME / 'mysql.pid').exists():
        stop_mysql('mysql-final-stop')
