package com.hify.app.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTroubleshootingMigrationTest {

    @Test
    void should_addCreatedByColumn_when_workflowCallTraceUsesBaseEntity() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V44__workflow_call_trace_base_columns.sql"));

        assertThat(migration)
                .contains("ALTER TABLE t_workflow_node_call_trace")
                .contains("ADD COLUMN created_by BIGINT NOT NULL DEFAULT 0 AFTER updated_at");
    }
}
