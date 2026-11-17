import subprocess,json,os
from pathlib import Path
out=Path(__file__).resolve().parent
work=out/'full-run/server'
env={'PATH':'/opt/homebrew/bin:/usr/bin:/bin','JAVA_HOME':'/Users/sailtrack/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home', 'TEST_DB_HOST':'127.0.0.1', 'TEST_DB_PORT':'1', 'DB_HOST':'127.0.0.1', 'DB_PORT':'1', 'TEST_REDIS_HOST':'127.0.0.1', 'TEST_REDIS_PORT':'1', 'REDIS_HOST':'127.0.0.1', 'REDIS_PORT':'1'}
metadata={'command':['mvn','test'],'cwd':str(work),'environment':env,'note':'No real credentials or .env copied or supplied. Infrastructure tests may fail due to absent credentials.'}
(out/'full-run-command.json').write_text(json.dumps(metadata,indent=2))
with (out/'full.stdout').open('w') as stdout,(out/'full.stderr').open('w') as stderr:
 result=subprocess.run(metadata['command'],cwd=work,env=env,stdout=stdout,stderr=stderr)
(out/'full.exit').write_text(str(result.returncode)+'\n')
print('FULL_EXIT='+str(result.returncode))
for line in (out/'full.stdout').read_text().splitlines():
 if ('Tests run:' in line and ' -- in ' not in line) or 'BUILD SUCCESS' in line or 'BUILD FAILURE' in line: print(line)
