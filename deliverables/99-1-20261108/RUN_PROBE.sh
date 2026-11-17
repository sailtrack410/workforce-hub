#!/bin/sh
set -eu
artifact_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
probe_classes=$(mktemp -d)
trap 'rm -rf "$probe_classes"' EXIT HUP INT TERM
dependencies=$(cat "$artifact_dir/classpath.txt")
project_classes=$(cat "$artifact_dir/runtime-classes.txt")
javac -cp "$project_classes:$dependencies" -d "$probe_classes" "$1"
java --class-path "$probe_classes:$project_classes:$dependencies" "$artifact_dir/PermissionProbe.java"
