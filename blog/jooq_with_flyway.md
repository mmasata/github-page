# JOOQ generation setup together with Flyway in SpringBoot application

This article aims to outline the problem of generating JOOQ code when Flyway is also used in SpringBoot
application. The article will also include sample code including a working application where the ideal variant of how to
to solve the problem.

## Problem Outline

We have a SpringBoot application that needs Flyway for database migration. Since we don't want to manually run these
scripts,
and we want to make sure we have the latest version of the database on each environment, so we're going to run the
migration for
startup.

JOOQ meanwhile generates the code in the build phase, which unfortunately we can't control. Of course we can use JOOQ
without generating
code, but this will rob us of its biggest advantage, which is writing type-safe SQL queries. And if we want to write SQL
queries
without type-safe, we can write queries as plain String.

Now we know we want to write type-safe queries, so we have to use the JOOQ code generator in the build phase. We also
know that
we want to migrate using Flyway at startup. This is where the whole problem comes up - how do we generate the output
from the JOOQ code generator,
if we don't have the database initialized yet? Without the database, there is nothing for the JOOQ code generator to
generate...

![ChickenEgg](https://static.wikia.nocookie.net/wakypedia/images/3/32/ChickenEgg.jpg)

## Possible Solutions

### a) Execute Flyway migration during build phase

The simplest solution seems to be to move Flyway migration to the build phase as well. This way, during the build phase
we will first perform
migration and then the JOOQ code generator connects to the database and generates code from it. Everything works, and
everyone is
happy. But is that right?

That was of course a rhetorical question, the answer is as when. Migrating in the build phase has its advantages and
disadvantages, it is necessary to
look at your specific scenario and determine which option suits you best. If it's a build phase migration, then
great, this solution will save you hours of suffering.

#### Migration during Build phase

- Migration starts only in Build phase, application startup will be faster (+)
- Inconsistency between environments (-)

#### Migration at Startup

- Migration is triggered at every startup, ensuring that we have the most up-to-date version on each environment (+)
- Automatic startup (no need to configure anything, just add a dependency. SpringBoot is friends with Flyway) (+)
- Slower application startup (-)

In the sample below you can see that the executions of both plugins are in the same "generate-sources" phase. For the
flyway configuration, we give
default path to the migration scripts /resources/db/migration (if we had them stored elsewhere, we would also have to
edit here). For the JOOQ plugin through includes via regex pattern we say we want includes everything, only in exclude
we have
flyway_schema_history (we don't want to generate a flyway table, it's not important to us).

```xml

<plugins>
    <plugin>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-maven-plugin</artifactId>
        <version>${flyway-maven-plugin.version}</version>

        <executions>
            <execution>
                <phase>generate-sources</phase>
                <goals>
                    <goal>migrate</goal>
                </goals>
            </execution>
        </executions>

        <configuration>
            <url>${db.url}</url>
            <user>${db.username}</user>
            <locations>
                <location>filesystem:src/main/resources/db/migration</location>
            </locations>
        </configuration>
    </plugin>

    <plugin>
        <groupId>org.jooq</groupId>
        <artifactId>jooq-codegen-maven</artifactId>
        <version>${org.jooq.version}</version>
        <executions>
            <execution>
                <phase>generate-sources</phase>
                <goals>
                    <goal>generate</goal>
                </goals>
            </execution>
        </executions>

        <configuration>
            <jdbc>
                <url>${db.url}</url>
                <user>${db.username}</user>
            </jdbc>
            <generator>
                <database>
                    <includes>.*</includes>
                    <excludes>flyway_schema_history</excludes>
                    <inputSchema>public</inputSchema>
                </database>
                <target>
                    <packageName>org.jooq.example.generated.jooq</packageName>
                    <directory>target/generated-sources/jooq</directory>
                </target>
            </generator>
        </configuration>
    </plugin>
</plugins>
```

### b) Generate via DDLDatabase extension

Another possible option is to generate it via the DDLDatabase extension. We specify the path to the .sql scripts and via
parameterization we specify in what order the files should be loaded. DDLDatabase extension internally starts in-memory
H2
database, into which it loads the database model from the input scripts.

```xml

<configuration>
    <generator>
        <database>
            <name>org.jooq.meta.extensions.ddl.DDLDatabase</name>
            <properties>

                <property>
                    <key>scripts</key>
                    <value>src/main/resources/*.sql</value>
                </property>

                <property>
                    <key>sort</key>
                    <value>flyway</value>
                </property>

            </properties>
        </database>
    </generator>
</configuration>
```

My personal view is that I would never choose this option. The main reason is that the H2 database has a very limited
functionality compared to a "full-featured" database. So if I have migration scripts, for example, made specifically for
PostgresSQL
and I have some specifics just for that database, then two things can happen:

a) H2 database is not even initialized because it is not able to
b) H2 database is initialized with limited functionality or slightly differently and the resulting generated code may
not
work against a real database

### c) Use TestContainers (preferred solution)

The third and final option is to use TestContainers. Since we want to generate JOOQ code against the real database, but
it
is not yet initialized, we can start a Docker container in the build phase to migrate into it. Thanks to
Docker container, we'll generate the code and then just throw the container away.

From the description itself, it may seem that this will not be a trivial matter, but the opposite is true. Exactly for
these purposes
directly from testcontainers, the "testcontainers-jooq-codegen-maven-plugin" plugin was created, to which you only need
to pass the necessary
parameters and you're done.

The plugin adds dependencies for postgresql in the sample, if you use another database, you only need to edit it. In the
config you then set the jooq, flyway and database type.

```xml

<plugin>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers-jooq-codegen-maven-plugin</artifactId>
    <version>${testcontainers-jooq-codegen-maven-plugin.version}</version>

    <dependencies>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <version>${test-containers.version}</version>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <version>${postgresql.version}</version>
        </dependency>
    </dependencies>

    <executions>
        <execution>
            <id>generate-jooq-sources</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>generate</goal>
            </goals>
            <configuration>
                <database>
                    <type>POSTGRES</type>
                    <containerImage>postgres:15-alpine</containerImage>
                </database>
                <flyway>
                    <locations>filesystem:src/main/resources/db/migration</locations>
                </flyway>
                <jooq>
                    <generator>
                        <database>
                            <includes>.*</includes>
                            <excludes>flyway_schema_history</excludes>
                            <inputSchema>public</inputSchema>
                        </database>
                        <target>
                            <packageName>org.jooq.example.generated.jooq</packageName>
                            <directory>target/generated-sources/jooq</directory>
                        </target>
                    </generator>
                </jooq>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## Conclusion

I have outlined three possible solutions for this problem. Personally, I would prefer the first or the last option, but
as I mentioned, it depends on the situation.