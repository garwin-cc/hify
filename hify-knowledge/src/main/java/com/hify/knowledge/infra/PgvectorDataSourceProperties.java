package com.hify.knowledge.infra;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "hify.postgres")
public class PgvectorDataSourceProperties {

    private String url = "jdbc:postgresql://localhost:5433/hify_vector";

    private String username = "hify";

    private String password = "hify_pg123";
}
