#!/usr/bin/env python3
"""Run the same production calculator probe in a disposable source-only build."""
from pathlib import Path
import json
import os
import shutil
import subprocess
import sys
import tempfile

artifacts = Path(__file__).resolve().parent
root = artifacts.parent.parent
tree = Path(sys.argv[1]).resolve()
phase = sys.argv[2]
work = Path(tempfile.mkdtemp(prefix="workforce-99-4-"))
server = work / "server"
server.mkdir()
shutil.copyfile(root / "server/pom.xml", server / "pom.xml")
for source in (root / "server/src/main/java").rglob("*.java"):
    destination = server / source.relative_to(root / "server")
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)
for name in (artifacts / "paths.txt").read_text().splitlines():
    if not name.startswith("server/src/main/java/"):
        continue
    destination = work / name
    destination.unlink(missing_ok=True)
    source = tree / name
    if source.exists():
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, destination)
probe = server / "src/test/java/com/workforcehub/workflow/LeaveDurationEvidenceTest.java"
probe.parent.mkdir(parents=True)
shutil.copyfile(artifacts / "evidence/LeaveDurationEvidenceTest.java", probe)
command = ["mvn", "-q", "-Dtest=LeaveDurationEvidenceTest", "test"]
environment = os.environ.copy()
environment["JAVA_HOME"] = "/Users/sailtrack/Library/Java/JavaVirtualMachines/ms-17.0.18/Contents/Home"
result = subprocess.run(command, cwd=server, env=environment, capture_output=True)
(artifacts / f"{phase}.stdout").write_bytes(result.stdout)
(artifacts / f"{phase}.stderr").write_bytes(result.stderr)
(artifacts / f"{phase}.exit").write_text(str(result.returncode) + "\n")
(artifacts / f"{phase}.command.json").write_text(json.dumps({"command":command,"cwd":str(server),"source_tree":str(tree),"exit":result.returncode},ensure_ascii=False,indent=2)+"\n")
sys.stdout.buffer.write(result.stdout)
sys.stderr.buffer.write(result.stderr)
shutil.rmtree(work)
sys.exit(result.returncode)
