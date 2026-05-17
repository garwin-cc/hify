package com.hify.knowledge.infra;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class PgvectorKnowledgeVectorRepositoryTest {

    @Test
    void should_keepPgvectorQualifier_when_constructorInjectionIsUsed() throws NoSuchMethodException {
        Constructor<PgvectorKnowledgeVectorRepository> constructor =
                PgvectorKnowledgeVectorRepository.class.getConstructor(NamedParameterJdbcTemplate.class);

        Qualifier qualifier = constructor.getParameters()[0].getAnnotation(Qualifier.class);

        assertThat(qualifier).isNotNull();
        assertThat(qualifier.value()).isEqualTo("pgvectorNamedJdbcTemplate");
    }
}
