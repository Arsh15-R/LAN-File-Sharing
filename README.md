# 🌐 LAN File Sharing System

[![Java Version](https://img.shields.io/badge/Java-8%20%7C%2011%20%7C%2017%20%7C%2021-orange.svg)](https://www.oracle.com/java/)
[![UI](https://img.shields.io/badge/GUI-Java%20Swing%20%2F%20AWT-blue.svg)]()
[![Database](https://img.shields.io/badge/Database-MySQL%20%7C%20JDBC-00758F.svg)](https://www.mysql.com/)
[![Networking](https://img.shields.io/badge/Networking-TCP%20Sockets-brightgreen.svg)]()
[![Architecture](https://img.shields.io/badge/Architecture-Layered%20MVC-purple.svg)]()

A robust, multi-threaded Desktop Client-Server **LAN File Sharing Application** built using **Java (Swing & AWT)**, **Socket Networking**, **Multithreading**, and **JDBC with MySQL**.

The application enables high-speed, secure peer-to-peer file routing over a Local Area Network (Wi-Fi or Ethernet) with live user discovery, real-time progress indicators, transfer history auditing, and non-blocking background streaming.

---

## 👥 Project Team

This project was developed by:

| Name | Role / Contribution |
| :--- | :--- |
| **Arsh Rana** | Project Lead, Network Architecture, Core Protocol & Sockets |
| **Daksh** | Database Design, DAO Layer, MySQL Schema & JDBC Integration |
| **Adhyma Singh** | GUI Development, Swing Components, AWT Event Handlers & UX |
| **Aarav Sharma** | Asynchronous Streaming (SwingWorker), Testing, Error Handling & Docs |

---

## 🚀 Key Features

- **User Authentication & Security**:
  - Account registration with email format and credential validation.
  - Secure login with SHA-256 hashed password verification.
  - Active session prevention (blocks concurrent duplicate logins).
- **Live LAN Discovery**:
  - Automatically displays online users on the LAN with live status badges.
  - Instant receiver selection via mouse clicks.
- **High-Speed Chunked File Transfers**:
  - Non-blocking streaming in 64 KB slices over TCP sockets.
  - Non-freezing UI using background `SwingWorker` threads on the Event Dispatch Thread (EDT).
  - Handles files of any size (documents, archives, videos, installers).
- **Interactive Transfer Negotiation**:
  - Recipient gets an immediate prompt showing sender name, file name, and formatted size.
  - Recipients can **Accept** or **Reject** transfers.
- **Real-Time Progress Tracking**:
  - Live `JProgressBar` with percentage and transferred/total bytes count.
  - Real-time status states: `Pending`, `In Progress`, `Completed`, `Failed`, `Cancelled`.
  - Ability to cancel in-flight transfers with safe stream cleanup on both ends.
- **Automatic Collision Avoidance**:
  - If a file with the same name already exists in `downloads/`, it automatically renames it (e.g., `document (1).pdf`) to prevent data loss.
- **Transfer History & Auditing**:
  - All past transfers are logged in MySQL.
  - Interactive table displaying UUID, sender, receiver, file size, status, and timestamp.
- **Fail-Safe & Fault Tolerance**:
  - Handles abrupt client disconnections, stream timeouts, and socket drops gracefully.
  - Built-in disk-backed fallback store when MySQL is temporarily offline.
  - Memory leak prevention via `oos.reset()` to clear Java serialization object caches during large file transfers.

---

## 🏛️ System Architecture

The application adopts a clean **Layered MVC (Model-View-Controller)** architecture:

```
+-----------------------------------------------------------------------------------+
|                            PRESENTATION LAYER (GUI)                               |
|  LoginFrame  |  RegisterFrame  |  DashboardFrame  |  ServerFrame (Control Panel)  |
|  - AWT Events: ActionEvent, MouseEvent, WindowEvent                               |
|  - Thread-Safe UI updates scheduled on the Event Dispatch Thread (EDT)            |
|  - Non-blocking asynchronous file streaming via SwingWorker                       |
+-----------------------------------------------------------------------------------+
                                        │ Invokes / Observes
+-----------------------------------------------------------------------------------+
|                        CLIENT NETWORKING CONTROLLER                               |
|  ClientSocketManager & NetworkEventListener                                       |
|  - Duplex TCP Socket communication with ObjectInputStream / ObjectOutputStream    |
|  - Background daemon thread processing asynchronous server notifications          |
+-----------------------------------------------------------------------------------+
                                        │ TCP Socket (Port 8888)
+-----------------------------------------------------------------------------------+
|                            SERVER NETWORKING LAYER                                |
|  FileServer & ClientHandler                                                       |
|  - Multi-threaded ServerSocket using cached thread pooling                        |
|  - In-flight routing of file chunks between sender and receiver                   |
|  - Dynamic client session registry and online broadcasting                       |
|  - oos.reset() invoked per write to eliminate object serialization memory leaks   |
+-----------------------------------------------------------------------------------+
                                        │ Data Access
+-----------------------------------------------------------------------------------+
|                          DATA ACCESS OBJECT (DAO) LAYER                           |
|  UserDAO  |  FileTransferDAO                                                      |
|  - PreparedStatements, parameter binding, try-with-resources                       |
|  - Safe SHA-256 password hashing (PasswordHasher)                                 |
+-----------------------------------------------------------------------------------+
                                        │ JDBC Connection
+-----------------------------------------------------------------------------------+
|                         DATABASE & PERSISTENCE LAYER                              |
|  DatabaseConnection (reads resources/db.properties or env variables) -> MySQL     |
|  - Automatic schema initialization (CREATE TABLE IF NOT EXISTS)                   |
+-----------------------------------------------------------------------------------+
                                        │ Data Objects
+-----------------------------------------------------------------------------------+
|                             JAVABEANS (MODEL LAYER)                               |
|  UserBean  |  LoginBean  |  FileTransferBean  |  TransferHistoryBean              |
|  - Encapsulated private fields, no-arg & param constructors, getters/setters,     |
|    implements Serializable                                                        |
+-----------------------------------------------------------------------------------+
```

---

## 📂 Project Structure

```
.
├── bin/                                    # Compiled Java bytecode (.class)
├── lib/
│   └── mysql-connector-j-8.3.0.jar         # MySQL Connector/J JDBC Driver
├── resources/
│   └── db.properties                       # Database credentials configuration
├── sql/
│   └── schema.sql                          # Database schema & initial seed data
├── downloads/                              # Default destination folder for received files
├── src/
│   └── com/
│       └── fileshare/
│           ├── beans/
│           │   ├── UserBean.java           # JavaBean for user accounts & profile
│           │   ├── LoginBean.java          # JavaBean for login credentials
│           │   ├── FileTransferBean.java   # JavaBean for transfer session state
│           │   └── TransferHistoryBean.java# JavaBean for transfer audit records
│           ├── client/
│           │   ├── ClientSocketManager.java # Background socket manager & listener thread
│           │   ├── NetworkEventListener.java# Callback interface for network events
│           │   ├── LoginFrame.java         # Swing login window
│           │   ├── RegisterFrame.java      # Swing user registration window
│           │   └── DashboardFrame.java     # Main transfer & history dashboard
│           ├── dao/
│           │   ├── UserDAO.java            # JDBC DAO for users table
│           │   └── FileTransferDAO.java    # JDBC DAO for file_transfers table
│           ├── database/
│           │   ├── DatabaseConnection.java # Database connection pool & auto-migrator
│           │   └── ApplySchema.java        # Utility to initialize schema from Java
│           ├── network/
│           │   ├── MessageType.java        # Protocol enumeration
│           │   ├── NetworkMessage.java     # Protocol packet envelope
│           │   └── FileChunk.java          # 64 KB binary slice container
│           ├── server/
│           │   ├── FileServer.java         # Multi-threaded ServerSocket engine
│           │   ├── ClientHandler.java      # Per-client worker thread
│           │   └── ServerFrame.java        # Swing GUI control panel for the server
│           └── utils/
│               ├── Constants.java          # App constants (ports, buffer size, paths)
│               ├── FormatUtils.java        # Human-readable byte & date formatters
│               ├── PasswordHasher.java     # SHA-256 secure password hashing
│               └── UIUtils.java            # Custom anti-aliased high-contrast UI buttons
├── allow-firewall.bat                      # Windows Defender rule for port 8888
├── apply-schema.bat                        # Script to apply SQL schema
├── compile.bat                             # Windows compilation script
├── compile.sh                              # Linux / macOS compilation script
├── run-server.bat                          # Windows server runner
├── run-server.sh                           # Linux / macOS server runner
├── run-client.bat                          # Windows client runner
├── run-client.sh                           # Linux / macOS client runner
├── .gitignore                              # Git exclusion rules
└── README.md                               # Project documentation
```

---

## 🛠️ Prerequisites

- **Java Development Kit (JDK)**: Java 8, 11, 17, or 21+ installed and added to `PATH`.
- **MySQL Database**: MySQL 8.x, 5.7, or MariaDB (optional for local testing, as the app includes an automatic fallback data store).

---

## 🗄️ Database Setup

### 1. Execute SQL Script
Open MySQL Command Line Client or MySQL Workbench:
```sql
SOURCE /path/to/project/sql/schema.sql;
```
*(Or execute [`apply-schema.bat`](apply-schema.bat) on Windows).*

### 2. Configure Credentials
Edit [`resources/db.properties`](resources/db.properties):
```properties
db.driver=com.mysql.cj.jdbc.Driver
db.url=jdbc:mysql://localhost:3306/fileshare_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&createDatabaseIfNotExist=true
db.user=root
db.password=YOUR_MYSQL_PASSWORD
```
> **Tip:** You can also pass credentials securely via environment variables: `DB_URL`, `DB_USER`, and `DB_PASSWORD`.

---

## ⚙️ Compilation & Execution

### 1. Compile the Project

**Windows:**
Double-click `compile.bat` or run:
```cmd
compile.bat
```

**Linux / macOS:**
```bash
chmod +x *.sh
./compile.sh
```

---

### 2. Start the Server

**Windows:**
Double-click `run-server.bat` or run:
```cmd
run-server.bat
```
*(Runs the GUI Server Control Panel. By default, it listens on port `8888`).*

To run headlessly in terminal/CLI mode:
```cmd
java -cp "bin;lib\mysql-connector-j-8.3.0.jar;resources" com.fileshare.server.FileServer --cli --port=8888
```

**Linux / macOS:**
```bash
./run-server.sh
```

---

### 3. Start the Clients

**Windows:**
Double-click `run-client.bat` or run:
```cmd
run-client.bat
```

**Linux / macOS:**
```bash
./run-client.sh
```

#### Pre-seeded Sample Accounts:
| Username | Password | Full Name | Role |
| :--- | :--- | :--- | :--- |
| **`Arsh`** | **`123456789`** | Arsh Rana | Sample User 1 / Team Lead |
| **`Daksh`** | **`123456789`** | Daksh | Sample User 2 / Collaborator |
| `alice` | `password123` | Alice Johnson | Legacy Test Account |
| `bob` | `password123` | Bob Smith | Legacy Test Account |

*(Or click **Create New Account** in the client window to register a new user).*

---

## 🌐 Running Across Multiple PCs on a Local Network (LAN / Wi-Fi)

1. **Find Server IP**:
   - On the machine running the server, open Command Prompt and run `ipconfig`.
   - Note the **IPv4 Address** (e.g. `192.168.1.100` or `10.70.151.244`).
2. **Firewall Access**:
   - Allow incoming connections on port `8888` by right-clicking `allow-firewall.bat` -> *Run as administrator*, or add a firewall exception manually.
3. **Connect Remote Client**:
   - On another PC on the same Wi-Fi, run `run-client.bat`.
   - Set **Server Host** to the server's IP address (e.g. `10.70.151.244`) and Port `8888`.
   - Log in and start exchanging files seamlessly!

---

## 📄 License

This project is developed for educational and academic demonstration purposes. Free to use and modify.
