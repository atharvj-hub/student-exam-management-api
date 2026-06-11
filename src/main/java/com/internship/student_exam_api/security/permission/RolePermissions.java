package com.internship.student_exam_api.security.permission;

import com.internship.student_exam_api.enums.Role;
import java.util.EnumSet;
import java.util.Set;

public final class RolePermissions {

    private RolePermissions() {}

    private static final Set<Permission> ADMIN_PERMISSIONS = EnumSet.of(
        Permission.USER_VIEW,
        Permission.USER_CREATE,
        Permission.USER_UPDATE,
        Permission.USER_DELETE,
        Permission.SUBJECT_VIEW,
        Permission.SUBJECT_CREATE,
        Permission.SUBJECT_UPDATE,
        Permission.SUBJECT_DELETE,
        Permission.EXAM_VIEW,
        Permission.EXAM_CREATE,
        Permission.EXAM_UPDATE,
        Permission.RESULT_VIEW,
        Permission.RESULT_CREATE,
        Permission.RESULT_UPDATE,
        Permission.ANALYTICS_VIEW,
        Permission.AI_INSIGHTS_VIEW
    );

    private static final Set<Permission> STUDENT_PERMISSIONS = EnumSet.of(
        Permission.USER_VIEW,
        Permission.SUBJECT_VIEW,
        Permission.EXAM_VIEW,
        Permission.RESULT_VIEW
    );

    public static Set<Permission> forRole(Role role) {
        return switch (role) {
            case ADMIN -> EnumSet.copyOf(ADMIN_PERMISSIONS);
            case STUDENT -> EnumSet.copyOf(STUDENT_PERMISSIONS);
        };
    }
}
