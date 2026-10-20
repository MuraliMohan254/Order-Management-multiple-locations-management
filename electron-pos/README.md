# WildBar POS — Desktop Terminal (Electron)

A hybrid Desktop Point-of-Sale (POS) terminal built with **Angular** and **Electron** featuring **Direct Client-Side Wi-Fi Thermal Printing (ESC/POS)**.

This application is designed to operate in two distinct modes based on client requirements:
1. **Standalone Offline POS Mode:** Runs both the Spring Boot backend JAR (on port **8089**) and PostgreSQL locally on the cashier machine (zero internet or cloud required).
2. **Cloud Multi-Outlet Mode (AWS EC2):** Connects to a central cloud backend on AWS EC2 across 25+ outlets, supporting Customer QR Mobile Ordering and Kitchen Display Screens (KDS), while dispatching thermal prints locally over the store's Wi-Fi.

---

## Architecture Overview

```
                      ┌──────────────────────────────────────┐
                      │             config.json              │
                      │                                      │
                      │   "mode": "standalone" | "cloud"     │
                      └──────────────────┬───────────────────┘
                                         │
             ┌───────────────────────────┴───────────────────────────┐
             ▼                                                       ▼
   [ MODE: STANDALONE ]                                    [ MODE: CLOUD (EC2) ]
┌───────────────────────────────┐                       ┌───────────────────────────────┐
│ Local Cashier Computer        │                       │ Local Cashier Computer        │
│                               │                       │                               │
│ ┌───────────────────────────┐ │                       │ ┌───────────────────────────┐ │
│ │ Electron POS App          │ │                       │ │ Electron POS App          │ │
│ │                           │ │                       │ │                           │ │
│ │ • Auto-spawns local JAR   │ │                       │ │ • Connects to AWS EC2     │ │
│ │   from backend-jar/       │ │                       │ │   for DB, Sales & KDS     │ │
│ │   on port 8089            │ │                       │ │                           │ │
│ │ • Direct Wi-Fi Printing   │ │                       │ │ • Direct Wi-Fi Printing   │ │
│ └─────────────┬─────────────┘ │                       │ └─────────────┬─────────────┘ │
│               │               │                       │               │               │
│               ▼               │                       │               ▼               │
│      Thermal Printer          │                       │      Thermal Printer          │
│   (192.168.31.53:9100)        │                       │    (Local Store Wi-Fi)        │
└───────────────────────────────┘                       └───────────────────────────────┘
```

---

## Configuration Examples (`config.json`)

The behavior of the application is controlled by `electron-pos/config.json`. Below are exact examples for each deployment model:

### Example 1: Standalone Offline POS Configuration
Use this when you want everything running locally on a single cashier computer without cloud dependencies:

```json
{
  "mode": "standalone",
  "localBackend": {
    "enabled": true,
    "jarDirectory": "./backend-jar",
    "port": 8089,
    "javaPath": "java",
    "jvmArgs": ["-Xmx512m"]
  },
  "printer": {
    "defaultIp": "192.168.31.53",
    "defaultPort": 9100
  }
}
```

**Key Highlights of Standalone Mode:**
- `port`: Set to **`8089`** (default).
- `jarDirectory`: Points to `./backend-jar` where your Spring Boot `.jar` is located.
- **Any JAR Name Works:** You can name your JAR `order-management.jar`, `order-management-0.0.1-SNAPSHOT.jar`, `backend.jar`, or `app.jar`. Electron will automatically find and launch it.
- **Auto-Lifecycle:** When the cashier launches the app, Java starts automatically in the background. When they close the window, Electron cleanly kills the background Java process.

---

### Example 2: Cloud Multi-Outlet Configuration (AWS EC2)
Use this when the backend is hosted on an AWS EC2 instance to manage multiple outlets, KDS screens, and QR ordering:

```json
{
  "mode": "cloud",
  "remoteApiUrl": "https://api.yourwildbar.com",
  "printer": {
    "defaultIp": "192.168.31.53",
    "defaultPort": 9100
  }
}
```
*(Alternatively, if using an EC2 IP without domain: `"remoteApiUrl": "http://13.233.xx.xx:8089"`)*

**Key Highlights of Cloud Mode:**
- `mode`: Set to `"cloud"`.
- `remoteApiUrl`: Points to your hosted backend.
- `localBackend`: Ignored; no local Java process is started.
- **Direct Wi-Fi Printing:** The app fetches orders and settles bills from the cloud, but prints tickets directly to the physical thermal printer at `192.168.31.53:9100` over the restaurant's local Wi-Fi.

