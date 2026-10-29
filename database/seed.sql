-- ====================================================================
-- Smart Factory Digital Twin - Idempotent Seed Data
-- ====================================================================

-- 1. Insert Initial Machines (idempotent ON CONFLICT)
INSERT INTO machines (machine_code, machine_name, location, enabled)
VALUES 
    ('CNC-01', 'CNC Milling Machine', 'Precision Bay 1', TRUE),
    ('MOT-02', 'Industrial Motor', 'Pump Station 3', TRUE),
    ('PKG-03', 'Packaging Machine', 'Assembly Line 2', TRUE)
ON CONFLICT (machine_code) DO UPDATE 
SET machine_name = EXCLUDED.machine_name,
    location = EXCLUDED.location,
    enabled = EXCLUDED.enabled,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Insert Default Configurations for Initial Machines
INSERT INTO machine_configuration (machine_id, temp_warning_limit, temp_critical_limit, vib_warning_limit, vib_critical_limit, nominal_rpm, simulation_interval_ms)
SELECT m.id, 80.0, 90.0, 3.5, 5.0, 
       CASE 
           WHEN m.machine_code = 'CNC-01' THEN 3200.0
           WHEN m.machine_code = 'MOT-02' THEN 1800.0
           ELSE 850.0 
       END,
       2000
FROM machines m
WHERE m.machine_code IN ('CNC-01', 'MOT-02', 'PKG-03')
ON CONFLICT (machine_id) DO UPDATE
SET temp_warning_limit = EXCLUDED.temp_warning_limit,
    temp_critical_limit = EXCLUDED.temp_critical_limit,
    vib_warning_limit = EXCLUDED.vib_warning_limit,
    vib_critical_limit = EXCLUDED.vib_critical_limit,
    nominal_rpm = EXCLUDED.nominal_rpm,
    simulation_interval_ms = EXCLUDED.simulation_interval_ms,
    updated_at = CURRENT_TIMESTAMP;
