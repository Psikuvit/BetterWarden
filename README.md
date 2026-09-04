# BetterWarden

Moderation and staff-ops platform for Minecraft networks: punishments, reports, tickets,
a chat filter, staff stats, and a web panel — all backed by one embedded database, whether
you're running a single server or a whole proxy network.

Available for **Paper**, **plain Spigot**, **Velocity**, and **BungeeCord/Waterfall**, plus a
**standalone Docker** build for running the core on its own (e.g. in front of a proxy network).

---

## Contents

- [Setup](#setup)
  - [Requirements](#requirements)
  - [Building from source](#building-from-source)
  - [Installing on a server](#installing-on-a-server)
  - [Installing on a proxy](#installing-on-a-proxy)
  - [Standalone core (Docker)](#standalone-core-docker)
  - [First boot & the setup wizard](#first-boot--the-setup-wizard)
  - [Networked setup (proxy + multiple servers)](#networked-setup-proxy--multiple-servers)
- [Configuration](#configuration)
- [Commands](#commands)
  - [Punishments](#punishments)
  - [Player info](#player-info)
  - [Reports](#reports)
  - [Tickets](#tickets)
  - [Admin](#admin)
  - [Proxy-only commands](#proxy-only-commands)
- [The web panel](#the-web-panel)
- [Migrating from SQLite to MySQL](#migrating-from-sqlite-to-mysql)

---

## Setup

### Requirements

- Java 21 or newer to **run** any of the plugin/proxy jars.
- Java 25 to **build** from source with `mvn clean package` (the Paper and Spigot modules
  compile against a newer server API, and a single Maven build compiles every module together).
  Building just the non-Paper/Spigot modules (`mvn -pl warden-core,warden-velocity,warden-bungee,warden-standalone -am package`)
  only needs Java 21.
- SQLite (default, zero setup) or MySQL 8+ for storage.
- Node.js, only if you want to build the web panel yourself.

### Building from source

From the repository root:

```bash
mvn clean package
```

Each platform module produces its own jar under `<module>/target/`:

| Module             | Platform              | Output jar                              |
|---------------------|------------------------|------------------------------------------|
| `warden-paper`      | Paper (and Folia)      | `BetterWarden.jar`                       |
| `warden-spigot`     | Plain Spigot           | `BetterWarden-Spigot.jar`                |
| `warden-velocity`   | Velocity               | `BetterWarden-Velocity.jar`              |
| `warden-bungee`     | BungeeCord / Waterfall | `BetterWarden-Bungee.jar`                |
| `warden-standalone` | Standalone core        | `warden-standalone.jar`                  |

Want the web panel included in the jar? Build it **before** running `mvn package` — see
[The web panel](#the-web-panel).

### Installing on a server

Pick the jar that matches your server software and drop it in `plugins/`:

- **Paper** (also covers **Folia** — no separate build needed): `BetterWarden.jar`
- **Plain Spigot**: `BetterWarden-Spigot.jar`

Start the server once to generate `plugins/BetterWarden/config.yml`, then see
[First boot & the setup wizard](#first-boot--the-setup-wizard).

### Installing on a proxy

- **Velocity**: `BetterWarden-Velocity.jar` in `plugins/`
- **BungeeCord / Waterfall**: `BetterWarden-Bungee.jar` in `plugins/`

Same idea — start once to generate the config, then continue below.

> The plain Spigot build ships the core moderation commands and punishment enforcement.
> Player lookups, reports, tickets, the admin command tree, and in-game GUI menus are
> Paper-only for now.

### Standalone core (Docker)

For running the core by itself (e.g. one core behind a Velocity/BungeeCord network instead of
embedding it in a plugin):

```bash
cp .env.example .env
# edit .env with real database credentials
docker compose up -d
```

This starts the core, a MySQL 8.4 database, and Redis (used to sync multiple core replicas)
together. The panel is reachable at `http://localhost:8095` by default (change
`WARDEN_PANEL_PORT` in `.env` to use a different host port).

Running the standalone jar directly instead of Docker also works — point `WARDEN_HOME` at a
data directory:

```bash
WARDEN_HOME=/path/to/data java -jar warden-standalone.jar
```

### First boot & the setup wizard

On the very first boot of any HOST-mode instance (a plugin/proxy in its default mode, or the
standalone core), the console prints a one-time setup code:

```
=================================================================
 Panel:      http://localhost:8095
 Setup code: XXXX-XXXX  (30 min, single use)
 Open the panel and enter this code to create your owner account.
=================================================================
```

Open the panel URL, enter the code, and create your owner account. If you missed the code or
it expired, reissue it without restarting:

- In-game / console (Paper, Spigot): `/warden setup`
- Proxy (Velocity, BungeeCord/Waterfall): `/warden setup`

Once an owner account exists, the code is no longer needed — log in at the panel normally.

### Networked setup (proxy + multiple servers)

Every plugin/proxy runs in one of two modes, set in `config.yml` under `warden.mode`:

- **`host`** (default) — embeds its own core: its own database, its own panel, its own setup
  wizard. Use this for a single standalone server, or on the proxy in front of a network.
- **`client`** — has no core of its own and talks to another instance's core over the network
  instead. Use this on backend servers sitting behind a proxy that's already running in `host`
  mode, so the whole network shares one database and one panel.

A backend server in `client` mode needs a proxy to have announced its core to it at least once
first: join the proxy once (with the backend still in `host` mode, or freshly installed) so the
handshake gets cached, *then* switch that backend's `warden.mode` to `client` and restart.

---

## Configuration

Every instance keeps its settings in `config.yml`, in its own data folder:

| Platform                        | Config location                          |
|----------------------------------|-------------------------------------------|
| Paper / Spigot                   | `plugins/BetterWarden/config.yml`         |
| Velocity                         | `plugins/betterwarden/config.yml`         |
| BungeeCord / Waterfall           | `plugins/BetterWarden/config.yml`         |
| Standalone core                  | `$WARDEN_HOME/config.yml`                 |

Key settings (all instances):

```yaml
warden:
  mode: host              # host | client - see "Networked setup" above
  storage:
    type: sqlite           # sqlite | mysql
    mysql:
      host: localhost
      port: 3306
      database: warden
      username: warden
      password: ""
  panel:
    port: 8095
    bind-address: ""       # leave blank to bind all interfaces
    trust-forwarded-headers: false   # only set true behind a reverse proxy/tunnel
  login-gate:
    fail-open: true        # allow logins through if a ban check fails, rather than lock everyone out
```

The standalone core (Docker or bare jar) also reads database credentials from environment
variables, which take priority over `config.yml` — handy for containers:

```
WARDEN_DB_HOST
WARDEN_DB_PORT
WARDEN_DB_NAME
WARDEN_DB_USER
WARDEN_DB_PASSWORD
```

Security secrets (an IP-hashing salt and a node-sync token) are generated automatically on
first boot and written back into `config.yml` — nothing to set up manually.

---

## Commands

Permission nodes are shown in parentheses. `[reason]` is optional and defaults to "No reason
provided" when left off. A reason starting with `#` looks up a punishment template instead of
using literal text (e.g. `/ban Steve #griefing`).

### Punishments

Available on Paper, Spigot (HOST mode has the full set below; CLIENT mode drops the *Silently*
variants, `#template` shorthand, and `/ipban`), Velocity, and BungeeCord/Waterfall (as
`/gban`, `/gmute`, `/gkick` — see [Proxy-only commands](#proxy-only-commands)).

| Command | Permission | Description |
|---|---|---|
| `/ban <player> [reason]` | `warden.ban` | Ban a player |
| `/sban <player> [reason]` | `warden.ban` | Silently ban a player (no broadcast) |
| `/tempban <player> <duration> [reason]` | `warden.ban` | Temporarily ban a player (e.g. `1d`, `12h`, `30m`) |
| `/ipban <player> [reason]` | `warden.ban` | Ban a player's IP address |
| `/kick <player> [reason]` | `warden.kick` | Kick a player |
| `/skick <player> [reason]` | `warden.kick` | Silently kick a player |
| `/mute <player> [reason]` | `warden.mute` | Mute a player |
| `/smute <player> [reason]` | `warden.mute` | Silently mute a player |
| `/tempmute <player> <duration> [reason]` | `warden.mute` | Temporarily mute a player |
| `/warn <player> [reason]` | `warden.warn` | Warn a player |
| `/unban <player> [reason]` | `warden.ban` | Remove an active ban |
| `/unmute <player> [reason]` | `warden.mute` | Remove an active mute |

Targets don't need to be online — a player who has joined before (or is found via a
username lookup) can be punished pre-emptively.

### Player info

Paper only.

| Command | Permission | Description |
|---|---|---|
| `/lookup <player>` | `warden.lookup` | Player profile summary (opens a menu in-game) |
| `/history <player>` | `warden.history` | Punishment history (opens a menu in-game) |
| `/note <player> <text>` | `warden.note` | Add a staff note to a player |
| `/alts <player>` | `warden.alts` | List a player's known alt accounts |

### Reports

Paper only. Any player can file a report; no permission needed for `/report` itself.

| Command | Permission | Description |
|---|---|---|
| `/report <player> [reason]` | *(none)* | Report a player to staff |
| `/reports` | `warden.reports` | List open reports |
| `/reports open` / `claimed` / `all` | `warden.reports` | Filter the report list |
| `/reports view <id>` | `warden.reports` | View a report's full detail |
| `/reports claim <id>` | `warden.reports` | Claim a report |
| `/reports dismiss <id>` | `warden.reports` | Dismiss a report |
| `/reports close <id>` | `warden.reports` | Close a report |

### Tickets

Paper only. Any player can open a ticket; no permission needed for `/ticket` itself.

| Command | Permission | Description |
|---|---|---|
| `/ticket <subject>` | *(none)* | Open a support ticket |
| `/tickets` | `warden.tickets` | List open tickets |
| `/tickets open` / `all` / `mine` | `warden.tickets` | Filter the ticket list |
| `/tickets view <id>` | `warden.tickets` | View a ticket's messages |
| `/tickets reply <id> <text>` | `warden.tickets` | Reply to a ticket |
| `/tickets note <id> <text>` | `warden.tickets` | Add an internal (staff-only) note |
| `/tickets assign <id>` | `warden.tickets` | Assign a ticket to yourself |
| `/tickets close <id>` | `warden.tickets` | Close a ticket |

### Admin

Paper only, all under `warden.admin`.

| Command | Description |
|---|---|
| `/warden status` | Show mode, storage type, login-gate setting, and a database health check |
| `/warden reload` | Re-validate `config.yml` (datasource/port changes still need a restart) |
| `/warden debug` | Write a debug report (versions, config hash, DB status) to a file and report its path |
| `/warden setup` | Reissue the panel setup code (no-ops once an owner account exists) |
| `/warden template add <key> <type> <duration\|-> <group\|-> <reason>` | Create a punishment template |
| `/warden template remove <key>` | Delete a punishment template |
| `/warden template list` | List all punishment templates |
| `/warden escalation add <group> <offence#> <type> <duration\|->` | Add a rung to an escalation ladder |
| `/warden escalation list <group>` | List an escalation ladder's rungs |

### Proxy-only commands

Velocity and BungeeCord/Waterfall. Global punishments issue directly through the proxy, so
they apply regardless of which backend server the target is connected to.

| Command | Permission | Description |
|---|---|---|
| `/gban <player> [reason]` | `warden.ban` | Network-wide ban |
| `/gmute <player> [reason]` | `warden.mute` | Network-wide mute |
| `/gkick <player> [reason]` | `warden.kick` | Network-wide kick |
| `/warden panel` | `warden.admin` | Show the panel URL |
| `/warden nodes` | `warden.admin` | Show how many backend servers are currently connected |
| `/warden setup` | `warden.admin` | Reissue the panel setup code |

---

## The web panel

The panel is reachable at `http://<host>:<panel.port>` (default port `8095`) — the same port
the plugin/proxy/standalone core already listens on, no separate service to run.

If you built from source, the panel needs to be built once, **before** packaging:

```bash
cd warden-panel
npm install
npm run build
```

This builds the panel straight into the core's own resources, so it's included in the jar
automatically the next time you run `mvn package`. Skip this step if you're not touching the
panel's source — it's rebuilt only when its own files change.

Log in with the owner account created during the [setup wizard](#first-boot--the-setup-wizard).
Additional staff accounts (with ADMIN/MODERATOR/VIEWER roles) are managed from the panel itself
once you're logged in.

---

## Migrating from SQLite to MySQL

`migrate.sh` copies an existing SQLite database into a MySQL one:

```bash
# Preview row counts first - writes nothing
./migrate.sh --sqlite-file /path/to/warden.db --mysql-database warden --dry-run

# Then actually migrate
./migrate.sh --sqlite-file /path/to/warden.db \
  --mysql-host localhost --mysql-port 3306 \
  --mysql-database warden --mysql-user warden --mysql-password yourpassword
```

Run this against a stopped instance (or a copy of its database file) to avoid migrating
mid-write data. After migrating, update `storage.type` to `mysql` in `config.yml` and restart.
