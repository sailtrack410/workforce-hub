#!/bin/sh
set -eu
if [ "$#" -ne 1 ]; then
    echo "usage: ROLLBACK.sh TARGET_COPY_FILE_OR_REPOSITORY_COPY" >&2
    exit 2
fi
artifact_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
filter_path=server/src/main/java/com/workforcehub/auth/security/AdminPermissionFilter.java
test_path=server/src/test/java/com/workforcehub/auth/AdminPermissionFilterTest.java
audit_test_path=server/src/test/java/com/workforcehub/plugin/AdminPluginLifecycleAuditIntegrationTest.java
if [ -f "$1" ]; then
    case $(basename -- "$1") in
        AdminPluginLifecycleAuditIntegrationTest.java)
            cp "$artifact_dir/baseline/$audit_test_path" "$1"
            echo "restored pristine AdminPluginLifecycleAuditIntegrationTest.java"
            ;;
        *)
            cp "$artifact_dir/baseline/$filter_path" "$1"
            echo "restored pristine AdminPermissionFilter.java"
            ;;
    esac
elif [ -d "$1" ]; then
    copy_root=$(CDPATH= cd -- "$1" && pwd)
    cp "$artifact_dir/baseline/$filter_path" "$copy_root/$filter_path"
    cp "$artifact_dir/baseline/$test_path" "$copy_root/$test_path"
    cp "$artifact_dir/baseline/$audit_test_path" "$copy_root/$audit_test_path"
    rm -f "$copy_root/server/src/main/resources/db/migration/V67__add_admin_operations_permissions.sql"
    rm -f "$copy_root/server/src/test/java/com/workforcehub/auth/AdminPermissionRouteMatrixTest.java"
    rm -f "$copy_root/server/src/test/java/com/workforcehub/auth/AdminPermissionWebTest.java"
    rm -f "$copy_root/server/src/test/resources/security/admin-permission-matrix.tsv"
    echo "restored pristine 99-1 source files"
else
    echo "target copy does not exist" >&2
    exit 2
fi
