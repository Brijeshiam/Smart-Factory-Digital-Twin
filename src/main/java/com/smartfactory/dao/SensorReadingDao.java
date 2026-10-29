package com.smartfactory.dao;

import com.smartfactory.config.DatabaseConfig;
import com.smartfactory.exception.DatabaseException;
import com.smartfactory.model.MachineState;
import com.smartfactory.model.SensorReading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SensorReadingDao {
    private static final Logger logger = LoggerFactory.getLogger(SensorReadingDao.class);
    private final DatabaseConfig dbConfig;

    public SensorReadingDao() {
        this(DatabaseConfig.getInstance());
    }

    public SensorReadingDao(DatabaseConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public SensorReading save(SensorReading reading) {
        String sql = "INSERT INTO sensor_readings " +
                     "(machine_id, recorded_at, temperature_c, vibration_mm_s, motor_rpm, " +
                     "production_count, machine_status, alert_message) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            Instant ts = reading.getRecordedAt() != null ? reading.getRecordedAt() : Instant.now();
            reading.setRecordedAt(ts);

            stmt.setLong(1, reading.getMachineId());
            stmt.setTimestamp(2, Timestamp.from(ts));
            stmt.setDouble(3, reading.getTemperatureC());
            stmt.setDouble(4, reading.getVibrationMmS());
            stmt.setDouble(5, reading.getMotorRpm());
            stmt.setLong(6, reading.getProductionCount());
            stmt.setString(7, reading.getMachineStatus().name());
            stmt.setString(8, reading.getAlertMessage());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    reading.setId(rs.getLong(1));
                }
            }
            return reading;
        } catch (SQLException e) {
            logger.error("Failed to insert sensor reading for machine {}: {}", reading.getMachineId(), e.getMessage());
            throw new DatabaseException("Failed to persist sensor reading", e);
        }
    }

    public List<SensorReading> findRecentByMachineId(Long machineId, int limit) {
        List<SensorReading> list = new ArrayList<>();
        String sql = "SELECT id, machine_id, recorded_at, temperature_c, vibration_mm_s, motor_rpm, " +
                     "production_count, machine_status, alert_message " +
                     "FROM sensor_readings WHERE machine_id = ? " +
                     "ORDER BY recorded_at DESC LIMIT ?";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, machineId);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to fetch recent readings for machine {}: {}", machineId, e.getMessage());
            throw new DatabaseException("Failed to fetch recent sensor readings", e);
        }
        return list;
    }

    public Optional<SensorReading> findLatestReadingForMachine(Long machineId) {
        List<SensorReading> list = findRecentByMachineId(machineId, 1);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
    }

    public List<SensorReading> findHistorical(Long machineId, Instant start, Instant end, MachineState status, int limit, int offset) {
        List<SensorReading> list = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT id, machine_id, recorded_at, temperature_c, vibration_mm_s, ")
                .append("motor_rpm, production_count, machine_status, alert_message FROM sensor_readings WHERE 1=1 ");

        List<Object> params = new ArrayList<>();

        if (machineId != null) {
            sql.append("AND machine_id = ? ");
            params.add(machineId);
        }
        if (start != null) {
            sql.append("AND recorded_at >= ? ");
            params.add(Timestamp.from(start));
        }
        if (end != null) {
            sql.append("AND recorded_at <= ? ");
            params.add(Timestamp.from(end));
        }
        if (status != null) {
            sql.append("AND machine_status = ? ");
            params.add(status.name());
        }

        sql.append("ORDER BY recorded_at DESC LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query historical sensor readings: {}", e.getMessage());
            throw new DatabaseException("Failed to query historical sensor readings", e);
        }
        return list;
    }

    public long countHistorical(Long machineId, Instant start, Instant end, MachineState status) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM sensor_readings WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (machineId != null) {
            sql.append("AND machine_id = ? ");
            params.add(machineId);
        }
        if (start != null) {
            sql.append("AND recorded_at >= ? ");
            params.add(Timestamp.from(start));
        }
        if (end != null) {
            sql.append("AND recorded_at <= ? ");
            params.add(Timestamp.from(end));
        }
        if (status != null) {
            sql.append("AND machine_status = ? ");
            params.add(status.name());
        }

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            logger.error("Failed to count historical sensor readings: {}", e.getMessage());
            throw new DatabaseException("Failed to count historical sensor readings", e);
        }
    }

    private SensorReading mapResultSet(ResultSet rs) throws SQLException {
        Timestamp recordedTs = rs.getTimestamp("recorded_at");
        return new SensorReading(
                rs.getLong("machine_id"),
                recordedTs != null ? recordedTs.toInstant() : Instant.now(),
                rs.getDouble("temperature_c"),
                rs.getDouble("vibration_mm_s"),
                rs.getDouble("motor_rpm"),
                rs.getLong("production_count"),
                MachineState.fromString(rs.getString("machine_status")),
                rs.getString("alert_message")
        );
    }
}
