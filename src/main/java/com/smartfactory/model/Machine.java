package com.smartfactory.model;

import java.time.Instant;
import java.util.Objects;

public class Machine {
    private Long id;
    private String machineCode;
    private String machineName;
    private String location;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public Machine() {
        this.enabled = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Machine(String machineCode, String machineName, String location, boolean enabled) {
        this.machineCode = machineCode;
        this.machineName = machineName;
        this.location = location;
        this.enabled = enabled;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Machine(Long id, String machineCode, String machineName, String location, boolean enabled, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.machineCode = machineCode;
        this.machineName = machineName;
        this.location = location;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMachineCode() {
        return machineCode;
    }

    public void setMachineCode(String machineCode) {
        this.machineCode = machineCode;
    }

    public String getMachineName() {
        return machineName;
    }

    public void setMachineName(String machineName) {
        this.machineName = machineName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Machine machine = (Machine) o;
        return Objects.equals(id, machine.id) || Objects.equals(machineCode, machine.machineCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, machineCode);
    }

    @Override
    public String toString() {
        return machineCode + " - " + machineName;
    }
}
