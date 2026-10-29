package com.smartfactory.dao;

import com.smartfactory.config.DatabaseConfig;
import com.smartfactory.exception.DatabaseException;
import com.smartfactory.model.ProductionRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ProductionRecordDao {
    private static final Logger logger = LoggerFactory.getLogger(ProductionRecordDao.class);
    private final DatabaseConfig dbConfig;

    public ProductionRecordDao() {
        this(DatabaseConfig.getInstance());
    }

    public ProductionRecordDao(DatabaseConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public ProductionRecord save(ProductionRecord record) {
        String sql = "INSERT INTO production_records (machine_id, recorded_at, cycle_count, units_produced, operating_minutes, efficiency_percent) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            Instant ts = record.getRecordedAt() != null ? record.getRecordedAt() : Instant.now();
            record.setRecordedAt(ts);

            stmt.setLong(1, record.getMachineId());
            stmt.setTimestamp(2, Timestamp.from(ts));
            stmt.setLong(3, record.getCycleCount());
            stmt.setInt(4, record.getUnitsProduced());
            stmt.setInt(5, record.getOperatingMinutes());
            stmt.setDouble(6, record.getEfficiencyPercent());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    record.setId(rs.getLong(1));
                }
            }
            return record;
        } catch (SQLException e) {
            logger.error("Failed to insert production record: {}", e.getMessage());
            throw new DatabaseException("Failed to persist production record", e);
        }
    }

    public List<ProductionRecord> findRecentByMachine(Long machineId, int limit) {
        List<ProductionRecord> list = new ArrayList<>();
        String sql = "SELECT id, machine_id, recorded_at, cycle_count, units_produced, operating_minutes, efficiency_percent " +
                     "FROM production_records WHERE machine_id = ? ORDER BY recorded_at DESC LIMIT ?";

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
            logger.error("Failed to query production records: {}", e.getMessage());
            throw new DatabaseException("Failed to query production records", e);
        }
        return list;
    }

    public long getTotalProductionCount() {
        // Sum latest production_count across all machines from sensor_readings or production_records
        String sql = "SELECT COALESCE(SUM(latest_count), 0) FROM (" +
                     "    SELECT DISTINCT ON (machine_id) production_count AS latest_count " +
                     "    FROM sensor_readings " +
                     "    ORDER BY machine_id, recorded_at DESC" +
                     ") s";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            logger.warn("Query total production count fallback to 0: {}", e.getMessage());
            return 0;
        }
    }

    private ProductionRecord mapResultSet(ResultSet rs) throws SQLException {
        Timestamp recordedTs = rs.getTimestamp("recorded_at");
        return new ProductionRecord(
                rs.getLong("machine_id"),
                recordedTs != null ? recordedTs.toInstant() : Instant.now(),
                rs.getLong("cycle_count"),
                rs.getInt("units_produced"),
                rs.getInt("operating_minutes"),
                rs.getDouble("efficiency_percent")
        );
    }
}
