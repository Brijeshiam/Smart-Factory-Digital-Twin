-- ====================================================================
-- Smart Factory Digital Twin - PostgreSQL Schema
-- Database: smart_factory
-- ====================================================================

-- 1. Machines Table
CREATE TABLE IF NOT EXISTS machines (
    id BIGSERIAL PRIMARY KEY,
    machine_code VARCHAR(50) NOT NULL UNIQUE,
    machine_name VARCHAR(100) NOT NULL,
    location VARCHAR(100) DEFAULT 'Factory Floor A',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Machine Configuration Table
CREATE TABLE IF NOT EXISTS machine_configuration (
    id BIGSERIAL PRIMARY KEY,
    machine_id BIGINT NOT NULL UNIQUE REFERENCES machines(id) ON DELETE CASCADE,
    temp_warning_limit DOUBLE PRECISION NOT NULL DEFAULT 80.0,
    temp_critical_limit DOUBLE PRECISION NOT NULL DEFAULT 90.0,
    vib_warning_limit DOUBLE PRECISION NOT NULL DEFAULT 3.5,
    vib_critical_limit DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    nominal_rpm DOUBLE PRECISION NOT NULL DEFAULT 1500.0,
    simulation_interval_ms INTEGER NOT NULL DEFAULT 2000,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Sensor Readings Table
CREATE TABLE IF NOT EXISTS sensor_readings (
    id BIGSERIAL PRIMARY KEY,
    machine_id BIGINT NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    temperature_c DOUBLE PRECISION NOT NULL,
    vibration_mm_s DOUBLE PRECISION NOT NULL,
    motor_rpm DOUBLE PRECISION NOT NULL,
    production_count BIGINT NOT NULL DEFAULT 0,
    machine_status VARCHAR(30) NOT NULL,
    alert_message TEXT
);

-- Performance Indexes for Sensor Readings
CREATE INDEX IF NOT EXISTS idx_sensor_readings_machine_recorded 
    ON sensor_readings(machine_id, recorded_at DESC);

-- 4. Alerts Table
CREATE TABLE IF NOT EXISTS alerts (
    id BIGSERIAL PRIMARY KEY,
    machine_id BIGINT NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    description TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' -- ACTIVE, ACKNOWLEDGED, RESOLVED
);

-- Performance Indexes for Alerts
CREATE INDEX IF NOT EXISTS idx_alerts_machine_status 
    ON alerts(machine_id, status);
CREATE INDEX IF NOT EXISTS idx_alerts_created_at 
    ON alerts(created_at DESC);

-- 5. Production Records Table
CREATE TABLE IF NOT EXISTS production_records (
    id BIGSERIAL PRIMARY KEY,
    machine_id BIGINT NOT NULL REFERENCES machines(id) ON DELETE CASCADE,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cycle_count BIGINT NOT NULL DEFAULT 0,
    units_produced INTEGER NOT NULL DEFAULT 0,
    operating_minutes INTEGER NOT NULL DEFAULT 0,
    efficiency_percent DOUBLE PRECISION NOT NULL DEFAULT 100.0
);

CREATE INDEX IF NOT EXISTS idx_production_records_machine_date 
    ON production_records(machine_id, recorded_at DESC);
