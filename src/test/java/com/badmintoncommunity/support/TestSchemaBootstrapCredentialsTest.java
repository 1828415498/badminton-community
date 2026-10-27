package com.badmintoncommunity.support;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TestSchemaBootstrapCredentialsTest {

    @Test
    void missingTestCredentialsFailBeforeOpeningDatabaseConnection() {
        DataSource dataSource = mock(DataSource.class);

        new ApplicationContextRunner()
                .withPropertyValues("TEST_DB_USER=", "TEST_DB_PASSWORD=")
                .withBean(DataSource.class, () -> dataSource)
                .withBean(TestSchemaBootstrap.class)
                .run(context -> {
                    IllegalStateException error = assertThrows(IllegalStateException.class,
                            () -> context.getBean(TestSchemaBootstrap.class)
                                    .run(new DefaultApplicationArguments()));
                    assertTrue(error.getMessage().contains("TEST_DB_USER"), error.getMessage());
                    assertTrue(error.getMessage().contains("TEST_DB_PASSWORD"), error.getMessage());
                    verifyNoInteractions(dataSource);
                });
    }
}
