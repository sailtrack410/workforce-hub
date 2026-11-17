from pathlib import Path
import sqlite3

artifact = Path(__file__).resolve().parent
sql = (artifact / "modified/server/src/main/resources/db/migration/V67__add_admin_operations_permissions.sql").read_text()
db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE admin_permission (
 id INTEGER PRIMARY KEY, perm_code TEXT UNIQUE, perm_name TEXT, module_code TEXT,
 action_code TEXT, status TEXT, created_by INTEGER, updated_by INTEGER, is_deleted INTEGER
);
CREATE TABLE admin_role (id INTEGER PRIMARY KEY, role_code TEXT UNIQUE, is_deleted INTEGER);
CREATE TABLE admin_role_permission (
 id INTEGER PRIMARY KEY, role_id INTEGER, permission_id INTEGER, created_by INTEGER,
 UNIQUE(role_id, permission_id)
);
INSERT INTO admin_role VALUES (1, 'SUPER_ADMIN', 0), (2, 'OTHER', 0);
INSERT INTO admin_permission VALUES (7100, 'UNRELATED', 'untouched', 'OTHER', 'MANAGE', 'ACTIVE', NULL, NULL, 0);
INSERT INTO admin_permission VALUES (7200, 'ADMIN_ALERT_MANAGE', 'old', 'OLD', 'OLD', 'INACTIVE', NULL, NULL, 1);
INSERT INTO admin_role_permission VALUES (7300, 2, 7100, NULL);
""")
unrelated = db.execute("SELECT * FROM admin_permission WHERE id=7100").fetchone()
db.executescript(sql)
first = [db.execute("SELECT * FROM " + table + " ORDER BY id").fetchall()
         for table in ("admin_permission", "admin_role_permission")]
db.executescript(sql)
second = [db.execute("SELECT * FROM " + table + " ORDER BY id").fetchall()
          for table in ("admin_permission", "admin_role_permission")]
assert first == second
assert db.execute("SELECT * FROM admin_permission WHERE id=7100").fetchone() == unrelated
for code in ("ADMIN_ALERT_MANAGE", "ADMIN_IM_OUTBOX_MANAGE", "ADMIN_AUDIT_LOG_VIEW"):
    permission_id, status, deleted = db.execute(
        "SELECT id,status,is_deleted FROM admin_permission WHERE perm_code=?", (code,)).fetchone()
    assert (status, deleted) == ("ACTIVE", 0)
    assert db.execute("SELECT role_id FROM admin_role_permission WHERE permission_id=?", (permission_id,)).fetchall() == [(1,)]
assert db.execute("SELECT * FROM admin_role_permission WHERE id=7300").fetchone() == (7300, 2, 7100, None)
print("SQLite fixture: 3 permissions active; SUPER_ADMIN grants only; existing IDs preserved; rerun idempotent")
print("Boundary: exact SQL executed on SQLite fixture; MySQL/Flyway deployment not verified")
