package com.orders.order_management.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatically checks whether the target PostgreSQL database exists on application startup.
 * If the database does not exist, it connects to PostgreSQL's default administrative
 * database ("postgres") and executes CREATE DATABASE automatically, eliminating the
 * need to manually create the database using pgAdmin.
 */
public class DatabaseAutoCreationListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent>, EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAutoCreationListener.class);
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    // Matches jdbc:postgresql://<host>[:<port>]/<database>[?<params>]
    private static final Pattern PG_JDBC_PATTERN = Pattern.compile("jdbc:postgresql://([^:/]+)(?::(\\d+))?/([^?]+)(?:\\?(.*))?");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        checkAndCreateDatabase(environment);
    }

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        checkAndCreateDatabase(event.getEnvironment());
    }

    private static void checkAndCreateDatabase(Environment env) {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        String url = env.getProperty("spring.datasource.url");
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            return;
        }

        String username = env.getProperty("spring.datasource.username", "postgres");
        String password = env.getProperty("spring.datasource.password", "");

        Matcher matcher = PG_JDBC_PATTERN.matcher(url);
        if (!matcher.matches()) {
            log.warn("[DatabaseAutoCreator] Could not parse PostgreSQL JDBC URL: {}", url);
            return;
        }

        String host = matcher.group(1);
        String port = matcher.group(2) != null ? matcher.group(2) : "5432";
        String targetDbName = matcher.group(3);
        String params = matcher.group(4);

        if ("postgres".equalsIgnoreCase(targetDbName) || "template1".equalsIgnoreCase(targetDbName)) {
            return;
        }

        // Maintenance connection URL to the default "postgres" database
        String adminUrl = "jdbc:postgresql://" + host + ":" + port + "/postgres" + (params != null ? "?" + params : "");

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            log.warn("[DatabaseAutoCreator] PostgreSQL JDBC driver class not found on classpath: {}", e.getMessage());
            return;
        }

        log.info("[DatabaseAutoCreator] Verifying database '{}' on {}:{}...", targetDbName, host, port);

        try (Connection conn = DriverManager.getConnection(adminUrl, username, password);
             Statement stmt = conn.createStatement()) {

            boolean exists = false;
            String checkSql = "SELECT 1 FROM pg_database WHERE datname = '" + targetDbName.replace("'", "''") + "'";
            try (ResultSet rs = stmt.executeQuery(checkSql)) {
                if (rs.next()) {
                    exists = true;
                }
            }

            if (exists) {
                log.info("[DatabaseAutoCreator] ✓ Target database '{}' already exists. Skipping auto-creation.", targetDbName);
            } else {
                log.info("[DatabaseAutoCreator] Database '{}' does not exist. Creating database automatically...", targetDbName);
                conn.setAutoCommit(true);
                String safeDbName = targetDbName.replace("\"", "\"\"");
                stmt.executeUpdate("CREATE DATABASE \"" + safeDbName + "\" WITH ENCODING = 'UTF8'");
                log.info("[DatabaseAutoCreator] ✓ Database '{}' created successfully! Flyway migrations will now initialize the schema.", targetDbName);
            }

        } catch (Exception e) {
            log.warn("[DatabaseAutoCreator] Notice during automatic database verification: {}. Application startup will continue.", e.getMessage());
        }
    }
}
