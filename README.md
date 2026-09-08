# BetterWarden

Moderation and staff-ops platform for Minecraft networks: punishments, reports, tickets,
a chat filter, staff stats, and a web panel — all backed by one embedded database, whether
you're running a single server or a whole proxy network.

Runs on **Paper** (and **Folia**), plain **Spigot**, **Velocity**, and **BungeeCord/Waterfall**.
Needs **Java 21 or newer**.

---

## Editions

BetterWarden ships in three editions. **You're reading the same README either way** — sections
below are marked when they only apply to Standard/Network.

| | **Free** | **Standard** | **Network** |
|---|---|---|---|
| Price | $0 | one-time | one-time |
| Servers | 1 | 1 | proxy + unlimited backends |
| Punishment commands + GUI (`/lookup`, `/history`) | ✅ | ✅ | ✅ |
| Web panel | – | ✅ | ✅ |
| Discord bot | – | ✅ | ✅ |
| Reports, tickets, appeals | – | ✅ | ✅ |
| Chat filter | – | ✅ | ✅ |
| Alt detection, staff notes | – | ✅ | ✅ |
| Punishment templates & escalation | – | ✅ | ✅ |
| MySQL storage | – | ✅ | ✅ |
| Network-wide `/gban /gmute /gkick`, proxy support | – | – | ✅ |
| Support | community | community | priority |

Running the free edition and want the rest of this? **betterwarden.dev** has Standard ($34.99,
single server) and Network ($59.99, full proxy network) — same jar family, same config, no
migration needed: drop in the paid jar, restart, and everything in this README lights up.

---

## Contents

