package com.hify.app.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowProductionizationMigrationTest {

    @Test
    void should_addCreatedByColumns_when_workflowProductionTablesUseBaseEntity() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V45__workflow_production_base_columns.sql"));

        assertThat(migration)
                .contains("ALTER TABLE t_workflow_publish")
                .contains("ALTER TABLE t_workflow_trigger")
                .contains("ALTER TABLE t_workflow_version_diff")
                .contains("ADD COLUMN created_by BIGINT NOT NULL DEFAULT 0 AFTER updated_at");
    }
}
