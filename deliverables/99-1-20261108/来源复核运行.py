from pathlib import Path
import hashlib
import json
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent
MODE = sys.argv[1]
BINARY = Path('/tmp/workforce-quota-2033-runtime/mysql-8.4.7-macos15-arm64')
BASE = json.loads((OUT / '来源复核-执行.json').read_text())
WORK = Path(tempfile.mkdtemp(prefix='workforce-review-final-'))
SNAPSHOT = WORK / 'source'
PRIOR = Path(BASE['snapshot'])
ENV = {
    'PATH': '/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin',
    'LANG': 'en_US.UTF-8',
    'JAVA_HOME': '/Users/sailtrack/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home',
}
EVENTS = []


def free_port():
    with socket.socket() as connection:
        connection.bind(('127.0.0.1', 0))
        return connection.getsockname()[1]


def execute(label, command, cwd=WORK, environment=ENV):
    print('COMMAND_EXECUTION', label, json.dumps(command), flush=True)
    with (OUT / f'复核-{MODE}-{label}.stdout').open('w') as stdout:
        with (OUT / f'复核-{MODE}-{label}.stderr').open('w') as stderr:
            result = subprocess.run(command, cwd=cwd, env=environment, stdout=stdout, stderr=stderr)
    EVENTS.append({'label': label, 'command': command, 'cwd': str(cwd), 'exit': result.returncode})
    for line in (OUT / f'复核-{MODE}-{label}.stdout').read_text().splitlines():
        if 'Tests run:' in line or 'BUILD ' in line or 'RACE results=' in line:
            print(line, flush=True)
    print(label, 'exit=', result.returncode, flush=True)
    return result.returncode


# Only copy known sanitized source trees. Never copy live application configuration or .env.
for relative in ['server/pom.xml', 'pom.xml']:
    origin = PRIOR / relative
    if origin.exists():
        destination = SNAPSHOT / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(origin, destination)
for relative in ['server/src', 'server/scripts', 'plugins', 'plugin-sdk']:
    if (PRIOR / relative).exists():
        shutil.copytree(PRIOR / relative, SNAPSHOT / relative)
for relative in ['server/src/main/java', 'server/src/test/java',
                 'server/src/main/resources/db/migration', 'server/src/test/resources/security',
                 'server/src/test/plugin-fixtures']:
    if (ROOT / relative).exists():
        shutil.copytree(ROOT / relative, SNAPSHOT / relative, dirs_exist_ok=True)
shutil.copyfile(ROOT / 'server/pom.xml', SNAPSHOT / 'server/pom.xml')
assert not (SNAPSHOT / '.env').exists()
assert not (SNAPSHOT / 'server/.env').exists()
hashes = {str(p.relative_to(SNAPSHOT)): hashlib.sha256(p.read_bytes()).hexdigest()
          for p in (SNAPSHOT / 'server/src').rglob('*') if p.is_file()}
mysql_port, redis_port = free_port(), free_port()
mysql_socket = str(WORK / 'mysql.sock')
mysql_started = redis_started = False
try:
    initialize = [str(BINARY / 'bin/mysqld'), '--no-defaults', '--initialize-insecure',
                  '--basedir=' + str(BINARY), '--datadir=' + str(WORK / 'data')]
    assert execute('initialize', initialize) == 0
    launch = [str(BINARY / 'bin/mysqld'), '--no-defaults', '--basedir=' + str(BINARY),
              '--datadir=' + str(WORK / 'data'), '--bind-address=127.0.0.1',
              '--port=' + str(mysql_port), '--socket=' + mysql_socket,
              '--pid-file=' + str(WORK / 'mysql.pid'), '--log-error=' + str(WORK / 'mysql.log'),
              '--mysqlx=OFF', '--daemonize']
    assert execute('mysql-start', launch) == 0
    mysql_started = True
    redis = ['/opt/homebrew/bin/redis-server', '--bind', '127.0.0.1', '--port', str(redis_port),
             '--save', '', '--appendonly', 'no', '--dir', str(WORK), '--pidfile',
             str(WORK / 'redis.pid'), '--logfile', str(WORK / 'redis.log'), '--daemonize', 'yes']
    assert execute('redis-start', redis) == 0
    redis_started = True
    password = secrets.token_hex(24)
    sql = ("CREATE USER 'review_fixture'@'127.0.0.1' IDENTIFIED BY '" + password
           + "'; GRANT ALL PRIVILEGES ON *.* TO 'review_fixture'@'127.0.0.1'; FLUSH PRIVILEGES;")
    created = subprocess.run([str(BINARY / 'bin/mysql'), '--no-defaults', '--socket=' + mysql_socket,
                              '-uroot'], input=sql, text=True, capture_output=True, env=ENV)
    assert created.returncode == 0, 'Disposable fixture user creation failed'
    environment = ENV | {
        'TEST_DB_HOST': '127.0.0.1', 'TEST_DB_PORT': str(mysql_port),
        'TEST_DB_NAME': 'workforce_review_final_test', 'TEST_DB_USER': 'review_fixture',
        'TEST_DB_PASSWORD': password, 'TEST_REDIS_HOST': '127.0.0.1',
        'TEST_REDIS_PORT': str(redis_port),
    }
    url = ('-Dquota.mysql.url=jdbc:mysql://127.0.0.1:' + str(mysql_port)
           + '/quota_2033_test?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true')
    if MODE == 'quota':
        artifact = ROOT / 'deliverables/20-3-3-20261108'
        manifest = json.loads((artifact / 'manifest.json').read_text())
        baseline = WORK / 'baseline'
        shutil.copytree(SNAPSHOT, baseline)
        for relative, state in manifest.items():
            if state.get('baseline'):
                shutil.copyfile(artifact / 'BASELINE' / relative, baseline / relative)
        rollback = WORK / 'rollback'
        shutil.copytree(SNAPSHOT, rollback)
        assert execute('rollback-script', [str(artifact / 'ROLLBACK.sh'), str(rollback)]) == 0
        assert all(hashlib.sha256((rollback / p).read_bytes()).hexdigest() == state['baseline']
                   for p, state in manifest.items() if state.get('baseline'))
        fixture = ROOT / 'server/src/test/java/com/workforcehub/file/FileQuotaConcurrencyTest.java'
        shutil.copyfile(fixture, rollback / 'server/src/test/java/com/workforcehub/file/FileQuotaConcurrencyTest.java')
        for label, source in [('BASELINE', baseline), ('MODIFIED', SNAPSHOT), ('ROLLBACK', rollback)]:
            execute(label, ['mvn', '-B', '-Dstyle.color=never', '-Dtest=FileQuotaConcurrencyTest',
                            url, 'clean', 'test'], source / 'server', environment)
    else:
        execute('full', ['mvn', '-B', '-Dstyle.color=never', url, 'clean', 'test'],
                SNAPSHOT / 'server', environment)
finally:
    if redis_started:
        execute('redis-stop', ['/opt/homebrew/bin/redis-cli', '-h', '127.0.0.1', '-p',
                               str(redis_port), 'shutdown', 'nosave'])
    if mysql_started:
        execute('mysql-stop', [str(BINARY / 'bin/mysqladmin'), '--no-defaults',
                               '--socket=' + mysql_socket, '-uroot', 'shutdown'])
    (OUT / f'复核-{MODE}-执行.json').write_text(json.dumps({
        'snapshot': str(SNAPSHOT), 'work': str(WORK), 'source_hashes': hashes,
        'events': EVENTS, 'boundary': 'Generated disposable fixture only; no live credentials or app processes',
    }, ensure_ascii=False, indent=2))
