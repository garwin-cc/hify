package com.hify.knowledge.infra;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

@Configuration
@Profile("!mock")
@EnableConfigurationProperties(PgvectorDataSourceProperties.class)
public class PgvectorDataSourceConfig {

    @Bean
    public DataSource pgvectorDataSource(PgvectorDataSourceProperties properties) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("HikariPool-Pgvector");
        config.setJdbcUrl(properties.getUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(3000);
        return new HikariDataSource(config);
    }

    @Bean
    public JdbcTemplate pgvectorJdbcTemplate(@Qualifier("pgvectorDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate pgvectorNamedJdbcTemplate(
            @Qualifier("pgvectorDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean
    public ApplicationRunner pgvectorMigrationRunner(@Qualifier("pgvectorDataSource") DataSource dataSource) {
        return args -> Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/pgvector")
                .baselineOnMigrate(true)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }
}
