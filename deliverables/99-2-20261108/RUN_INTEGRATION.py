"""Disposable, task-owned MySQL/Redis only. Never reads actual .env files."""
from pathlib import Path
import hashlib
import json
import shutil
import socket
import subprocess
import sys
import tempfile
import time

root = Path(__file__).resolve().parents[2]
dest = Path(__file__).resolve().parent
state_file = dest / 'integration-runtime.json'
environment = {'PATH': '/opt/homebrew/bin:/usr/bin:/bin', 'HOME': '/Users/sailtrack',
    'JAVA_HOME': '/Users/sailtrack/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home'}
selection = ','.join([
    'ModuleAttendanceApiIntegrationTest#offboardedEmployeeShouldNotPunchWithExistingSession',
    'ModuleImApiIntegrationTest#offboardedEmployeeShouldNotSendMessageWithOldSession',
    'PluginGatewayPermissionMatrixIntegrationTest', 'EmployeePluginListIntegrationTest',
    'PluginManagerServiceIntegrationTest', 'SessionValidityRequestChainTest',
    'AdminPermissionFilterTest', 'AdminPermissionRouteMatrixTest', 'AdminPermissionWebTest',
    'AdminPluginManagementWebTest', 'PluginGatewaySecurityTest'])


def event(label, command, cwd=None, env=None, timeout=None):
    print('COMMAND_EXECUTION ' + json.dumps({'label': label, 'command': command, 'cwd': str(cwd)}), flush=True)
    with (dest / (label + '.stdout')).open('wb') as stdout, (dest / (label + '.stderr')).open('wb') as stderr:
        result = subprocess.run(command, cwd=cwd, env=env or environment,
            stdout=stdout, stderr=stderr, timeout=timeout)
    (dest / (label + '.exit')).write_text(str(result.returncode))
    (dest / (label + '.event.json')).write_text(json.dumps({'command': command, 'cwd': str(cwd),
        'exit': result.returncode, 'stdout': label + '.stdout', 'stderr': label + '.stderr'}, indent=2))
    return result.returncode


def free_port():
    with socket.socket() as connection:
        connection.bind(('127.0.0.1', 0))
        return connection.getsockname()[1]


phase = sys.argv[1]
if phase == 'setup':
    runtime = Path(tempfile.mkdtemp(prefix='wfh-99-2-integration-', dir='/tmp'))
    mysql_home = Path('/tmp/workforce-quota-2033-runtime/mysql-8.4.7-macos15-arm64')
    mysql_port, redis_port = free_port(), free_port()
    assert mysql_port != redis_port
    meta = {'runtime': str(runtime), 'mysql_home': str(mysql_home), 'mysql_port': mysql_port,
        'redis_port': redis_port, 'snapshot': str(runtime / 'snapshot'),
        'boundary': 'New task-owned datadir and ports; no actual .env or user service credentials'}
    state_file.write_text(json.dumps(meta, indent=2))
    assert event('integration-mysql-init', [str(mysql_home / 'bin/mysqld'), '--no-defaults',
        '--initialize-insecure', '--basedir=' + str(mysql_home), '--datadir=' + str(runtime / 'data')], timeout=60) == 0
    assert event('integration-mysql-start', [str(mysql_home / 'bin/mysqld'), '--no-defaults',
        '--basedir=' + str(mysql_home), '--datadir=' + str(runtime / 'data'), '--bind-address=127.0.0.1',
        '--port=' + str(mysql_port), '--socket=' + str(runtime / 'mysql.sock'),
        '--pid-file=' + str(runtime / 'mysql.pid'), '--log-error=' + str(runtime / 'mysql.log'),
        '--mysqlx=OFF', '--daemonize'], timeout=60) == 0
    sql = "CREATE USER 'fixture'@'%' IDENTIFIED BY 'wfh-992-disposable'; GRANT ALL ON *.* TO 'fixture'@'%';"
    result = subprocess.run([str(mysql_home / 'bin/mysql'), '--no-defaults',
        '--socket=' + str(runtime / 'mysql.sock'), '-uroot'], input=sql.encode(),
        env=environment, capture_output=True)
    assert result.returncode == 0, result.stderr.decode()
    assert event('integration-redis-start', ['/opt/homebrew/bin/redis-server', '--bind', '127.0.0.1',
        '--port', str(redis_port), '--save', '', '--appendonly', 'no', '--dir', str(runtime),
        '--daemonize', 'yes', '--pidfile', str(runtime / 'redis.pid'), '--logfile', str(runtime / 'redis.log')]) == 0
    snapshot = Path(meta['snapshot'])
    (snapshot / 'server').mkdir(parents=True)
    shutil.copyfile(root / 'server/pom.xml', snapshot / 'server/pom.xml')
    for path in ['server/src/main/java', 'server/src/test/java', 'server/src/test/plugin-fixtures',
        'plugins/hello-plugin/src']:
        shutil.copytree(root / path, snapshot / path)
    files = subprocess.run(['git', 'ls-files', 'server/src/main/resources', 'server/src/test/resources',
        'server/scripts/sql'], cwd=root, capture_output=True, text=True, check=True).stdout.splitlines()
    for relative in files:
        if '.env' in Path(relative).name:
            continue
        target = snapshot / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(root / relative, target)
    for path in ['server/src/test/resources/security', 'server/src/main/resources/db/migration']:
        shutil.copytree(root / path, snapshot / path, dirs_exist_ok=True)
    meta['source_hashes'] = {str(p.relative_to(snapshot)): hashlib.sha256(p.read_bytes()).hexdigest()
        for p in snapshot.rglob('*') if p.is_file()}
    state_file.write_text(json.dumps(meta, indent=2))
    print('Isolated MySQL/Redis and current source snapshot ready.', flush=True)
