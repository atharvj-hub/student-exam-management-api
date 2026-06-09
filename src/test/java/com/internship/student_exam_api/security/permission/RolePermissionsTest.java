package com.internship.student_exam_api.security.permission;

import com.internship.student_exam_api.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RolePermissions — the single source of truth
 * for role-to-permission mapping.
 */
class RolePermissionsTest {

    @Test
    @DisplayName("ADMIN should have every defined permission")
    void adminShouldHaveEveryDefinedPermission() {
        Set<Permission> adminPerms = RolePermissions.forRole(Role.ADMIN);

        assertEquals(EnumSet.allOf(Permission.class), adminPerms,
            "ADMIN must have ALL permissions defined in the Permission enum");
    }

    @Test
    @DisplayName("ADMIN permission count should equal Permission enum size")
    void adminPermissionCountShouldEqualEnumSize() {
        Set<Permission> adminPerms = RolePermissions.forRole(Role.ADMIN);

        assertEquals(Permission.values().length, adminPerms.size(),
            "ADMIN permission count must match total Permission enum constants");
    }

    @Test
    @DisplayName("STUDENT should have exactly 4 view-only permissions")
    void studentShouldHaveExactly4ViewPermissions() {
        Set<Permission> studentPerms = RolePermissions.forRole(Role.STUDENT);

        assertEquals(4, studentPerms.size());
        assertTrue(studentPerms.containsAll(Set.of(
            Permission.USER_VIEW,
            Permission.SUBJECT_VIEW,
            Permission.EXAM_VIEW,
            Permission.RESULT_VIEW)));
    }

    @Test
    @DisplayName("STUDENT should NOT have any write or delete permissions")
    void studentShouldNotHaveWritePermissions() {
        Set<Permission> studentPerms = RolePermissions.forRole(Role.STUDENT);

        assertFalse(studentPerms.contains(Permission.USER_CREATE));
        assertFalse(studentPerms.contains(Permission.USER_UPDATE));
        assertFalse(studentPerms.contains(Permission.USER_DELETE));
        assertFalse(studentPerms.contains(Permission.SUBJECT_CREATE));
        assertFalse(studentPerms.contains(Permission.SUBJECT_UPDATE));
        assertFalse(studentPerms.contains(Permission.SUBJECT_DELETE));
        assertFalse(studentPerms.contains(Permission.EXAM_CREATE));
        assertFalse(studentPerms.contains(Permission.EXAM_UPDATE));
        assertFalse(studentPerms.contains(Permission.RESULT_CREATE));
        assertFalse(studentPerms.contains(Permission.RESULT_UPDATE));
        assertFalse(studentPerms.contains(Permission.AI_INSIGHTS_VIEW));
    }

    @Test
    @DisplayName("forRole should return a defensive copy — mutations must not affect future calls")
    void forRoleShouldReturnDefensiveCopy() {
        Set<Permission> first = RolePermissions.forRole(Role.ADMIN);
        int originalSize = first.size();

        // Mutate the returned set
        first.remove(Permission.USER_DELETE);

        // A second call must return the original, un-mutated set
        Set<Permission> second = RolePermissions.forRole(Role.ADMIN);
        assertEquals(originalSize, second.size());
        assertTrue(second.contains(Permission.USER_DELETE),
            "Internal permission set must not be affected by external mutation");
    }

    @Test
    @DisplayName("ADMIN and STUDENT permission sets should not be the same reference")
    void adminAndStudentShouldBeDifferentSets() {
        Set<Permission> admin = RolePermissions.forRole(Role.ADMIN);
        Set<Permission> student = RolePermissions.forRole(Role.STUDENT);

        assertNotSame(admin, student);
        assertNotEquals(admin, student);
    }
}
