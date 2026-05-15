package com.hify.workflow.web;

import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.workflow.api.SubmitWorkflowReviewReq;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowRunControllerRoleTest {

    @Test
    void should_require_editor_or_admin_when_submitting_human_review() throws Exception {
        Method method = WorkflowRunController.class.getMethod("submitReview", Long.class, SubmitWorkflowReviewReq.class);
        RequireRole role = method.getAnnotation(RequireRole.class);

        assertThat(role.value()).containsExactlyInAnyOrder(UserRole.ADMIN, UserRole.EDITOR);
    }
}
