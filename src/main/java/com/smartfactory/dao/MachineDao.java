package com.smartfactory.dao;

import com.smartfactory.config.DatabaseConfig;
import com.smartfactory.exception.DatabaseException;
import com.smartfactory.model.Machine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MachineDao {
    private static final Logger logger = LoggerFactory.getLogger(MachineDao.class);
    private final DatabaseConfig dbConfig;

    public MachineDao() {
        this(DatabaseConfig.getInstance());
    }

    public MachineDao(DatabaseConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    public List<Machine> findAll() {
        List<Machine> machines = new ArrayList<>();
        String sql = "SELECT id, machine_code, machine_name, location, enabled, created_at, updated_at " +
                     "FROM machines ORDER BY id ASC";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                machines.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to query all machines: {}", e.getMessage());
            throw new DatabaseException("Failed to query machines", e);
        }
        return machines;
    }

    public Optional<Machine> findById(Long id) {
        if (id == null) return Optional.empty();
        String sql = "SELECT id, machine_code, machine_name, location, enabled, created_at, updated_at " +
                     "FROM machines WHERE id = ?";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query machine by ID {}: {}", id, e.getMessage());
            throw new DatabaseException("Failed to query machine by ID", e);
        }
        return Optional.empty();
    }

    public Optional<Machine> findByCode(String code) {
        if (code == null) return Optional.empty();
        String sql = "SELECT id, machine_code, machine_name, location, enabled, created_at, updated_at " +
                     "FROM machines WHERE machine_code = ?";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query machine by code {}: {}", code, e.getMessage());
            throw new DatabaseException("Failed to query machine by code", e);
        }
        return Optional.empty();
    }

    public Machine save(Machine machine) {
        if (machine.getId() == null) {
            return insert(machine);
        } else {
            return update(machine);
        }
    }

    private Machine insert(Machine machine) {
        String sql = "INSERT INTO machines (machine_code, machine_name, location, enabled, created_at, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?) RETURNING id";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            Instant now = Instant.now();
            machine.setCreatedAt(now);
            machine.setUpdatedAt(now);

            stmt.setString(1, machine.getMachineCode());
            stmt.setString(2, machine.getMachineName());
            stmt.setString(3, machine.getLocation());
            stmt.setBoolean(4, machine.isEnabled());
            stmt.setTimestamp(5, Timestamp.from(now));
            stmt.setTimestamp(6, Timestamp.from(now));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    machine.setId(rs.getLong(1));
                }
            }
            logger.info("Inserted new machine with ID {}", machine.getId());
            return machine;
        } catch (SQLException e) {
            logger.error("Failed to insert machine {}: {}", machine.getMachineCode(), e.getMessage());
            throw new DatabaseException("Failed to insert machine", e);
        }
    }

    public Machine update(Machine machine) {
        String sql = "UPDATE machines SET machine_code = ?, machine_name = ?, location = ?, enabled = ?, updated_at = ? " +
                     "WHERE id = ?";

        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            Instant now = Instant.now();
            machine.setUpdatedAt(now);

            stmt.setString(1, machine.getMachineCode());
            stmt.setString(2, machine.getMachineName());
            stmt.setString(3, machine.getLocation());
            stmt.setBoolean(4, machine.isEnabled());
            stmt.setTimestamp(5, Timestamp.from(now));
            stmt.setLong(6, machine.getId());

            int updated = stmt.executeUpdate();
            if (updated == 0) {
                throw new DatabaseException("Machine not found for update, id: " + machine.getId());
            }
            return machine;
        } catch (SQLException e) {
            logger.error("Failed to update machine {}: {}", machine.getId(), e.getMessage());
            throw new DatabaseException("Failed to update machine", e);
        }
    }

    public boolean delete(Long id) {
        String sql = "DELETE FROM machines WHERE id = ?";
        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            logger.error("Failed to delete machine {}: {}", id, e.getMessage());
            throw new DatabaseException("Failed to delete machine", e);
        }
    }

    public long count() {
        String sql = "SELECT COUNT(*) FROM machines";
        try (Connection conn = dbConfig.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        } catch (SQLException e) {
            logger.error("Failed to count machines: {}", e.getMessage());
            throw new DatabaseException("Failed to count machines", e);
        }
    }

    private Machine mapResultSet(ResultSet rs) throws SQLException {
        Timestamp createdTs = rs.getTimestamp("created_at");
        Timestamp updatedTs = rs.getTimestamp("updated_at");

        return new Machine(
                rs.getLong("id"),
                rs.getString("machine_code"),
                rs.getString("machine_name"),
                rs.getString("location"),
                rs.getBoolean("enabled"),
                createdTs != null ? createdTs.toInstant() : Instant.now(),
                updatedTs != null ? updatedTs.toInstant() : Instant.now()
        );
    }
}
