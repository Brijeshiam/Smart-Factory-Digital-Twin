package com.smartfactory.dao;

import com.smartfactory.config.DatabaseConfig;
import com.smartfactory.exception.DatabaseException;
import com.smartfactory.model.MachineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

public class ConfigurationDao {
    private static final Logger logger = LoggerFactory.getLogger(ConfigurationDao.class);
    private final DatabaseConfig dbConfig;

    public ConfigurationDao() {
        this(DatabaseConfig.getInstance());
    }

    public ConfigurationDao(DatabaseConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public Optional<MachineConfiguration> findByMachineId(Long machineId) {
        if (machineId == null) return Optional.empty();
        String sql = "SELECT id, machine_id, temp_warning_limit, temp_critical_limit, " +
                     "vib_warning_limit, vib_critical_limit, nominal_rpm, simulation_interval_ms, updated_at " +
                     "FROM machine_configuration WHERE machine_id = ?";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, machineId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find configuration for machine {}: {}", machineId, e.getMessage());
            throw new DatabaseException("Failed to find machine configuration", e);
        }
        return Optional.empty();
    }

    public MachineConfiguration saveOrUpdate(MachineConfiguration config) {
        String sql = "INSERT INTO machine_configuration " +
                     "(machine_id, temp_warning_limit, temp_critical_limit, vib_warning_limit, " +
                     "vib_critical_limit, nominal_rpm, simulation_interval_ms, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT (machine_id) DO UPDATE SET " +
                     "temp_warning_limit = EXCLUDED.temp_warning_limit, " +
                     "temp_critical_limit = EXCLUDED.temp_critical_limit, " +
                     "vib_warning_limit = EXCLUDED.vib_warning_limit, " +
                     "vib_critical_limit = EXCLUDED.vib_critical_limit, " +
                     "nominal_rpm = EXCLUDED.nominal_rpm, " +
                     "simulation_interval_ms = EXCLUDED.simulation_interval_ms, " +
                     "updated_at = EXCLUDED.updated_at " +
                     "RETURNING id";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            Instant now = Instant.now();
            config.setUpdatedAt(now);

            stmt.setLong(1, config.getMachineId());
            stmt.setDouble(2, config.getTempWarningLimit());
            stmt.setDouble(3, config.getTempCriticalLimit());
            stmt.setDouble(4, config.getVibWarningLimit());
            stmt.setDouble(5, config.getVibCriticalLimit());
            stmt.setDouble(6, config.getNominalRpm());
            stmt.setInt(7, config.getSimulationIntervalMs());
            stmt.setTimestamp(8, Timestamp.from(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    config.setId(rs.getLong(1));
                }
            }
            return config;
        } catch (SQLException e) {
            logger.error("Failed to save machine configuration: {}", e.getMessage());
            throw new DatabaseException("Failed to save machine configuration", e);
        }
    }

    private MachineConfiguration mapResultSet(ResultSet rs) throws SQLException {
        MachineConfiguration cfg = new MachineConfiguration();
        cfg.setId(rs.getLong("id"));
        cfg.setMachineId(rs.getLong("machine_id"));
        cfg.setTempWarningLimit(rs.getDouble("temp_warning_limit"));
        cfg.setTempCriticalLimit(rs.getDouble("temp_critical_limit"));
        cfg.setVibWarningLimit(rs.getDouble("vib_warning_limit"));
        cfg.setVibCriticalLimit(rs.getDouble("vib_critical_limit"));
        cfg.setNominalRpm(rs.getDouble("nominal_rpm"));
        cfg.setSimulationIntervalMs(rs.getInt("simulation_interval_ms"));
        Timestamp ts = rs.getTimestamp("updated_at");
        cfg.setUpdatedAt(ts != null ? ts.toInstant() : Instant.now());
        return cfg;
    }
}
