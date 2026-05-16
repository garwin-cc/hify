package com.hify.app.db;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ModelGovernanceMigrationTest {

    @Test
    void should_addCreatedByColumns_when_modelGovernanceTablesUseBaseEntity() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V43__model_governance_base_columns.sql"));

        assertThat(migration)
                .contains("ALTER TABLE t_model_default_policy")
                .contains("ALTER TABLE t_llm_call_stat")
                .contains("ADD COLUMN created_by BIGINT NOT NULL DEFAULT 0 AFTER updated_at");
    }
}
