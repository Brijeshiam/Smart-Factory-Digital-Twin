package com.smartfactory.config;

import com.smartfactory.exception.DatabaseException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

public class DatabaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);

    private static volatile DatabaseConfig instance;
    private HikariDataSource dataSource;
    private final String dbUrl;
    private final String dbUser;

    private DatabaseConfig() {
        this.dbUrl = resolveConfig("DB_URL", "jdbc:postgresql://localhost:5432/smart_factory");
        this.dbUser = resolveConfig("DB_USER", "postgres");
        String dbPassword = resolveConfig("DB_PASSWORD", "postgres");

        logger.info("Configuring database connection: URL={}, User={}", dbUrl, dbUser);

        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setUsername(dbUser);
            config.setPassword(dbPassword);

            // Pool settings
            config.setMaximumPoolSize(Integer.parseInt(resolveConfig("DB_MAX_POOL_SIZE", "10")));
            config.setMinimumIdle(Integer.parseInt(resolveConfig("DB_MIN_IDLE", "2")));
            config.setConnectionTimeout(Long.parseLong(resolveConfig("DB_CONNECTION_TIMEOUT_MS", "5000")));
            config.setIdleTimeout(60000);
            config.setMaxLifetime(300000);
            config.setPoolName("SmartFactoryHikariPool");

            // Driver class if postgresql
            if (dbUrl.startsWith("jdbc:postgresql:")) {
                config.setDriverClassName("org.postgresql.Driver");
            }

            this.dataSource = new HikariDataSource(config);
            logger.info("HikariCP connection pool initialized successfully.");
        } catch (Exception e) {
            logger.error("Failed to initialize HikariCP connection pool: {}", e.getMessage());
            // We do not throw fatal exception here so UI can show connection dialog with retry
        }
    }

    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }

    public static synchronized void setCustomDataSource(DataSource customDataSource) {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        if (instance.dataSource != null && !instance.dataSource.isClosed()) {
            instance.dataSource.close();
        }
        if (customDataSource instanceof HikariDataSource hds) {
            instance.dataSource = hds;
        } else {
            // Wrapped or direct
            HikariConfig cfg = new HikariConfig();
            instance.dataSource = null; // Reset
        }
    }

    /**
     * Reinitialize connection pool with updated settings or retry.
     */
    public synchronized boolean reconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
            } catch (Exception ignored) {}
        }
        try {
            String url = resolveConfig("DB_URL", "jdbc:postgresql://localhost:5432/smart_factory");
            String user = resolveConfig("DB_USER", "postgres");
            String pass = resolveConfig("DB_PASSWORD", "postgres");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(pass);
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(5000);
            config.setPoolName("SmartFactoryHikariPool");

            if (url.startsWith("jdbc:postgresql:")) {
                config.setDriverClassName("org.postgresql.Driver");
            }

            this.dataSource = new HikariDataSource(config);
            return testConnection();
        } catch (Exception e) {
            logger.error("Reconnect attempt failed: {}", e.getMessage());
            return false;
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not available or closed.");
        }
        return dataSource.getConnection();
    }

    public boolean testConnection() {
        if (dataSource == null || dataSource.isClosed()) {
            return false;
        }
        try (Connection conn = dataSource.getConnection()) {
            return conn.isValid(2);
        } catch (Exception e) {
            logger.warn("Database connection test failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Executes SQL script from resource or file to initialize schema or seed.
     */
    public void executeSqlScript(String scriptContent) throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            // Split by semicolon, ignoring empty commands
            String[] commands = scriptContent.split(";");
            for (String cmd : commands) {
                String trimmed = cmd.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing HikariCP database pool...");
            dataSource.close();
        }
    }

    private static String resolveConfig(String key, String defaultValue) {
        String envVal = System.getenv(key);
        if (envVal != null && !envVal.trim().isEmpty()) {
            return envVal.trim();
        }
        String propVal = System.getProperty(key);
        if (propVal != null && !propVal.trim().isEmpty()) {
            return propVal.trim();
        }
        return defaultValue;
    }

    public String getDbUrl() {
        return dbUrl;
    }

    public String getDbUser() {
        return dbUser;
    }
}
