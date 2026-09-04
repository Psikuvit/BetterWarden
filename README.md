# BetterWarden

Moderation and staff-ops platform for Minecraft networks: punishments, reports, tickets,
a chat filter, staff stats, and a web panel — all backed by one embedded database, whether
you're running a single server or a whole proxy network.

Runs on **Paper** (and **Folia**), plain **Spigot**, **Velocity**, and **BungeeCord/Waterfall**.
Needs **Java 21 or newer**.

---

## Contents

- [Installation](#installation)
- [First-time setup](#first-time-setup)
- [Networked setup (proxy + multiple servers)](#networked-setup-proxy--multiple-servers)
- [Configuration](#configuration)
- [Commands & permissions](#commands--permissions)
  - [Punishments](#punishments)
  - [Player info](#player-info)
  - [Reports](#reports)
  - [Tickets](#tickets)
  - [Admin](#admin)
  - [Proxy-only commands](#proxy-only-commands)
- [GUI features](#gui-features)
- [The web panel](#the-web-panel)

---

## Installation

Drop the jar that matches your server software into `plugins/` and restart:

- **Paper** or **Folia** — `BetterWarden.jar`
- **Plain Spigot** — `BetterWarden-Spigot.jar`
- **Velocity** — `BetterWarden-Velocity.jar`
- **BungeeCord / Waterfall** — `BetterWarden-Bungee.jar`

> The plain Spigot build covers the core moderation commands and punishment enforcement.
> Player lookups, reports, tickets, the admin command tree, and the in-game GUI menus are
> Paper-only for now.

That's it — no other plugins or setup steps are required to get moderation commands working.
The panel, reports, and tickets become usable once you finish the [setup wizard](#first-time-setup)
below.

## First-time setup

On first boot, the console prints a one-time setup code:

```
=================================================================
 Panel:      http://localhost:8095
 Setup code: XXXX-XXXX  (30 min, single use)
 Open the panel and enter this code to create your owner account.
=================================================================
```

Open that URL in a browser, enter the code, and create your owner account — that's your first
staff login, with full access. If the code expires or you missed it, reissue it without
restarting:

```
/warden setup
```

Once an owner account exists, the code is no longer needed — log in at the panel normally from
then on. Additional staff accounts (with ADMIN/MODERATOR/VIEWER roles) are created from inside
the panel once you're logged in.

## Networked setup (proxy + multiple servers)

Running more than one server behind a proxy? Set `warden.mode` in `config.yml`:

- **`host`** (default) — this instance embeds its own core: its own database, its own panel,
  its own setup wizard. Use this on a standalone server, or on the proxy in front of a network.
- **`client`** — this instance has no core of its own and talks to another instance's core over
  the network instead, so the whole network shares one database and one panel. Use this on
  backend servers sitting behind a proxy that's already running in `host` mode.

A backend switching to `client` mode needs the proxy to have announced itself to it at least
once first: join the proxy once while the backend is still in `host` mode (or freshly
installed) so the handshake gets cached, *then* switch that backend to `client` and restart.

---

## Configuration

Settings live in `config.yml`, generated on first boot in the plugin/proxy's own data folder
(`plugins/BetterWarden/config.yml` on Paper/Spigot/BungeeCord, `plugins/betterwarden/config.yml`
on Velocity).

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
  chat-filter:
    enabled: true
    blocked-words: []      # plain words, or "regex:<pattern>" for a real regex rule
```

Security secrets (an IP-hashing salt and a node-sync token used between a proxy and its
backends) are generated automatically on first boot and written back into `config.yml` —
nothing to set up by hand.

Change `warden.mode`, `storage`, or `panel.port` and you'll need to restart for it to take
effect — everything else can be changed and picked up with `/warden reload`.

---

## Commands & permissions

`[reason]` is optional and defaults to "No reason provided" when left off. A reason starting
with `#` looks up a punishment template instead of literal text (e.g. `/ban Steve #griefing`).
Targets don't need to be online — a player who has joined before (or is found via a username
lookup) can be punished pre-emptively.

### Punishments

Paper, Spigot (HOST mode has the full set below; CLIENT mode drops the *Silently* variants,
`#template` shorthand, and `/ipban`), and as network-wide `/gban /gmute /gkick` on the proxy
(see [Proxy-only commands](#proxy-only-commands)).

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

### Player info

Paper only.

| Command | Permission | Description |
|---|---|---|
| `/lookup <player>` | `warden.lookup` | Player profile summary (opens a menu in-game) |
| `/history <player>` | `warden.history` | Punishment history (opens a menu in-game) |
| `/note <player> <text>` | `warden.note` | Add a staff note to a player |
| `/alts <player>` | `warden.alts` | List a player's known alt accounts |

### Reports

Paper only. Any player can file a report — no permission needed for `/report` itself.

| Command | Permission | Description |
|---|---|---|
| `/report <player> [reason]` | *(none)* | Report a player to staff |
| `/reports` | `warden.reports` | Open reports (opens a menu in-game for a player; text list for console) |
| `/reports open` / `claimed` / `all` | `warden.reports` | Filter the report list |
| `/reports view <id>` | `warden.reports` | View a report's full detail |
| `/reports claim <id>` | `warden.reports` | Claim a report |
| `/reports dismiss <id>` | `warden.reports` | Dismiss a report |
| `/reports close <id>` | `warden.reports` | Close a report |

### Tickets

Paper only. Any player can open a ticket — no permission needed for `/ticket` itself.

| Command | Permission | Description |
|---|---|---|
| `/ticket <subject>` | *(none)* | Open a support ticket |
| `/tickets` | `warden.tickets` | Open tickets (opens a menu in-game for a player; text list for console) |
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
| `/warden escalation add <group> <offence#> <type> <duration\|->` | Add a rung to an escalation ladder (e.g. 1st offence = warn, 2nd = tempmute, 3rd = ban) |
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

## GUI features

Paper only — these open as normal inventory menus, no other plugin required.

- **`/lookup <player>`** — a player's profile: active punishment count, staff note count,
  known alts, and one-click actions:
  - **Warn / Mute / Ban** open a reason picker (pick one of your punishment templates, or type
    a custom reason) instead of typing the full command.
  - **Kick** asks for confirmation, then kicks immediately.
  - Temporary bans/mutes (which need a duration) stay command-only for now.
- **`/history <player>`** — a scrollable list of a player's past punishments, active and expired.
- **`/reports`** (as a player) — a grid of reports, color-coded by status (yellow = open, green
  = claimed, gray = dismissed/closed). Click one to open its detail view: **Claim**,
  **Teleport** to where it was filed, **View Chat** (the chat snapshot captured when it was
  submitted), **Dismiss**, or **Close**.
- **`/tickets`** (as a player) — a grid of tickets, same color-coding. Click one to **Assign to
  me**, **Reply**, add an **Internal Note** (staff-only, not visible to the ticket opener), or
  **Close** it.

Console always gets a plain text list/reply instead of a menu, for both `/reports` and
`/tickets`.

---

## The web panel

Reachable at `http://<host>:<panel.port>` — port `8095` by default, the same port the
plugin/proxy already listens on, no separate service to run.

Log in with the owner account created during the [setup wizard](#first-time-setup). From there,
staff accounts and their roles (OWNER/ADMIN/MODERATOR/VIEWER) are managed from inside the panel.
Failed logins are rate-limited (locked out for 15 minutes after too many attempts, per account
and per IP).

If you're exposing the panel beyond your own network, put it behind a reverse proxy or tunnel
that terminates TLS (the panel itself only serves plain HTTP) — then set
`trust-forwarded-headers: true` in `config.yml` so it reads the real client IP from that proxy
instead of the tunnel's own address.