elif phase == 'cleanup':
    meta = json.loads(state_file.read_text())
    runtime = Path(meta['runtime'])
    assert event('integration-redis-stop', ['/opt/homebrew/bin/redis-cli', '-h', '127.0.0.1',
        '-p', str(meta['redis_port']), 'shutdown', 'nosave'], timeout=30) == 0
    assert event('integration-mysql-stop', [str(Path(meta['mysql_home']) / 'bin/mysqladmin'), '--no-defaults',
        '--socket=' + str(runtime / 'mysql.sock'), '-uroot', 'shutdown'], timeout=30) == 0
    for port in [meta['mysql_port'], meta['redis_port']]:
        with socket.socket() as connection:
            assert connection.connect_ex(('127.0.0.1', port)) != 0
    meta['owned_fixture_stopped'] = True
    state_file.write_text(json.dumps(meta, indent=2))
    print('Only task-owned MySQL/Redis stopped; both owned ports closed.', flush=True)
else:
    meta = json.loads(state_file.read_text())
    snapshot = Path(meta['snapshot'])
    state = json.loads((dest / 'state.json').read_text())
    for relative in state['integration_paths']:
        source = dest / 'integration-before' / relative if phase == 'baseline' else root / relative
        shutil.copyfile(source, snapshot / relative)
    env = dict(environment, TEST_DB_HOST='127.0.0.1', TEST_DB_PORT=str(meta['mysql_port']),
        TEST_DB_NAME='workforce_992_test', TEST_DB_USER='fixture', TEST_DB_PASSWORD='wfh-992-disposable',
        DB_HOST='127.0.0.1', DB_PORT=str(meta['mysql_port']), DB_NAME='workforce_992_test',
        DB_USER='fixture', DB_PASSWORD='wfh-992-disposable', TEST_REDIS_HOST='127.0.0.1',
        TEST_REDIS_PORT=str(meta['redis_port']), REDIS_HOST='127.0.0.1', REDIS_PORT=str(meta['redis_port']))
    command = ['mvn', '-B', '-Dstyle.color=never']
    if phase == 'full':
        command.append('-Dquota.mysql.url=jdbc:mysql://127.0.0.1:' + str(meta['mysql_port'])
            + '/quota_2033_test?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true')
    else:
        command.append('-Dtest=' + selection)
    command.extend(['clean', 'test'])
    label = 'integration-' + phase
    code = event(label, command, cwd=snapshot / 'server', env=env)
    for line in (dest / (label + '.stdout')).read_text().splitlines():
        if 'Tests run:' in line or 'BUILD ' in line:
            print(line, flush=True)
    print('EXIT_STATUS=' + str(code), flush=True)
    sys.exit(code)
