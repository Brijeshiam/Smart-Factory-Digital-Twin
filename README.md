# Smart Factory Digital Twin — Real-Time Monitoring & Anomaly Detection

Industrial IoT (IIoT) Desktop Digital Twin application developed in **Java 21** and **JavaFX 21** with **PostgreSQL**, **HikariCP**, and real-time sensor simulation, automated threshold anomaly detection, and predictive maintenance condition analysis.

---

## 1. Overview & Architecture

The **Smart Factory Digital Twin** models physical factory floor assets as synchronized virtual digital twins. The system continuously receives simulated sensor streams, maintains physical asset states, evaluates multi-parameter operating thresholds, alerts operators, prevents alert flooding, and projects production capacity via what-if downtime analysis.

### System Architecture
```
                         +-----------------------------+
                         |     JavaFX 21 Desktop UI    |
                         |  (Dashboard, Floor, Trends) |
                         +--------------+--------------+
                                        ^
                                        | (Platform.runLater / Observable)
                         +--------------+--------------+
                         |      Digital Twin Engine    |
                         |    (Asset Virtual State)    |
                         +-------+--------------+------+
                                 |              |
          +----------------------+              +----------------------+
          |                                                            |
+---------v----------+                                       +---------v----------+
| Sensor Data Source |                                       | Threshold Evaluator|
| (Simulated Physics |                                       | & Health Scoring   |
| & Fault Injection) |                                       +---------+----------+
+--------------------+                                                 |
                                                             +---------v----------+
                                                             |  Alert Lifecycle   |
                                                             |   (Deduplication   |
                                                             |   & Auto-Resolve)  |
                                                             +---------+----------+
                                                                       |
+--------------------+       Async Non-Blocking DB Worker              |
| PostgreSQL 16/17   |<================================================+
| (schema.sql/seeds) |
+--------------------+
```

### Key Technology Stack
* **Language:** Java 21 LTS (or 23 JDK)
* **Desktop UI:** JavaFX 21 (`javafx-controls`, `javafx-fxml`, `javafx-graphics`, JavaFX Charts)
* **Styling:** Custom CSS Dark Industrial Theme (`src/main/resources/styles/theme.css`)
* **Persistence:** PostgreSQL JDBC Driver (`org.postgresql:postgresql:42.7.3`)
* **Connection Pooling:** HikariCP (`com.zaxxer:HikariCP:5.1.0`)
* **Logging:** SLF4J 2.0 with Logback Classic
* **Unit Testing:** JUnit 5 (18 automated tests passing)
* **Build System:** Apache Maven 3.9+ with `javafx-maven-plugin`

---

## 2. Factory Assets & Physics Simulation

The application seeds and models three initial industrial assets:
1. **CNC-01 — CNC Milling Machine** (Precision Bay 1, Nominal 3200 RPM)
2. **MOT-02 — Industrial Motor** (Pump Station 3, Nominal 1800 RPM)
3. **PKG-03 — Packaging Machine** (Assembly Line 2, Nominal 850 RPM)

### Telemetry Parameters Monitored
* **Temperature (°C):** Normal `< 80.0°C`, Warning `80.0°C – 89.9°C`, Critical `≥ 90.0°C`
* **Vibration (mm/s):** Normal `< 3.5 mm/s`, Warning `3.5 – 4.9 mm/s`, Critical `≥ 5.0 mm/s`
* **Motor Speed (RPM):** Monitored against equipment nominal baseline
* **Cumulative Production Count:** Real-time part increment during operational status
* **Illustrative Health Score (0–100):** Proximity penalty score based on threshold bounds

---

## 3. PostgreSQL Database Setup (Docker Recommended)

