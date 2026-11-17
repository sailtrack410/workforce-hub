#!/usr/bin/env python3
"""Own isolated fixtures only; never load real .env or service credentials."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time

artifact = Path(__file__).resolve().parent
repository = artifact.parent.parent
test_path = Path('server/src/test/java/com/workforcehub/plugin/AdminPluginLifecycleAuditIntegrationTest.java')
metadata_path = artifact / 'audit-runtime.json'
base_env = {'PATH': '/opt/homebrew/bin:/usr/bin:/bin', 'HOME': '/Users/sailtrack',
            'JAVA_HOME': '/Users/sailtrack/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home'}


def event(label, command, cwd=None, env=None, timeout=None):
    print('COMMAND_EXECUTION ' + json.dumps({'label': label, 'command': command, 'cwd': str(cwd)}), flush=True)
    result = subprocess.run(command, cwd=cwd, env=env or base_env, capture_output=True, timeout=timeout)
    (artifact / (label + '.stdout')).write_bytes(result.stdout)
    (artifact / (label + '.stderr')).write_bytes(result.stderr)
    record = {'command': command, 'cwd': str(cwd), 'exit': result.returncode,
              'stdout': label + '.stdout', 'stderr': label + '.stderr'}
    (artifact / (label + '.event.json')).write_text(json.dumps(record, indent=2) + '\n')
    return result


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


phase = sys.argv[1]
if phase == 'setup':
    runtime = Path(tempfile.mkdtemp(prefix='workforce-991-audit-', dir='/tmp'))
    mysql_home = Path('/tmp/workforce-quota-2033-runtime/mysql-8.4.7-macos15-arm64')
    data = runtime / 'data'
    mysql_port, redis_port = free_port(), free_port()
    assert mysql_port != redis_port
    meta = {'runtime': str(runtime), 'mysql_home': str(mysql_home), 'mysql_port': mysql_port,
            'redis_port': redis_port, 'snapshot': str(runtime / 'snapshot'),
            'boundary': 'new datadir and own loopback processes; no actual .env; generated fixture credentials only'}
    metadata_path.write_text(json.dumps(meta, indent=2) + '\n')
    init = event('audit-mysql-init', [str(mysql_home / 'bin/mysqld'), '--no-defaults',
        '--initialize-insecure', '--basedir=' + str(mysql_home), '--datadir=' + str(data)], timeout=60)
    if init.returncode:
        sys.exit(init.returncode)
    command = [str(mysql_home / 'bin/mysqld'), '--no-defaults', '--basedir=' + str(mysql_home),
        '--datadir=' + str(data), '--bind-address=127.0.0.1', '--port=' + str(mysql_port),
        '--socket=' + str(runtime / 'mysql.sock'), '--pid-file=' + str(runtime / 'mysql.pid'),
        '--log-error=' + str(runtime / 'mysql.log'), '--mysqlx=OFF']
    with (runtime / 'mysql-launch.log').open('wb') as output:
        process = subprocess.Popen(command, env=base_env, stdout=output, stderr=output, start_new_session=True)
    meta['owned_mysql_pid'] = process.pid
    meta['owned_mysql_command'] = command
    metadata_path.write_text(json.dumps(meta, indent=2) + '\n')
    client = [str(mysql_home / 'bin/mysqladmin'), '--no-defaults', '--socket=' + str(runtime / 'mysql.sock'), '-uroot', 'ping']
    for _ in range(50):
        if subprocess.run(client, env=base_env, capture_output=True).returncode == 0:
            break
        time.sleep(0.2)
    else:
        raise RuntimeError('owned MySQL did not become ready')
    sql = "CREATE USER 'fixture'@'%' IDENTIFIED BY 'isolated-audit-fixture'; GRANT ALL ON workforce_991_test.* TO 'fixture'@'%';"
    result = subprocess.run([str(mysql_home / 'bin/mysql'), '--no-defaults', '--socket=' + str(runtime / 'mysql.sock'),
        '-uroot'], input=sql.encode(), env=base_env, capture_output=True)
    assert result.returncode == 0, result.stderr.decode()
    redis = event('audit-redis-start', ['/opt/homebrew/bin/redis-server', '--bind', '127.0.0.1',
        '--port', str(redis_port), '--save', '', '--appendonly', 'no', '--dir', str(runtime),
        '--daemonize', 'yes', '--pidfile', str(runtime / 'redis.pid'), '--logfile', str(runtime / 'redis.log')])
    assert redis.returncode == 0
    snapshot = Path(meta['snapshot'])
    (snapshot / 'server').mkdir(parents=True)
    shutil.copy2(repository / 'server/pom.xml', snapshot / 'server/pom.xml')
    shutil.copytree(repository / 'server/src', snapshot / 'server/src', ignore=shutil.ignore_patterns('.env', '.env.*'))
    shutil.copytree(repository / 'server/scripts/sql', snapshot / 'server/scripts/sql', ignore=shutil.ignore_patterns('.env', '.env.*'))
    shutil.copytree(repository / 'plugins/hello-plugin/src', snapshot / 'plugins/hello-plugin/src')
    hashes = {str(p.relative_to(snapshot)): hashlib.sha256(p.read_bytes()).hexdigest()
              for p in snapshot.rglob('*') if p.is_file()}
    (artifact / 'audit-snapshot-hashes.json').write_text(json.dumps(hashes, indent=2) + '\n')
    print('Owned MySQL and Redis ready; current source snapshot created', flush=True)
elif phase == 'cleanup':
    meta = json.loads(metadata_path.read_text())
    runtime = Path(meta['runtime'])
    results = []
    for label, command in [('audit-mysql-stop', [str(Path(meta['mysql_home']) / 'bin/mysqladmin'),
            '--no-defaults', '--socket=' + str(runtime / 'mysql.sock'), '-uroot', 'shutdown']),
            ('audit-redis-stop', ['/opt/homebrew/bin/redis-cli', '-h', '127.0.0.1', '-p', str(meta['redis_port']),
             'shutdown', 'nosave'])]:
        result = event(label, command, timeout=30)
        results.append(result.returncode)
    for port in [meta['mysql_port'], meta['redis_port']]:
        with socket.socket() as sock:
            assert sock.connect_ex(('127.0.0.1', port)) != 0, 'owned port remains open'
    assert results == [0, 0], results
    (artifact / 'audit-fixture-cleanup.json').write_text(json.dumps({
        'runtime': meta['runtime'], 'mysql_exit': results[0], 'redis_exit': results[1],
        'mysql_port_closed': True, 'redis_port_closed': True,
        'scope': 'Only processes created for this run were stopped'
    }, indent=2) + '\n')
    print('Owned MySQL/Redis stopped; both ports closed', flush=True)
else:
    meta = json.loads(metadata_path.read_text())
    snapshot = Path(meta['snapshot'])
    if phase == 'baseline':
        shutil.copy2(artifact / 'baseline' / test_path, snapshot / test_path)
    elif phase == 'rollback':
        result = event('audit-rollback-script', [str(artifact / 'ROLLBACK.sh'), str(snapshot / test_path)])
        assert result.returncode == 0
        restored = hashlib.sha256((snapshot / test_path).read_bytes()).hexdigest()
        original = hashlib.sha256((artifact / 'baseline' / test_path).read_bytes()).hexdigest()
        assert restored == original
        (artifact / 'audit-rollback-restored.json').write_text(json.dumps(
            {'restored_sha256': restored, 'original_sha256': original, 'equal': True}, indent=2) + '\n')
    elif phase in ['modified', 'full']:
        shutil.copy2(artifact / 'modified' / test_path, snapshot / test_path)
    else:
        raise ValueError(phase)
    env = dict(base_env, TEST_DB_HOST='127.0.0.1', TEST_DB_PORT=str(meta['mysql_port']),
        TEST_DB_NAME='workforce_991_test', TEST_DB_USER='fixture', TEST_DB_PASSWORD='isolated-audit-fixture',
        DB_HOST='127.0.0.1', DB_PORT=str(meta['mysql_port']), DB_NAME='workforce_991_test',
        DB_USER='fixture', DB_PASSWORD='isolated-audit-fixture', TEST_REDIS_HOST='127.0.0.1',
        TEST_REDIS_PORT=str(meta['redis_port']), REDIS_HOST='127.0.0.1', REDIS_PORT=str(meta['redis_port']))
    command = ['mvn', '-B']
    if phase != 'full':
        command.append('-Dtest=AdminPluginLifecycleAuditIntegrationTest')
    else:
        command.append('clean')
        command.append('-Dquota.mysql.url=jdbc:mysql://127.0.0.1:' + str(meta['mysql_port'])
                       + '/quota_2033_test?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true')
    command.append('test')
    label = 'audit-' + phase
    result = event(label, command, cwd=snapshot / 'server', env=env)
    for line in result.stdout.decode().splitlines():
        if 'Tests run:' in line or 'BUILD ' in line or 'AUDIT_AUTHORIZATION' in line:
            print(line, flush=True)
    print('EXIT_STATUS=' + str(result.returncode), flush=True)
    sys.exit(result.returncode)
