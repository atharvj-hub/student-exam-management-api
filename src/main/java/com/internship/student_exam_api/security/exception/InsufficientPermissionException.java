package com.internship.student_exam_api.security.exception;

import com.internship.student_exam_api.security.permission.Permission;

public class InsufficientPermissionException extends RuntimeException {

    private final Permission requiredPermission;

    public InsufficientPermissionException(Permission required) {
        super("Access denied. Required permission: " + required.name());
        this.requiredPermission = required;
    }

    public Permission getRequiredPermission() {
        return requiredPermission;
    }
}
