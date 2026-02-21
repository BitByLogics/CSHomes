# CSHomes

A high-performance, cross-server home management system for Paper. Great for small SMPs or large multi-server setups.

---

### Key Features

* **Cross-Server Teleportation:** Seamlessly move players between sub-servers using Redis pub/sub.
* **Flexible Storage:** Support for both SQLite (local) and MySQL (network-wide).
* **Async by Default:** Database operations and teleport requests run off the main thread to prevent TPS drops.
* **Caching:** Uses Caffeine to store home data in memory, reducing database hits while maintaining data integrity.
* **Customizable:** Full support for MiniMessage (Adventure) formatting in the config.

---

### Configuration

After the first run, edit the `plugins/CSHomes/config.yml` file.

```yaml
# Used for identifying the server for homes, must match the server name in proxy.
Server-Name: "Survival-1"

Database-Details:
  Type: "SQLite" # Options: SQLite, MySQL
  Host: "localhost"
  Port: 3306
  Database: "cshomes"
  Username: "root"
  Password: "password"

Redis-Details:
  Host: "localhost"
  Port: 6379
  Password: ""
```

---

### Commands & Permissions

| Command           | Description | Permission |
|:------------------| :--- | :--- |
| `/home <name>`    | Teleport to a saved home | `cshomes.home` |
| `/sethome <name>` | Create a home at your current spot | `cshomes.sethome` |
| `/delhome <name>` | Delete a saved home | `cshomes.delhome` |
| `/cshomes`        | Reload the plugin and database hook | `cshomes.admin` |