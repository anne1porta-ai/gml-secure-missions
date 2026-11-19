package com.gml.shield;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Socle commun des tests d'integration : un vrai PostgreSQL 16 est lance par Testcontainers.
 * Flyway applique ensuite les migrations du projet et Hibernate valide le mapping JPA.
 */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
abstract class PostgreSqlIntegrationTestBase {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRESQL =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("gmlshield_test")
                    .withUsername("gml_test")
                    .withPassword("gml_test_password");
}