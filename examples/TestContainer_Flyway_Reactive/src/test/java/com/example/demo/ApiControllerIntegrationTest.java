package com.example.demo;

import com.example.demo.controller.ApiController;
import com.example.demo.database.DemoEntity;
import com.example.demo.database.DemoEntityRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import reactor.test.StepVerifier;

@SpringBootTest
public class ApiControllerIntegrationTest {

    private static final String POSTGRES_URL_TEMPLATE = "r2dbc:postgresql://%s:%d/%s";
    static PostgreSQLContainer<?> POSTGRES_CONTAINER = new PostgreSQLContainer<>(
            "postgres:16-alpine"
    );

    @BeforeAll
    static void beforeAll() {
        POSTGRES_CONTAINER.start();

    }

    @AfterAll
    static void afterAll() {
        POSTGRES_CONTAINER.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> POSTGRES_URL_TEMPLATE.formatted(POSTGRES_CONTAINER.getHost(),
                POSTGRES_CONTAINER.getFirstMappedPort(),
                POSTGRES_CONTAINER.getDatabaseName()));
        registry.add("spring.r2dbc.username", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES_CONTAINER::getPassword);

        registry.add("spring.flyway.url", POSTGRES_CONTAINER::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES_CONTAINER::getUsername);
        registry.add("spring.flyway.password", POSTGRES_CONTAINER::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private DemoEntityRepository demoEntityRepository;

    @Autowired
    private ApiController apiController;

    @Test
    void getEntities() {
        demoEntityRepository.save(new DemoEntity()).block();

        var resultFlux = apiController.getEntities();
        StepVerifier.create(resultFlux)
                .expectNextCount(1)
                .expectComplete();
    }
}
