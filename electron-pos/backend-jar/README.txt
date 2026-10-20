# Backend JAR Directory
Drop your Spring Boot JAR file (e.g., `order-management.jar` or `order-management-0.0.1-SNAPSHOT.jar`) into this folder.

When "mode" is set to "standalone" in config.json, the Electron app will automatically:
1. Detect this JAR file.
2. Launch Java in the background on startup.
3. Wait until the local database/API is ready.
4. Open the POS screen.
5. Automatically terminate the Java background process when you close the POS app.
