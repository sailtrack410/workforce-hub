#!/usr/bin/env python3
"""Run identical evaluator input against a disposable target copy and retain literal output."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

artifact = Path(__file__).resolve().parent
label, root = sys.argv[1], Path(sys.argv[2]).resolve()
fixture = artifact / 'regression/FileQuotaConcurrencyTest.java'
shutil.copy2(fixture, root / 'server/src/test/java/com/workforcehub/file/FileQuotaConcurrencyTest.java')
command = ['mvn', '-B', '-Dtest=FileQuotaConcurrencyTest',
           '-Dquota.mysql.url=jdbc:mysql://127.0.0.1:23383/quota_2033_test?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true', 'test']
env = {'PATH': '/opt/homebrew/bin:/usr/bin:/bin', 'HOME': '/Users/sailtrack',
       'JAVA_HOME': '/Users/sailtrack/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home'}
print('COMMAND_EXECUTION ' + json.dumps({'label': label, 'cwd': str(root / 'server'), 'command': command}), flush=True)
result = subprocess.run(command, cwd=root / 'server', env=env, capture_output=True)
(artifact / (label + '.stdout')).write_bytes(result.stdout)
(artifact / (label + '.stderr')).write_bytes(result.stderr)
event = {'label': label, 'cwd': str(root / 'server'), 'command': command,
         'input_sha256': hashlib.sha256(fixture.read_bytes()).hexdigest(),
         'exit_status': result.returncode, 'stdout': label + '.stdout', 'stderr': label + '.stderr'}
(artifact / (label + '.event.json')).write_text(json.dumps(event, indent=2) + '\n')
for line in result.stdout.decode().splitlines():
    if 'RACE results=' in line or 'Tests run:' in line or 'BUILD ' in line:
        print(line)
if result.stderr:
    print(result.stderr.decode(), file=sys.stderr)
print('EXIT_STATUS=' + str(result.returncode), flush=True)
sys.exit(result.returncode)
