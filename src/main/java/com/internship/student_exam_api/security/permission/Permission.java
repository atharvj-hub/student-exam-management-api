package com.internship.student_exam_api.security.permission;

public enum Permission {
    // Student management
    USER_VIEW,
    USER_CREATE,
    USER_UPDATE,
    USER_DELETE,

    // Subject management
    SUBJECT_VIEW,
    SUBJECT_CREATE,
    SUBJECT_UPDATE,
    SUBJECT_DELETE,

    // Exam management
    EXAM_VIEW,
    EXAM_CREATE,
    EXAM_UPDATE,

    // Result management
    RESULT_VIEW,
    RESULT_CREATE,
    RESULT_UPDATE,

    // AI analytics
    AI_INSIGHTS_VIEW
}