---

## Configuration Reference:

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `mode` | `string` | `"standalone"` | `"standalone"` (local JAR) or `"cloud"` (remote EC2 backend). |
| `remoteApiUrl` | `string` | `""` | Public URL of your EC2 backend when `mode` is set to `"cloud"`. |
| `localBackend.enabled` | `boolean` | `true` | `true` to auto-launch the local Spring Boot JAR. |
| `localBackend.jarDirectory` | `string` | `"./backend-jar"` | Folder where your Spring Boot `.jar` file is placed. |
| `localBackend.port` | `number` | `8089` | Port for the local backend (default `8089`). |
| `localBackend.javaPath` | `string` | `"java"` | Path to Java runtime executable (`java` on system PATH or full path to `java.exe`). |
| `localBackend.jvmArgs` | `array` | `["-Xmx512m"]` | JVM memory parameters for the local backend. |
| `printer.defaultIp` | `string` | `"192.168.31.53"`| Local network IP of the 80mm thermal printer. |
| `printer.defaultPort` | `number` | `9100` | RAW ESC/POS port (standard `9100`). |

---

## Mode 1: Standalone Offline POS Setup Guide

### 1. Drop your Spring Boot JAR (Any Name Works!)
Copy your compiled backend `.jar` into the `electron-pos/backend-jar/` directory:
- **No specific naming required**: Whether it is `order-management-0.0.1-SNAPSHOT.jar`, `app.jar`, or `server.jar`, Electron will automatically find and run it.
- If multiple JARs are present, it automatically chooses the most recently modified one.

```powershell
# Example: Copying built JAR
Copy-Item "..\backend\order-management\target\*.jar" "backend-jar\order-management.jar"
```

### 2. Ensure PostgreSQL is Running
Ensure your local PostgreSQL service is running with the `order_management` database created:
- Host: `localhost:5432`
- Database: `order_management`

### 3. Launch the POS App
```powershell
cd electron-pos
npm start
```
**What happens automatically:**
1. Electron checks if port **8089** is already alive.
2. If not, it discovers the `.jar` in `backend-jar/` and spawns Java in the background (`java -jar ... --server.port=8089`).
3. Monitors startup health until the API is responding.
4. Opens the full POS terminal interface.
5. On window close, it cleanly kills the Java process.

---

## Mode 2: Cloud Multi-Outlet Setup Guide (AWS EC2)

### 1. Deploy Backend to AWS EC2
Follow the deployment script in the project root:
```bash
# On your AWS EC2 instance:
sudo bash deploy-ec2.sh
```
This launches Spring Boot, PostgreSQL, and Nginx with SSL (HTTPS) in Docker.

### 2. Configure `config.json` on Cashier PCs
In `electron-pos/config.json`:
```json
{
  "mode": "cloud",
  "remoteApiUrl": "https://api.yourwildbar.com",
  "printer": {
    "defaultIp": "192.168.31.53",
    "defaultPort": 9100
  }
}
```

### 3. Launch the POS App
```powershell
cd electron-pos
npm start
```
The desktop app connects to EC2 for all transactions while printing tickets locally to `192.168.31.53:9100` in less than 50ms over the store's Wi-Fi.

---

## Updating Frontend UI Build

You have two simple options to update the frontend:

### Option A: Manual Copy-Paste (No Scripts Needed!)
1. Build the Angular project:
   ```powershell
   cd frontend\order-management
   npm run build
   ```
2. Copy all files inside `frontend\order-management\dist\order-management\browser` and paste them directly into `electron-pos\dist\`.
   - **Auto-Patching:** Electron automatically detects your files and patches `<base href="./">` in `index.html` on startup so you never get a blank screen.
   - Even if you paste the entire `browser` folder into `electron-pos\dist\browser`, Electron will auto-detect it.

### Option B: Automatic Script Copy
```powershell
# 1. Build Angular production artifacts:
cd frontend\order-management
npm run build

# 2. Copy the built dist into Electron:
cd ..\..\electron-pos
npm run copy-dist