A production-ready [docker-compose.yml](file:///c:/Users/brije/OneDrive/Desktop/java%20package/docker-compose.yml) is provided in the project root.

### Method A: Docker Compose (Quickest & Recommended)

Start the PostgreSQL 16 Alpine container with a single command:
```bash
docker compose up -d
```

To stop the container:
```bash
docker compose down
```

To view logs or check container health:
```bash
docker compose ps
docker exec -it smart-factory-postgres pg_isready -U postgres -d smart_factory
```

### Method B: Standalone Docker Run
If you prefer running without compose:
```bash
docker run --name smart-factory-postgres -e POSTGRES_DB=smart_factory -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 -d postgres:16-alpine
```

### Method C: Local PostgreSQL (psql / pgAdmin)
```sql
CREATE DATABASE smart_factory;
```
Execute initialization scripts:
```bash
psql -U postgres -d smart_factory -f database/schema.sql
psql -U postgres -d smart_factory -f database/seed.sql
```

### Database Environment Variables
Defaults in [DatabaseConfig.java](file:///c:/Users/brije/OneDrive/Desktop/java%20package/src/main/java/com/smartfactory/config/DatabaseConfig.java) are preconfigured to match the Docker container out-of-the-box:
* `DB_URL`: `jdbc:postgresql://localhost:5432/smart_factory`
* `DB_USER`: `postgres`
* `DB_PASSWORD`: `postgres`

---

## 4. How to Run the Application

### Option A: Standard Maven JavaFX Run
```bash
mvn clean javafx:run
```
*(Or on Windows using the included wrapper)*:
```powershell
.\mvn.cmd javafx:run
```

### Option B: Run Unit & Integration Tests
```bash
.\mvn.cmd test
```

---

## 5. Feature Walkthrough & Verification Guide

### 1. Main Dashboard
* View high-level summary cards (Total machines, normal, warning, critical, factory output, health average).
* Real-time streaming JavaFX line charts for Temperature, Vibration, and Motor Speed.
* Sliding window of recent points keeps the UI responsive.
* In-app alert banner appears upon any critical incident.

### 2. 2D Factory Floor
* Spatial representation of factory floor assets with glowing status borders:
  * **Green:** Normal operating condition
  * **Amber:** Warning condition (approaching threshold)
  * **Red:** Critical condition (action required)
  * **Gray:** Machine offline / maintenance
* Click **"Inspect Details"** on any card to jump directly to machine drill-down.

### 3. Fault Injection Demo
* Select any machine in the header or on its card and choose a fault scenario:
  * `Inject High Temperature (>90°C Critical)`
  * `Inject Warning Temperature (82°C-88°C)`
  * `Inject Excessive Vibration (>5.2 mm/s Critical)`
  * `Inject Motor Stall / Rapid RPM Drop`
* Watch the temperature/vibration ramp realistically.
* Notice how the machine status transitions and an alert is recorded.
* Select `Clear All` or `Normal Operation` — the sensor values smoothly settle back to nominal, and the active alert **auto-resolves**!

### 4. Alert Deduplication & Operator Lifecycle
* Repeated abnormal readings **do not spawn duplicate active alerts**.
* Operators can click **"Acknowledge"** to mark awareness (status becomes `ACKNOWLEDGED`).
* Acknowledging does not falsely clear the machine condition until readings return to safe levels.

### 5. What-If Capacity Simulator
* Navigate to **"What-If Capacity"**.
* Select an asset, toggle **"Simulate Machine Offline"** or adjust production speed (-50% to +50%).
* Choose an 8-hour shift, 24-hour day, or 7-day projection.
* Observe baseline vs. scenario output comparisons and net impact percentages without affecting live simulation data.

### 6. Historical Telemetry & CSV Export
* Navigate to **"Historical Telemetry"**.
* Filter by machine, condition, or timeframe.
* Click **"Export to CSV"** to generate an audit-ready CSV file of PostgreSQL records.

---

## 6. Troubleshooting

| Issue | Cause | Resolution |
|---|---|---|
| **DB: OFFLINE badge** | PostgreSQL service not running or wrong credentials | Verify port 5432 is running. Set `DB_PASSWORD` in environment. The app gracefully falls back to memory mode if DB is disconnected. |
| **No plugin found for prefix 'javafx'** | Maven plugin group mapping | Run with the included `.\mvn.cmd javafx:run` wrapper or specify `org.openjfx:javafx-maven-plugin:0.0.8:run`. |
| **Port 5432 already in use** | Multiple PG instances | Ensure your target instance matches `DB_URL`. |
