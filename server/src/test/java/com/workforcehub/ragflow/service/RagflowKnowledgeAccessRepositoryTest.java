package com.workforcehub.ragflow.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagflowKnowledgeAccessRepositoryTest {
    private KnowledgeAccessTestFixture f;

    @BeforeEach
    void setUp() {
        f = new KnowledgeAccessTestFixture();
    }

    @Test
    void publicRequiresCurrentEligibleEmployeeAndPrivateCreatedByDoesNotGrantEmployeeOwnership() {
        assertTrue(f.repository.canReadSpace(2L, 7L));
        assertFalse(f.repository.canReadSpace(1L, 7L), "管理员创建者与员工ID相同不能授予私有知识");
        assertFalse(f.repository.canReadSpace(2L, 999L));
        assertFalse(f.repository.canReadSpace(999L, 7L));
    }

    @Test
    void onlyTypedEmployeeOwnerGrantsPrivateAccess() {
        for (String type : new String[] {"ADMIN", "ADMIN_USER", "UNKNOWN"}) {
            f.jdbc.update("UPDATE kb_space SET owner_type=?, owner_id=7 WHERE id=1", type);
            assertFalse(f.repository.canReadSpace(1L, 7L), type);
        }
        f.jdbc.update("UPDATE kb_space SET owner_type='USER', owner_id=7 WHERE id=1");
        assertTrue(f.repository.canReadSpace(1L, 7L));
        assertFalse(f.repository.canReadSpace(1L, 8L));
    }

    @Test
    void explicitReadAndManageAclAllowPrivateSpaceButOtherPermissionsDoNot() {
        for (String permission : new String[] {"use", "visible", "write", "UNKNOWN"}) {
            f.grant("USER", 7, permission);
            assertFalse(f.repository.canReadSpace(1L, 7L), permission);
            f.jdbc.update("DELETE FROM kb_space_acl");
        }
        for (String permission : new String[] {"read", "manage"}) {
            f.grant("USER", 7, permission);
            assertTrue(f.repository.canReadSpace(1L, 7L), permission);
            assertFalse(f.repository.canReadSpace(1L, 8L));
            f.jdbc.update("DELETE FROM kb_space_acl");
        }
    }

    @Test
    void departmentAndPositionNeedAnExplicitCurrentMembershipGrant() {
        for (String visibility : new String[] {"DEPARTMENT", "POSITION"}) {
            f.jdbc.update("UPDATE kb_space SET visibility=? WHERE id=1", visibility);
            assertFalse(f.repository.canReadSpace(1L, 7L), "可见范围本身不是授权");
        }
        f.grant("DEPARTMENT", 10, "read");
        assertTrue(f.repository.canReadSpace(1L, 7L));
        assertFalse(f.repository.canReadSpace(1L, 8L));
        f.jdbc.update("UPDATE org_user SET primary_dept_id=20 WHERE id=7");
        assertFalse(f.repository.canReadSpace(1L, 7L));
        f.jdbc.update("DELETE FROM kb_space_acl");
        f.grant("POSITION", 100, "manage");
        assertTrue(f.repository.canReadSpace(1L, 7L));
        f.jdbc.update("UPDATE org_user SET position_id=200 WHERE id=7");
        assertFalse(f.repository.canReadSpace(1L, 7L));
    }

    @Test
    void departmentAndPositionOwnershipUseCurrentActiveOrganizationalObject() {
        for (String type : new String[] {"DEPARTMENT", "POSITION"}) {
            long id = type.equals("DEPARTMENT") ? 10 : 100;
            String table = type.equals("DEPARTMENT") ? "org_department" : "org_position";
            f.jdbc.update("UPDATE kb_space SET owner_type=?, owner_id=? WHERE id=1", type, id);
            assertTrue(f.repository.canReadSpace(1L, 7L), type);
            assertFalse(f.repository.canReadSpace(1L, 8L), type);
            f.jdbc.update("UPDATE " + table + " SET status='INACTIVE' WHERE id=?", id);
            assertFalse(f.repository.canReadSpace(1L, 7L), type);
            f.jdbc.update("UPDATE " + table + " SET status='ACTIVE', is_deleted=1 WHERE id=?", id);
            assertFalse(f.repository.canReadSpace(1L, 7L), type);
            f.jdbc.update("UPDATE " + table + " SET is_deleted=0 WHERE id=?", id);
        }
    }

    @Test
    void departmentAndPositionAclStopWorkingWhenReferencedObjectIsDisabledOrDeleted() {
        for (String type : new String[] {"DEPARTMENT", "POSITION"}) {
            long id = type.equals("DEPARTMENT") ? 10 : 100;
            String table = type.equals("DEPARTMENT") ? "org_department" : "org_position";
            f.grant(type, id, "read");
            assertTrue(f.repository.canReadSpace(1L, 7L));
            f.jdbc.update("UPDATE " + table + " SET status='INACTIVE' WHERE id=?", id);
            assertFalse(f.repository.canReadSpace(1L, 7L));
            f.jdbc.update("UPDATE " + table + " SET status='ACTIVE', is_deleted=1 WHERE id=?", id);
            assertFalse(f.repository.canReadSpace(1L, 7L));
            f.jdbc.update("DELETE FROM kb_space_acl");
        }
    }

    @Test
    void administratorRolesAndUnknownSubjectsNeverImplyEmployeeMembership() {
        for (String type : new String[] {"ROLE", "GROUP", "ADMIN_USER", "UNKNOWN"}) {
            for (long id : new long[] {1, 2, 7}) {
                f.grant(type, id, "read");
                assertFalse(f.repository.canReadSpace(1L, 7L), type + ":" + id);
            }
        }
    }

    @Test
    void aclRevocationIsEffectiveOnTheNextCheck() {
        f.grant("USER", 7, "read");
        assertTrue(f.repository.canReadSpace(1L, 7L));
        f.jdbc.update("UPDATE kb_space_acl SET status='INACTIVE'");
        assertFalse(f.repository.canReadSpace(1L, 7L));
        f.jdbc.update("UPDATE kb_space_acl SET status='ACTIVE'");
        assertTrue(f.repository.canReadSpace(1L, 7L));
        f.jdbc.update("DELETE FROM kb_space_acl");
        assertFalse(f.repository.canReadSpace(1L, 7L));
    }

    @Test
    void publicAndExplicitGrantsNeverOverrideSpaceLifecycleOrUnknownVisibility() {
        f.grant("USER", 7, "manage");
        for (long space : new long[] {1, 2}) {
            f.jdbc.update("UPDATE kb_space SET status='INACTIVE' WHERE id=?", space);
            assertFalse(f.repository.canReadSpace(space, 7L));
            f.jdbc.update("UPDATE kb_space SET status='ACTIVE', is_deleted=1 WHERE id=?", space);
            assertFalse(f.repository.canReadSpace(space, 7L));
            f.jdbc.update("UPDATE kb_space SET is_deleted=0, visibility='UNKNOWN' WHERE id=?", space);
            assertFalse(f.repository.canReadSpace(space, 7L));
        }
    }

    @Test
    void employeeAndAccountRevocationOverridePublicAndPrivateGrants() {
        f.grant("USER", 7, "read");
        for (long space : new long[] {1, 2}) {
            for (String employment : new String[] {"PROBATION", "ACTIVE", "PENDING_OFFBOARD"}) {
                f.jdbc.update("UPDATE org_user SET employment_status=? WHERE id=7", employment);
                assertTrue(f.repository.canReadSpace(space, 7L), employment);
            }
            for (String employment : new String[] {"PENDING_ONBOARD", "OFFBOARDED", "UNKNOWN"}) {
                f.jdbc.update("UPDATE org_user SET employment_status=? WHERE id=7", employment);
                assertFalse(f.repository.canReadSpace(space, 7L), employment);
            }
            f.jdbc.update("UPDATE org_user SET employment_status='ACTIVE', status='INACTIVE' WHERE id=7");
            assertFalse(f.repository.canReadSpace(space, 7L));
            f.jdbc.update("UPDATE org_user SET status='ACTIVE', is_deleted=1 WHERE id=7");
            assertFalse(f.repository.canReadSpace(space, 7L));
            f.jdbc.update("UPDATE org_user SET is_deleted=0 WHERE id=7");
            f.jdbc.update("UPDATE auth_user_account SET account_status='LOCKED' WHERE user_id=7");
            assertFalse(f.repository.canReadSpace(space, 7L));
            f.jdbc.update("UPDATE auth_user_account SET account_status='ACTIVE', is_deleted=1 WHERE user_id=7");
            assertFalse(f.repository.canReadSpace(space, 7L));
            f.jdbc.update("UPDATE auth_user_account SET is_deleted=0 WHERE user_id=7");
        }
        f.jdbc.update("DELETE FROM auth_user_account WHERE user_id=7");
        assertFalse(f.repository.canReadSpace(2L, 7L));
    }
}
