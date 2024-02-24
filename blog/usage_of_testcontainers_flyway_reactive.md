# Setup of TestContainers with Flyway and SpringBoot Reactive Web Service

This tutorial is intended to show how to make TestContainers work in Spring integration tests, assuming that
the application uses both Flyway and a reactive approach.

## Basic introduction to the used technologies

### What is [Flyway](https://flywaydb.org/)

Flyway is an open-source tool for managing database migrations. Its main purpose is to facilitate the management of
database version
schema and data structures over time. Flyway allows developers and database administrators to track changes in the
database
using migration scripts and automatically apply them to the target database.

#### Example

For example, we have a new application where we know we need the 'Customer' table in the relational database. So we
create the first migration script.

* V1__create_customer.sql

```sql
CREATE TABLE customer
(
    id         UUID PRIMARY KEY,
    first_name TEXT NOT NULL,
    last_name  TEXT NOT NULL
);
```

This is enough for a while. Eventually a request will come in to add a row with the user's age. In this case, we create
a new SQL that will make the changes.

* V2__add_age_column.sql

```sql
ALTER TABLE customer
    ADD age INT;
```

### What is Reactive RESTful Web Service

Reactive programming is a programming paradigm focused on dealing with asynchronous data streams and the propagation of
changes.

In this case, the [Project Reactor](https://projectreactor.io/) library will be used, which is a designed abstraction
for a non-blocking approach. The main entities here are **Mono** (representing a stream with 0..1 values) and **Flux** (
0...N values).

#### Example

##### Servlet approach

```java

@GetMapping(path = "/{id}")
public User getUserById(@PathVariable Integer id) {
    //some logic...
}

@GetMapping
public List<User> getAllUsers() {
    //some logic...
}
```

##### Reactive approach

```java

@GetMapping(path = "/{id}")
public Mono<User> getUserById(@PathVariable Integer id) {
    //some logic...
}

@GetMapping
public Flux<User> getAllUsers() {
    //some logic...
}
```

### What is [TestContainers](https://testcontainers.com/)

> Testcontainers is a library that provides easy and lightweight APIs for bootstrapping local development and test
> dependencies with real services wrapped in Docker containers. Using Testcontainers, you can write tests that depend on
> the same services you use in production without mocks or in-memory services.
>
> -- <cite>https://testcontainers.com/getting-started/#what-is-testcontainers</cite>

In our case we will have a relational database on top of the RESTful service and in integration tests we will not be
satisfied with just mocking the Repository layer, but we will want to have tests as close to reality as possible.

Therefore, in the integration test we will want to run a Docker Container with the given database against which the
integration tests will run and discard the Container when the test is finished.

## JDBC vs R2DBC

JDBC (Java Database Connectivity) is an API for programmers to access relational databases. It is a long-standing
standard that has proven itself. One of the main limitations is that it uses a blocking I/O model. For this reason, it
is not very
appropriate to use JDBC in reactive applications.

Because of the need for a non-blocking I/O model, R2DBC (Reactive Relational Database Connectivity) was created. The
ideal choice for
our reactive application.

## Problem outline

Since Flyway only supports JDBC, we will have to create a Container that can communicate over both JDBC and R2DBC at the
same time. We also need to make sure that the flyway migration takes place before the tests themselves.

We have a reactive application with a database, and we use Flyway for script migration. This is what **pom.xml** looks like:

```xml

<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.0-SNAPSHOT</version>
    <relativePath/> <!-- lookup parent from repository -->
</parent>

<dependencies>

<!-- REACTIVE DEPENDENCIES -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-r2dbc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>


<!-- DB DEPENDENCIES -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>r2dbc-postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- FLYWAY DEPENDENCY -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
</dependencies>
```

We have defined migration scripts in **/resources/db/migration**:

```sql
CREATE SEQUENCE id_sequence;

CREATE TABLE demo_entity
(
    id   BIGINT PRIMARY KEY DEFAULT nextval('id_sequence'),
    data TEXT
);
```

Let's create a JPA entity and a Repository interface:

```java

@Data
@Table(name = "demo_entity")
public class DemoEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("data")
    private String data;
}

@Repository
public interface DemoEntityRepository extends ReactiveCrudRepository<DemoEntity, UUID> {
}
```

And there is one REST API Controller that performs GET and POST operation:

```java

@RestController
@RequestMapping(path = "/demo_entity")
@RequiredArgsConstructor
public class ApiController {

    private final DemoEntityRepository demoEntityRepository;

    @GetMapping
    public Flux<DemoEntity> getEntities() {
        return demoEntityRepository.findAll();
    }

    @PostMapping
    public Mono<DemoEntity> createEntity(@RequestBody Mono<CreateEntityRequest> requestMono) {
        return requestMono.flatMap(request -> {

            var newDemoEntity = new DemoEntity();
            newDemoEntity.setData(request.data());
            return demoEntityRepository.save(newDemoEntity);
        });
    }
}
```

## Solution

First we will add all necessary dependencies in **pom.xml** to work with the database and testcontainers.

```xml

<dependencies>
    <!-- TEST DEPENDENCIES -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.projectreactor</groupId>
        <artifactId>reactor-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>r2dbc</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Now let's create a simple integration test that first adds one record to the database and then calls the controller
to get all the records in the database:

```java

@SpringBootTest
public class ApiControllerIntegrationTest {

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
```

At this stage, the integration test is set up correctly, but we don't get the TestContainer running when we run the integration test.
So we need to set the TestContainer to run before the first integration test and finish after the last integration test.
```java
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
```


Now the container starts and stops exactly as we need it. Finally, we need to pair the information from the container to the application context so that the application can connect to the database correctly.
```java
private static final String POSTGRES_URL_TEMPLATE = "r2dbc:postgresql://%s:%d/%s";

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
```


And now, after running the integration test, the TestContainer should be started, which runs the migration scripts immediately after starting. After that, the integration tests will start, where the application will be able to connect to the database via R2DBC.
The full sample application is available here: https://github.com/mmasata/mmasata.github.io/tree/master/examples/TestContainer_Flyway_Reactive




**Warning**: The solution works with a specific PostgreSQL relational database, so if you are using another database, you must
modify dependencies and image containers in the test.