- [Installation](#installation)
- [Free edition — quick start](#free-edition--quick-start)
- [Standard/Network — first-time setup (web panel)](#standardnetwork--first-time-setup-web-panel)
- [Networked setup (proxy + multiple servers) — Network only](#networked-setup-proxy--multiple-servers--network-only)
- [Configuration](#configuration)
- [Commands & permissions](#commands--permissions)
  - [Punishments](#punishments)
  - [Player info](#player-info)
  - [Reports — Standard/Network](#reports--standardnetwork)
  - [Tickets — Standard/Network](#tickets--standardnetwork)
  - [Admin](#admin)
  - [Proxy-only commands — Network](#proxy-only-commands--network)
- [GUI features](#gui-features)
- [The web panel — Standard/Network](#the-web-panel--standardnetwork)

---

## Installation

Drop the jar that matches your server software into `plugins/` and restart. One jar covers
both **Paper/Folia** and **plain Spigot** — it ships both plugin descriptor formats, and each
server type only ever reads its own, so the right one boots automatically:

**Free edition:**

- **Paper, Folia, or plain Spigot** — `BetterWarden-Free.jar`

**Standard/Network:**

- **Paper, Folia, or plain Spigot** — `BetterWarden.jar`
- **Velocity** — `BetterWarden-Velocity.jar` *(Network only)*
- **BungeeCord / Waterfall** — `BetterWarden-Bungee.jar` *(Network only)*

> On plain Spigot (no Paper), only the core moderation commands and punishment enforcement are
> available. Player lookups, the admin command tree, and the in-game GUI menus need Paper.
> Reports/tickets and their menus are Paper-only *and* Standard/Network-only.

That's it — no other plugins or setup steps are required to get moderation commands working
in either edition.

## Free edition — quick start

There's no setup wizard in the free edition — no web panel, so nothing to open in a browser.
Once the jar is in `plugins/` and the server's restarted, the commands under
[Punishments](#punishments) and [Player info](#player-info) work immediately. `/warden status`
confirms it booted correctly and tells you which edition you're running.

That's the whole free edition: single server, SQLite, punishments + lookup/history. Everything
else in this README — the panel, Discord bot, reports/tickets/appeals, chat filter, alt
detection, templates, MySQL, and proxy/network support — is Standard/Network. See
[Editions](#editions) above.

## Standard/Network — first-time setup (web panel)

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

## Networked setup (proxy + multiple servers) — Network only

Running more than one server behind a proxy needs a Network license (Standard is single-server).
Set `warden.mode` in `config.yml`:

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
  mode: host              # host | client - see "Networked setup" above (Network only)
  storage:
    type: sqlite           # sqlite | mysql (mysql is Standard/Network - free is forced to sqlite)
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
    public-url: ""          # externally-reachable panel URL, e.g. for Discord report links
  login-gate:
    fail-open: true        # allow logins through if a ban check fails, rather than lock everyone out
  chat-filter:
    enabled: true           # Standard/Network - ignored in the free edition
    blocked-words: []      # plain words, or "regex:<pattern>" for a real regex rule
  discord:
    enabled: false          # Standard/Network - the bot never connects in the free edition
    token: ""
```

Security secrets (an IP-hashing salt and a node-sync token used between a proxy and its
backends) are generated automatically on first boot and written back into `config.yml` —
nothing to set up by hand.

Change `warden.mode`, `storage`, or `panel.port` and you'll need to restart for it to take
effect — everything else can be changed and picked up with `/warden reload`.

---

## Commands & permissions

`[reason]` is optional and defaults to "No reason provided" when left off. A reason starting
with `#` looks up a punishment template instead of literal text (e.g. `/ban Steve #griefing`,
Standard/Network only — templates don't exist in the free edition).
Targets don't need to be online — a player who has joined before (or is found via a username
lookup) can be punished pre-emptively.

### Punishments

**All editions.** Paper, Spigot (HOST mode has the full set below; CLIENT mode drops the
*Silently* variants, `#template` shorthand, and `/ipban`), and as network-wide
`/gban /gmute /gkick` on the proxy (Network only, see
[Proxy-only commands](#proxy-only-commands--network)).

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

**All editions**, Paper only. `/note` and `/alts` are listed here but their *data* is
Standard/Network — in the free edition they still run, `/note` tells you it's a paid feature
instead of saving anything, and `/alts` always reports none found.

| Command | Permission | Description |
|---|---|---|
| `/lookup <player>` | `warden.lookup` | Player profile summary (opens a menu in-game) |
| `/history <player>` | `warden.history` | Punishment history (opens a menu in-game) |
| `/note <player> <text>` | `warden.note` | Add a staff note to a player — Standard/Network |
| `/alts <player>` | `warden.alts` | List a player's known alt accounts — Standard/Network |

### Reports — Standard/Network

Paper only. Not registered at all in the free edition. Any player can file a report — no
permission needed for `/report` itself.

| Command | Permission | Description |
|---|---|---|
| `/report <player> [reason]` | *(none)* | Report a player to staff |
| `/reports` | `warden.reports` | Open reports (opens a menu in-game for a player; text list for console) |
| `/reports open` / `claimed` / `all` | `warden.reports` | Filter the report list |
| `/reports view <id>` | `warden.reports` | View a report's full detail |
| `/reports claim <id>` | `warden.reports` | Claim a report |
| `/reports dismiss <id>` | `warden.reports` | Dismiss a report |
| `/reports close <id>` | `warden.reports` | Close a report |

### Tickets — Standard/Network

Paper only. Not registered at all in the free edition. Any player can open a ticket — no
permission needed for `/ticket` itself.

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

Paper only, all under `warden.admin`. `status`/`reload`/`debug` work in every edition; the rest
are Standard/Network and aren't registered at all in the free edition.

| Command | Description |
|---|---|
| `/warden status` | Show version, edition, mode, storage type, login-gate setting, Discord/panel state, and a database health check |
| `/warden reload` | Re-validate `config.yml` (datasource/port changes still need a restart) |
| `/warden debug` | Write a support-ready debug report (version, edition, config hash, DB/Discord/panel status) to a file and report its path |
| `/warden setup` | Reissue the panel setup code — Standard/Network |
| `/warden template add <key> <type> <duration\|-> <group\|-> <reason>` | Create a punishment template — Standard/Network |
| `/warden template remove <key>` | Delete a punishment template — Standard/Network |
| `/warden template list` | List all punishment templates — Standard/Network |
| `/warden escalation add <group> <offence#> <type> <duration\|->` | Add a rung to an escalation ladder (e.g. 1st offence = warn, 2nd = tempmute, 3rd = ban) — Standard/Network |
| `/warden escalation list <group>` | List an escalation ladder's rungs — Standard/Network |
| `/warden unlink <player>` | Force-unlink a player's Discord account — Standard/Network |

### Proxy-only commands — Network

Velocity and BungeeCord/Waterfall — these jars only ship with a Network license. Global
punishments issue directly through the proxy, so they apply regardless of which backend server
the target is connected to.

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

- **`/lookup <player>`** — a player's profile: active punishment count, and one-click actions:
  - **Warn / Mute / Ban** open a reason picker (pick one of your punishment templates, or type
    a custom reason) instead of typing the full command. In the free edition there are no
    templates to pick from, so this is just the custom-reason prompt.
  - **Kick** asks for confirmation, then kicks immediately.
  - Temporary bans/mutes (which need a duration) stay command-only for now.
  - Staff note count and known-alt list show in Standard/Network; the free edition shows
    "Standard/Network feature" in their place instead of a possibly-misleading empty result.
- **`/history <player>`** — a scrollable list of a player's past punishments, active and expired.
- **`/reports`** (as a player) — Standard/Network. A grid of reports, color-coded by status
  (yellow = open, green = claimed, gray = dismissed/closed). Click one to open its detail view:
  **Claim**, **Teleport** to where it was filed, **View Chat** (the chat snapshot captured when
  it was submitted), **Dismiss**, or **Close**.
- **`/tickets`** (as a player) — Standard/Network. A grid of tickets, same color-coding. Click
  one to **Assign to me**, **Reply**, add an **Internal Note** (staff-only, not visible to the
  ticket opener), or **Close** it.

Console always gets a plain text list/reply instead of a menu, for both `/reports` and
`/tickets`.

---

## The web panel — Standard/Network

Not available in the free edition — see [Editions](#editions) to upgrade.

Reachable at `http://<host>:<panel.port>` — port `8095` by default, the same port the
plugin/proxy already listens on, no separate service to run.

Log in with the owner account created during the
[setup wizard](#standardnetwork--first-time-setup-web-panel). From there, staff accounts and
their roles (OWNER/ADMIN/MODERATOR/VIEWER) are managed from inside the panel. Failed logins are
rate-limited (locked out for 15 minutes after too many attempts, per account and per IP).

If you're exposing the panel beyond your own network, put it behind a reverse proxy or tunnel
that terminates TLS (the panel itself only serves plain HTTP) — then set
`trust-forwarded-headers: true` and `panel.public-url` in `config.yml` so it reads the real
client IP from that proxy instead of the tunnel's own address, and so Discord report links point
somewhere real.
