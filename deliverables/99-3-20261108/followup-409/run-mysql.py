#!/usr/bin/env python3
"""Run against owned disposable MySQL/Redis only, with generated fixture credentials."""
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import sys
import tempfile
import time

out = Path(__file__).resolve().parent
repo = out.parents[2]
metadata = out / 'runtime.json'
base_env = {'PATH': '/opt/homebrew/bin:/usr/bin:/bin', 'HOME': '/Users/sailtrack',
            'JAVA_HOME': '/Users/sailtrack/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home'}


def event(label, command, cwd=None, env=None, input=None):
    print('COMMAND_EXECUTION ' + json.dumps({'label': label, 'command': command, 'cwd': str(cwd)}), flush=True)
    with (out / (label + '.stdout')).open('wb') as stdout, (out / (label + '.stderr')).open('wb') as stderr:
        result = subprocess.run(command, cwd=cwd, env=env or base_env, input=input, stdout=stdout, stderr=stderr)
    record = {'command': command, 'cwd': str(cwd), 'exit': result.returncode,
              'stdout': str(out / (label + '.stdout')), 'stderr': str(out / (label + '.stderr'))}
    (out / (label + '.event.json')).write_text(json.dumps(record, indent=2) + '\n')
    return result.returncode


def free_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


phase = sys.argv[1]
if phase == 'setup':
    runtime = Path(tempfile.mkdtemp(prefix='workforce-993-mysql-', dir='/tmp'))
    mysql_home = Path('/tmp/workforce-quota-2033-runtime/mysql-8.4.7-macos15-arm64')
    mysql_port, redis_port = free_port(), free_port()
    assert mysql_port != redis_port
    meta = {'runtime': str(runtime), 'mysql_home': str(mysql_home), 'mysql_port': mysql_port,
            'redis_port': redis_port, 'snapshot': str(runtime / 'snapshot'),
            'boundary': 'Owned new datadir and free loopback ports; no real .env or credentials'}
    metadata.write_text(json.dumps(meta, indent=2) + '\n')
    code = event('mysql-init', [str(mysql_home / 'bin/mysqld'), '--no-defaults', '--initialize-insecure',
                              '--basedir=' + str(mysql_home), '--datadir=' + str(runtime / 'data')])
    assert code == 0
    code = event('mysql-start', [str(mysql_home / 'bin/mysqld'), '--no-defaults', '--basedir=' + str(mysql_home),
        '--datadir=' + str(runtime / 'data'), '--bind-address=127.0.0.1', '--port=' + str(mysql_port),
        '--socket=' + str(runtime / 'mysql.sock'), '--pid-file=' + str(runtime / 'mysql.pid'),
        '--log-error=' + str(runtime / 'mysql.log'), '--mysqlx=OFF', '--daemonize'])
    assert code == 0
    # Only this script's generated fixture user is configured; its password never goes into output.
    sql = b"CREATE USER 'fixture'@'%' IDENTIFIED BY 'leave-993-disposable-fixture'; GRANT ALL ON workforce_993_test.* TO 'fixture'@'%'; GRANT ALL ON quota_2033_test.* TO 'fixture'@'%';"
    assert event('mysql-fixture-user', [str(mysql_home / 'bin/mysql'), '--no-defaults',
                 '--socket=' + str(runtime / 'mysql.sock'), '-uroot'], input=sql) == 0
    assert event('redis-start', ['/opt/homebrew/bin/redis-server', '--bind', '127.0.0.1', '--port', str(redis_port),
        '--save', '', '--appendonly', 'no', '--dir', str(runtime), '--daemonize', 'yes',
        '--pidfile', str(runtime / 'redis.pid'), '--logfile', str(runtime / 'redis.log')]) == 0
    snapshot = Path(meta['snapshot'])
    (snapshot / 'server').mkdir(parents=True)
    shutil.copy2(repo / 'server/pom.xml', snapshot / 'server/pom.xml')
    for source in ['server/src', 'server/scripts/sql', 'plugins/hello-plugin/src']:
        shutil.copytree(repo / source, snapshot / source, ignore=shutil.ignore_patterns('.env', '.env.*'))
    print('OWNED_FIXTURES_READY ' + str(metadata), flush=True)
elif phase == 'cleanup':
    meta = json.loads(metadata.read_text())
    runtime = Path(meta['runtime'])
    assert event('mysql-stop', [str(Path(meta['mysql_home']) / 'bin/mysqladmin'), '--no-defaults',
                '--socket=' + str(runtime / 'mysql.sock'), '-uroot', 'shutdown']) == 0
    assert event('redis-stop', ['/opt/homebrew/bin/redis-cli', '-h', '127.0.0.1', '-p', str(meta['redis_port']),
                              'shutdown', 'nosave']) == 0
    for port in [meta['mysql_port'], meta['redis_port']]:
        with socket.socket() as sock:
            assert sock.connect_ex(('127.0.0.1', port)) != 0
    meta['owned_fixture_stopped'] = True
    metadata.write_text(json.dumps(meta, indent=2) + '\n')
    print('OWNED_FIXTURES_STOPPED', flush=True)
else:
    meta = json.loads(metadata.read_text())
    snapshot = Path(meta['snapshot'])
    production = ['server/src/main/java/com/workforcehub/leave/service/LeaveService.java',
                  'server/src/main/java/com/workforcehub/leave/repository/LeaveRepository.java']
    if phase == 'baseline':
        for name in production:
            shutil.copy2(out / 'baseline' / name, snapshot / name)
    elif phase in ['modified', 'full']:
        for name in production + ['server/src/test/java/com/workforcehub/leave/LeaveSettlementTransactionTest.java',
                                 'server/src/test/java/com/workforcehub/leave/ModuleLeaveWorkflowIntegrationTest.java']:
            shutil.copy2(repo / name, snapshot / name)
    elif phase == 'rollback':
        assert event('rollback-script', [str(out.parent / 'ROLLBACK.sh'), '--followup', str(snapshot)]) == 0
    else:
        raise ValueError(phase)
    env = dict(base_env, TEST_DB_HOST='127.0.0.1', TEST_DB_PORT=str(meta['mysql_port']),
        TEST_DB_NAME='workforce_993_test', TEST_DB_USER='fixture', TEST_DB_PASSWORD='leave-993-disposable-fixture',
        DB_HOST='127.0.0.1', DB_PORT=str(meta['mysql_port']), DB_NAME='workforce_993_test',
        DB_USER='fixture', DB_PASSWORD='leave-993-disposable-fixture', TEST_REDIS_HOST='127.0.0.1',
        TEST_REDIS_PORT=str(meta['redis_port']), REDIS_HOST='127.0.0.1', REDIS_PORT=str(meta['redis_port']))
    command = ['mvn', '-B', '-Dstyle.color=never']
    if phase == 'full':
        command += ['-Dquota.mysql.url=jdbc:mysql://127.0.0.1:' + str(meta['mysql_port'])
                    + '/quota_2033_test?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true']
    else:
        command += ['-Dtest=ModuleLeaveWorkflowIntegrationTest,ModuleLeaveApiIntegrationTest,LeaveSettlementTransactionTest']
    command += ['clean', 'test']
    code = event(phase, command, cwd=snapshot / 'server', env=env)
    for line in (out / (phase + '.stdout')).read_text().splitlines():
        if 'Tests run:' in line or 'BUILD ' in line or line.startswith('MYSQL_'):
            print(line, flush=True)
    print('EXIT_STATUS=' + str(code), flush=True)
    sys.exit(code)
