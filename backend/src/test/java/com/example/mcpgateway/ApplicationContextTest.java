package com.example.mcpgateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class ApplicationContextTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsWithSqliteSchema() {
        String applicationName = jdbcTemplate.queryForObject(
                "select application_name from schema_marker where id = 1",
                String.class
        );
        assertThat(applicationName).isEqualTo("mcp-gateway");
    }
}