# 3. Test in Electron:
npm start
```

---

## Direct Wi-Fi Thermal Printing (ESC/POS)

### Why Client-Side Printing in Electron is Superior:
- Standard web browsers (Chrome/Firefox) **cannot** open raw TCP sockets to port `9100` due to browser sandbox restrictions.
- Because Electron runs on the cashier computer with Node.js, it creates direct TCP socket streams (`net.Socket`) over local Wi-Fi.
- **Zero Drivers Required:** No Windows or vendor printer drivers needed on any PC, tablet, or phone.
- **Speed:** Instant ticket print in ~50ms.
- **Hardware Compatibility:** Works with any standard 80mm ESC/POS printer (Caysn, Epson, Rongta, Xprinter, TVS, Citizen).

### Features Supported:
- **Kitchen Order Tickets (KOT):**
  - Multi-round tracking: Highlights `*** PREPARE NEW ITEMS ONLY (ROUND X) ***` for add-on orders.
  - Itemized quantities, double-height titles, and kitchen notes.
  - Automatic **2-tone buzzer alert** to notify chefs.
  - Automatic paper cut.
- **Customer Bill:**
  - Restaurant name, GSTIN, order number, table/takeaway status.
  - Itemized quantity, item name, and price list.
  - Subtotal, GST tax breakdown, discounts, and double-height Grand Total.
  - Settled By cashier name and automatic paper cut.

---

## Packaging into a Windows Desktop Application (`.exe`)

The Electron POS desktop terminal is pre-configured with **`electron-builder`** and includes a dedicated, high-resolution **Billing & POS Terminal application icon** (`assets/icon.ico`).

### 1. Dedicated Billing Application Icon (`assets/icon.ico`)
- **Design Elements**: Modern POS billing terminal featuring an ejected printed receipt bill slip, glowing digital emerald display (`₹ 1,250` / `PAID ✓`), tactile keypad with action keys, and NFC card reader chip.
- **Multi-Resolution Windows Container**: Contains embedded mipmaps at **256x256, 128x128, 64x64, 48x48, 32x32, and 16x16**, ensuring razor-sharp rendering on Windows 10/11 taskbars, Alt-Tab, desktop shortcuts, and high-DPI scaling.
- **Regenerate Icon (Optional)**:
  ```powershell
  npm run generate-icon
  ```

---

### 2. Available Packaging Commands

Navigate to `electron-pos/` and run the command matching your distribution target:

| Target | Command | Output Artifact | Best For |
| :--- | :--- | :--- | :--- |
| **Single Portable `.exe`** | `npm run dist:portable` | `release/WildBar POS 1.0.0.exe` | **Recommended:** Single executable file that runs instantly without installation (great for USB drives or quick cashier deployment). |
| **Windows NSIS Installer** | `npm run dist:installer` | `release/WildBar POS Setup 1.0.0.exe` | Standard Windows wizard installer with Desktop shortcut and Start Menu entry. |
| **Unpacked Directory** | `npm run pack` | `release/win-unpacked/WildBar POS.exe` | Fastest build (~10 seconds). Useful for testing the `.exe` without waiting for 7z compression. |
| **Full Build (Both)** | `npm run dist` | `release/` (Installer + Portable) | Complete distribution package for customer handover. |

---

### 3. Step-by-Step Production Release Workflow

#### Step 1: Compile Production Frontend Artifacts
```powershell
cd "frontend\order-management"
npm run build
```

#### Step 2: (Optional for Standalone Mode) Copy Spring Boot JAR
If distributing the Standalone Offline version with local database:
```powershell
cd ..\..\backend\order-management
.\mvnw.cmd clean package -DskipTests
Copy-Item "target\*.jar" "..\..\electron-pos\backend-jar\order-management.jar" -Force
```

#### Step 3: Package the Desktop Executable
```powershell
cd ..\..\electron-pos
npm run dist:portable
```
Note:  Do `npm i` to install node_modules.

Your ready-to-run desktop executable will be in:
📁 **`electron-pos\release\WildBar POS 1.0.0.exe`** (with custom Billing icon embedded).

---

### 4. Built-in Automated Database Backups (On-Login & On-Exit)
When operating in standalone mode, the Electron desktop application automatically guards against data loss:
- **On Cashier Login**: Automatically runs a silent background database snapshot if > 12 hours elapsed.
- **On Shift Exit**: Intercepts application window closing (`before-quit`) to snapshot the database before terminating the Java backend.
- **Credential Protection**: Uses Windows DPAPI / Electron `safeStorage` to encrypt passwords on disk (`backup-config.json`), keeping master pgAdmin passwords hidden from store staff.
- **Full Guide**: See [`scripts/backup/README.md`](../scripts/backup/README.md).
