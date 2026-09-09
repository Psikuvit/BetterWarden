# BetterWarden Architecture

This document explains how BetterWarden is actually put together: the module
layout, how a single "Core" gets embedded into five different Minecraft
server/proxy runtimes, how a proxy hands its Core off to the backend servers
behind it, how a backend server without its own Core talks to a remote one,
and how the free/paid split, the web panel, the Discord bot and multi-node
scaling all fit into that picture.

It describes what the code does today. `PLAN.md` and `docs/spec/*.txt` (both
intentionally untracked, see `.gitignore`) are the forward-looking design docs
this was built from — this file is the as-built reference.

---

## 1. The core idea

Almost the entire plugin — punishments, reports, tickets, chat filter, alt
detection, staff notes, the web panel, the Discord bot, everything — lives in
one platform-agnostic module, **`warden-core`**, as a normal embedded **Spring
Boot 4.1** application (JPA/Hibernate, Spring MVC, Spring Security, Spring
WebSocket). It has zero compile-time dependency on Bukkit, Velocity, Bungee,
or any Minecraft API.

Each platform module (`warden-bukkit`, `warden-velocity`, `warden-bungee`,
`warden-standalone`) is a thin adapter whose only jobs are:

1. Read `config.yml` before Spring exists (`ConfigBootstrap`).
2. Decide **HOST** or **CLIENT** mode (§6).
3. In HOST mode: boot the embedded Spring context and register a
   `PlatformBridge` implementation so `warden-core` can kick/message/broadcast
   to players without knowing what a "player" even is.
4. In CLIENT mode: skip Spring entirely and talk to a remote Core over
   REST/WebSocket instead (`RemoteCoreClient`, §7).
5. Register whatever platform-native commands/listeners/GUIs that platform
   supports (Bukkit has punishment commands + GUI menus + chat listeners;
   Velocity/Bungee only have proxy-wide punishment commands; the standalone
   app has no in-game surface at all).

This is why the same `PunishmentService`, the same SQLite/MySQL schema, the
same web panel, and the same Discord bot code run unmodified whether
BetterWarden is a single Paper server, a full proxy network, or a standalone
Docker container serving a fleet of CLIENT nodes.

---

## 2. Module map

```
betterwarden-parent (root pom.xml)
├── warden-core        Spring Boot app: services, JPA repos/entities, REST API,
│                      WebSocket hub, web panel backend, Discord bot,
│                      i18n, edition gating. No Minecraft API dependency.
├── warden-bukkit      Paper AND plain Spigot in one jar (me.psikuvit.betterWarden
│                      for Paper, me.psikuvit.betterWarden.spigot for Spigot).
│                      Ships both paper-plugin.yml and plugin.yml.
├── warden-velocity    Velocity proxy plugin.
├── warden-bungee      BungeeCord/Waterfall proxy plugin.
├── warden-standalone  Headless Spring Boot app (no Minecraft API at all) —
│                      the Docker/Network-tier Core, talked to only over
│                      REST/WS by CLIENT-mode proxies and servers.
└── warden-panel       React 19 + TypeScript + Vite SPA — the web panel's
                       frontend. Built separately (`npm run build`) and its
                       output is copied into warden-core/src/main/resources/
                       static/ by hand — not yet wired into the Maven
                       reactor (see PLAN.md).
```

Dependency direction is strictly one-way: every platform module depends on
`warden-core`; `warden-core` depends on nothing platform-specific. Maven
module list: [pom.xml:14-24](pom.xml).

| Module | Depends on | Needs |
|---|---|---|
| `warden-core` | Spring Boot, JPA/Hibernate, SQLite/MySQL drivers, Flyway, Spring Security, Spring WebSocket, JDA 6 (Discord) | JDK 21 |
| `warden-bukkit` | `warden-core`, paper-api (compile), Spigot API, Adventure | JDK 25 to compile against paper-api, bytecode target 21 |
| `warden-velocity` | `warden-core`, Velocity API | JDK 21+ |
| `warden-bungee` | `warden-core`, BungeeCord API, Adventure-platform-bungeecord | JDK 21+ |
| `warden-standalone` | `warden-core` only | plain JDK 21 (`warden-standalone/Dockerfile`) |
| `warden-panel` | none of the above (separate npm project) | Node/npm |

---

## 3. Editions: Free / Standard / Network

One codebase, three sellable tiers, gated by a single build-time property —
never by anything in `config.yml`, so a free user can't unlock paid features
by editing config:

- Root [pom.xml](pom.xml)'s `warden.edition` property (default `paid`, or
  `free` under the `free` Maven profile) is filtered into
  `warden-core/target/classes/edition.properties` at build time.
- [`EditionService`](warden-core/src/main/java/me/psikuvit/betterWarden/core/config/EditionService.java)
  reads that classpath resource once at boot and exposes `isPaid()`/`isFree()`.
  If the resource is missing or unreadable it **fails toward paid**, never
  silently downgrading a real buyer's install.
- Every paid subsystem checks `EditionService` before activating: the web
  panel's setup wizard never generates a setup code in free
  ([`SetupBootstrap`](warden-core/src/main/java/me/psikuvit/betterWarden/core/panel/setup/SetupBootstrap.java)),
  MySQL storage is forced back to SQLite in free
  ([`ConfigBootstrap.applyToSystemProperties`](warden-core/src/main/java/me/psikuvit/betterWarden/core/config/ConfigBootstrap.java:62)),
  reports/tickets/Discord-link commands are only registered when
  `edition.isPaid()` ([`BetterWarden.java:129`](warden-bukkit/src/main/java/me/psikuvit/betterWarden/BetterWarden.java:129)),
  and `StaffNoteService`/chat filter/alt detection/escalation all short-circuit
  in free.

| | Free | Standard | Network |
|---|---|---|---|
| Punishments (ban/mute/kick/warn), GUI menus | ✅ | ✅ | ✅ |
| Storage | SQLite only | SQLite or MySQL | SQLite or MySQL |
| Web panel, Discord bot, reports/tickets/appeals, chat filter, alt detection, staff notes, templates/escalation | ❌ | ✅ | ✅ |
| Proxy modules (`warden-velocity`/`warden-bungee`), `warden-standalone`, cross-server sync | ❌ | ❌ | ✅ |

Packaging commands for each:

```bash
# Paid (Standard/Network) — the default profile, full reactor
mvn -o clean package -DskipTests

# Free — warden-core + warden-bukkit only
mvn -o package -DskipTests -Pfree -pl warden-core,warden-bukkit -am
```

---

## 4. Deployment topologies

BetterWarden supports four shapes, all running the exact same
`warden-core` code:

### A. Single server (Free or Standard, no proxy)

```
┌─────────────────────────┐
│  Paper/Spigot server     │
│  warden-bukkit  (HOST)   │
│  ┌─────────────────────┐ │
│  │ embedded Spring core │ │  ← owns SQLite/MySQL directly
│  │ (warden-core)         │ │
│  └─────────────────────┘ │
└─────────────────────────┘
```
`warden.mode: HOST` (the default). The plugin boots its own embedded Spring
context, JPA/SQLite, and (Standard+) web panel on `:8095` — see
[`BetterWarden.onEnable`](warden-bukkit/src/main/java/me/psikuvit/betterWarden/BetterWarden.java).

### B. Multiple independent servers

Just (A) repeated per server — each has its own database, its own panel, no
cross-server sync. This is what Standard tier looks like without a proxy.

### C. Proxy network, embedded Core (Network tier, "Stage 3")

```
┌─────────────────────────────────────────┐
│ Velocity/Bungee proxy                    │
│ warden-velocity/warden-bungee  (HOST)    │
│ ┌───────────────────────────────────┐   │
│ │ embedded Spring core (warden-core) │   │  ← MySQL (shared) or SQLite
│ └───────────────────────────────────┘   │
└───────────────┬───────────────┬─────────┘
     handshake ↓                ↓ handshake
┌───────────────────┐   ┌───────────────────┐
│ Paper backend #1    │   │ Paper backend #2    │
│ warden-bukkit (CLIENT)│   │ warden-bukkit (CLIENT)│
│ RemoteCoreClient ─────┼───┼── REST + WS ────────┤
└───────────────────┘   └───────────────────┘
```
The proxy runs `warden.mode: HOST` and embeds the Core itself. Every backend
server behind it runs `warden.mode: CLIENT` and never touches a database —
it talks to the proxy's embedded Core over REST + WebSocket instead (§7).

### D. Standalone Core, proxy + servers as CLIENT (Network tier, "Stage 4")

```
                    ┌────────────────────────────┐
                    │ warden-standalone (Docker)   │
                    │ headless Spring core          │──── MySQL
                    │ :8095 REST + WS               │──── Redis (multi-replica, §14)
                    └───────────┬────────────────┘
                       REST/WS  │  (warden.core.url in config.yml)
            ┌──────────────────┴───────────────────┐
   ┌───────────────────┐                  ┌───────────────────┐
   │ Velocity/Bungee      │  handshake →    │ Paper backend        │
   │ (CLIENT)              │───────────────►│ warden-bukkit (CLIENT)│
   └───────────────────┘                  └───────────────────┘
```
Here the Core is a standalone process (Docker, `warden-standalone`), and even
the proxy itself runs CLIENT mode — it has no upstream to get a handshake
from, so it reads `warden.core.url` directly out of its own `config.yml`
([`WardenVelocityPlugin.bootClientMode`](warden-velocity/src/main/java/me/psikuvit/betterWarden/WardenVelocityPlugin.java:145)).
It still relays a handshake onward to backend servers on join, so a Paper
server behind it never needs to know or care whether the Core is embedded or
standalone — it only ever sees "here's a Core URL and a token".

`docker-compose.yml` at the repo root wires up exactly this: `core` +
`mysql` + `redis`, with the standalone core's `Dockerfile` doing a
`-pl warden-standalone -am` Maven build (only `warden-core` gets pulled in,
never `warden-bukkit`/`paper-api`, so it's buildable with a plain JDK — see
[`warden-standalone/Dockerfile`](warden-standalone/Dockerfile)).

---

## 5. Boot sequence (every platform)

Every entry point — `BetterWarden.onEnable`, `WardenSpigotPlugin.onEnable`,
`WardenVelocityPlugin.onProxyInitialize`, `WardenBungeePlugin.onEnable`,
`WardenStandaloneApp.main` — follows the same shape, most of it shared via
[`ConfigBootstrap`](warden-core/src/main/java/me/psikuvit/betterWarden/core/config/ConfigBootstrap.java)
(platform-agnostic, runs *before* Spring exists) and, for Bukkit/Spigot,
[`PlatformBootstrap`](warden-bukkit/src/main/java/me/psikuvit/betterWarden/PlatformBootstrap.java):

1. **Force the context classloader** to the plugin's own classloader —
   Spring's autoconfiguration scanning reads `Thread.currentThread()
   .getContextClassLoader()`, and every one of these platforms' plugin
   classloaders is never the JVM's default one. Skipping this means Spring
   silently fails to find its own autoconfiguration classes.
2. **`ConfigBootstrap.ensureConfigFile`** — copies the bundled
   `default-config.yml` out to disk on first run.
3. **`ConfigBootstrap.ensureSecuritySecrets`** — generates and persists
   `warden.security.ip-salt` and `warden.security.node-token` (32 random
   bytes, hex-encoded) into `config.yml` on first run only, for whichever of
   the two isn't already set.
4. **`ConfigBootstrap.applyToSystemProperties`** — flattens the entire
   `warden.*` YAML tree into JVM system properties (dotted keys), so Spring's
   own property resolution picks them up with zero custom `@Configuration`
   wiring, and resolves `spring.datasource.*`/`spring.flyway.locations` from
   `warden.storage.type` (forced to `sqlite` in the free edition regardless
   of what's configured).
5. **`ConfigBootstrap.readMode`** — reads `warden.mode` (`HOST` default, or
   `CLIENT`).
6. **Branch**: HOST boots the embedded Spring context
   (`SpringApplicationBuilder(WardenSpringApp.class)...run()`), registering a
   platform-specific `PlatformBridge` singleton via a Spring context
   initializer; CLIENT skips Spring entirely and boots a `RemoteCoreClient`
   instead (§7).
7. Platform-specific commands/listeners are registered against whichever
   beans (HOST) or remote-cache/gateway objects (CLIENT) resulted.

[`WardenSpringApp`](warden-core/src/main/java/me/psikuvit/betterWarden/core/WardenSpringApp.java)
is the actual `@SpringBootApplication` entry class — identical for every
platform and for `warden-standalone`'s plain `SpringApplication.run(...)`.

---

## 6. HOST vs CLIENT mode

`warden.mode` (`config.yml`) is the single switch every platform reads before
Spring boots:

- **HOST** — this process embeds the full Spring context: JPA, the
  database (SQLite or MySQL), the REST API, the WebSocket hub, and (paid) the
  web panel and Discord bot. It is a Core in its own right. This is the only
  mode a standalone Paper/Spigot server or a proxy with its own database runs.
- **CLIENT** — this process never boots Spring, never touches a database.
  It only knows how to reach a *remote* Core over HTTP/WebSocket
  (`RemoteCoreClient`, §7) and runs a much smaller feature set: ban/mute
  gate listeners and punishment commands driven off a `RemotePunishmentCache`
  instead of a local `PunishmentService`.

A Paper/Spigot backend server in CLIENT mode has no `warden.core.url` of its
own to read — it learns where its Core lives from the **proxy handshake**
(§7) the first time a player joins through the proxy. A proxy in CLIENT mode
(topology D) *does* read `warden.core.url` straight out of its own
`config.yml`, because a proxy is the top of the topology — there's no
upstream node to hand it one.

---

## 7. The proxy → backend handshake (Stage 3)

When a proxy is running (HOST or CLIENT — either way it knows a Core URL and
node-token), it needs to tell each backend server behind it which Core to
use, without that backend server needing any of its own config for it. This
is done over a **Bukkit/Bungee/Velocity plugin-messaging channel**, not HTTP:

- Channel: `warden:core`
  ([`CoreHandshake.CHANNEL_ID`](warden-core/src/main/java/me/psikuvit/betterWarden/core/network/CoreHandshake.java)).
- Wire payload: a small dependency-free `DataOutputStream` encoding (no
  Jackson — the receiving backend may not have Spring booted yet when this
  arrives): a protocol-version int, then `coreUrl`, `nodeToken`, `version`,
  `tier` (`EMBEDDED` or `STANDALONE`) as UTF strings.
- **Sender**: `WardenVelocityPlugin.onServerPostConnect` /
  `WardenBungeePlugin.onServerConnected` — fired every time a player connects
  to a backend server, the proxy sends its current handshake (built fresh
  from its own embedded `CoreConfig` in HOST mode, or reused from
  `clientHandshakeToRelay` in CLIENT mode) down that backend's plugin channel.
- **Receiver**:
  [`CoreHandshakeListener`](warden-bukkit/src/main/java/me/psikuvit/betterWarden/network/CoreHandshakeListener.java)
  on the backend — decodes it and **persists it to disk**
  (`node-handshake.yml` in the plugin's data folder), then logs that
  `warden.mode: client` + a restart is needed to actually use it. It does
  **not** take effect immediately — the backend already decided HOST vs
  CLIENT for the current run before any player could possibly have triggered
  this, so it only affects the *next* boot.
- On a HOST boot, `CoreHandshakeListener.warnIfCached` checks whether a stale
  cached handshake exists and warns the operator their server is running an
  independent core while a proxy relationship is cached but unused — almost
  certainly not what was intended.
- On a CLIENT boot, `CoreHandshakeListener.readCached` reads that same file
  back to learn the Core URL/token to connect to
  (`BetterWarden.bootClientMode`, `WardenSpigotPlugin.bootClientMode`). If no
  handshake is cached yet, the plugin logs an error and refuses to start in
  CLIENT mode — join once through the proxy first.

This is why CLIENT-mode backend servers need **zero manual configuration** of
where their Core is: join the network once through the proxy, restart, done.

---

## 8. CLIENT-mode network protocol (backend/proxy ↔ Core)

Once a CLIENT node has a Core URL + node-token (from the handshake, or from
its own `config.yml` for a top-of-topology proxy), it talks to that Core via
[`RemoteCoreClient`](warden-core/src/main/java/me/psikuvit/betterWarden/core/client/RemoteCoreClient.java) —
built on plain `java.net.http` (no extra dependency; `HttpClient` +
`java.net.http.WebSocket` have shipped since JDK 11):

- **On start**: `GET /api/v1/punishments/active` — a full snapshot of every
  active punishment, loaded into an in-memory
  [`RemotePunishmentCache`](warden-core/src/main/java/me/psikuvit/betterWarden/core/client/RemotePunishmentCache.java).
  Then opens a WebSocket to `/ws/nodes`.
- **Live push**: the Core's
  [`NodeWebSocketHandler`](warden-core/src/main/java/me/psikuvit/betterWarden/core/ws/NodeWebSocketHandler.java)
  broadcasts a tiny `{event: "PUNISHMENT_CHANGED", uuid: ...}` invalidation
  notice to every connected node whenever a `PunishmentChangedEvent` fires
  locally on the Core (issue/revoke) — deliberately **not** the full payload.
  The CLIENT reacts by re-fetching just that UUID:
  `GET /api/v1/punishments/by-uuid/{uuid}`.
- **Writes** (issuing/revoking a punishment from a CLIENT-side command) POST
  straight to the Core: `POST /api/v1/punishments`,
  `POST /api/v1/punishments/{id}/revoke`.
- **Auth**: every request/handshake carries `X-Node-Token: <shared secret>`.
  REST calls are checked by
  [`NodeAuthInterceptor`](warden-core/src/main/java/me/psikuvit/betterWarden/core/network/NodeAuthInterceptor.java)
  (applied to `/api/v1/**` via `WebConfig`); the WebSocket handshake is
  checked once, up front, by
  [`NodeHandshakeInterceptor`](warden-core/src/main/java/me/psikuvit/betterWarden/core/ws/NodeHandshakeInterceptor.java).
  It's a single shared secret — no per-node identity yet (documented gap,
  see `PLAN.md`).
- **Resilience**: if the Core is unreachable, a write is appended to an
  on-disk [`WriteJournal`](warden-core/src/main/java/me/psikuvit/betterWarden/core/client/WriteJournal.java)
  instead of being dropped. `RemoteCoreClient` reconnects with capped
  exponential backoff (1s → 30s) and, once reconnected, **replays the journal
  oldest-first**, stopping (and keeping the remainder queued) the moment a
  replay fails again.
- **Server-side controller**: what a CLIENT talks to is
  [`NodeApiController`](warden-core/src/main/java/me/psikuvit/betterWarden/core/api/NodeApiController.java)
  — thin wrappers around `PunishmentService`/`PunishmentRepository`, nothing
  CLIENT-specific in the business logic itself.

Both HOST-mode gate listeners (`BanGateListener`, `MuteGateListener`,
`MuteCommandBlockListener`) and CLIENT-mode ones run **the same listener
classes** against different implementations of one interface,
[`PunishmentGateway`](warden-core/src/main/java/me/psikuvit/betterWarden/core/service/PunishmentGateway.java)
— `PunishmentService` implements it directly on a HOST core;
`RemotePunishmentCache` implements it against the WebSocket-fed cache on a
CLIENT node.

---

## 9. Security model

- **`warden.security.node-token`** — the shared secret for all node-to-node
  traffic (`/api/v1/**`, `/ws/nodes`). Auto-generated on first boot, never
  read from anywhere but `config.yml`.
- **`warden.security.ip-salt`** — salts IP hashing (`IpHashingService`/
  `IpHasher`) so raw player IPs are never stored — only a salted hash, used
  for IP-ban and alt-detection matching. Auto-generated the same way. A
  CLIENT node needs this too (for local IP-ban gate checks without a round
  trip) — it's read straight out of its own `config.yml` copy
  (`ConfigBootstrap.readIpSalt`), which means every node in a network must
  end up with the *same* salt for IP-ban matching to agree — currently
  achieved because the handshake-caching flow writes a fresh config on first
  boot, then the operator is expected to keep it in sync (also a
  documented gap area, not automated yet).
- **Panel auth is a completely separate layer** — see §12. Node-token auth
  explicitly bypasses Spring Security/CSRF (`/api/v1/**`, `/ws/nodes` are
  `permitAll` + CSRF-exempt in
  [`SecurityConfig`](warden-core/src/main/java/me/psikuvit/betterWarden/core/panel/auth/SecurityConfig.java))
  because it's machine-to-machine header-token auth, not a browser session.

---

## 10. Platform abstraction layer

`warden-core` never imports Bukkit/Velocity/Bungee. Two seams make that
possible:

- **[`PlatformBridge`](warden-core/src/main/java/me/psikuvit/betterWarden/core/platform/PlatformBridge.java)** —
  `isOnline`, `find`/`findByName`, `kick`, `message`, `broadcastPermission`,
  `runConsoleCommand`, `onlinePlayers()`. Each platform registers its own
  implementation (`PaperBridge`, `SpigotBridge`, `VelocityBridge`,
  `BungeeBridge`) as a Spring singleton bean at context-initializer time
  (`gac.getBeanFactory().registerSingleton("platformBridge", ...)`, visible
  in every `onEnable`/`onProxyInitialize` above). `warden-standalone` never
  registers one, so Spring falls back to
  [`NoOpBridge`](warden-core/src/main/java/me/psikuvit/betterWarden/core/platform/NoOpBridge.java)
  (`@ConditionalOnMissingBean`) — it logs what it *would* have done, since a
  standalone Core never has a live player connection to act on directly.
- **`PunishmentGateway`** (§8) — the HOST-vs-CLIENT seam for the gate
  listeners.

Paper vs plain Spigot inside `warden-bukkit` itself is a narrower version of
the same idea — see §13 of the module's own package layout: genuinely
identical logic (`BanGateListener`, `PlayerTrackingListener`,
`CoreHandshakeListener`, and now `PlatformBootstrap`'s config/CLIENT-boot
logic) is shared directly; anything touching a real API difference (Brigadier
vs classic commands, native Adventure vs `BukkitAudiences`, `AsyncChatEvent`
vs `AsyncPlayerChatEvent`, Folia-aware scheduling) has its own copy per
platform under `me.psikuvit.betterWarden` (Paper) vs
`me.psikuvit.betterWarden.spigot` (Spigot), documented per-file.

---

## 11. Data layer

- **JPA/Hibernate** over either:
  - **SQLite** (default; the *only* option in the free edition, forced via
    `ConfigBootstrap.applyToSystemProperties` regardless of what
    `config.yml` says) — `org.hibernate.community.dialect.SQLiteDialect`
    (Hibernate has no first-party SQLite support).
  - **MySQL** (Standard/Network only) — connection details resolved with
    environment variables winning over `config.yml` (`WARDEN_DB_HOST` etc.),
    so a container orchestrator's own secret store doesn't have to agree with
    a checked-in config value.
- **Flyway** migrations, dialect-specific paths:
  `classpath:db/migration/sqlite` vs `classpath:db/migration/mysql`
  (`warden-core/src/main/resources/db/migration/{sqlite,mysql}`).
- **Repositories** (`warden-core/.../core/repo/*`) — one Spring Data JPA
  interface per entity: `PunishmentRepository`, `ReportRepository`,
  `TicketRepository`, `PlayerRepository`, `PanelUserRepository`,
  `AuditLogRepository`, `StaffNoteRepository`, `AppealRepository`,
  `DiscordLinkRepository`, `EscalationRepository`,
  `PunishmentTemplateRepository`, `ChatFilterRepository`-equivalent
  (`FilteredMessageRepository`), `IpHistoryRepository`,
  `NameHistoryRepository`, `SettingRepository`.
- In a proxy/network topology, only the **HOST** node(s) touch this layer
  directly — CLIENT nodes only ever see it through the REST/WebSocket API
  (§8).

---

## 12. Web panel (React SPA + Spring MVC/Security backend)

The panel is **Standard/Network only** — see `SetupBootstrap` (§3), the
single choke point that keeps it permanently unreachable in the free edition
regardless of the rest of the Spring wiring being "up" (no owner account can
ever be created because no setup code is ever generated).

- **Frontend**: `warden-panel/` — React 19 + TypeScript + Vite
  ([`warden-panel/package.json`](warden-panel/package.json)). Built
  separately (`npm run build`) and its `dist/` output copied by hand into
  `warden-core/src/main/resources/static/` — **not yet wired into the Maven
  reactor** (tracked in `PLAN.md`; a `frontend-maven-plugin` binding is the
  planned fix).
- **Serving it**: [`SpaWebConfig`](warden-core/src/main/java/me/psikuvit/betterWarden/core/config/SpaWebConfig.java) —
  tries to resolve a real static file first (so `/assets/index-xyz.js` is
  served as-is, at any depth/extension), and only falls back to
  `index.html` when nothing matches, so React Router's client-side routes
  (`/dash`, `/players/123`, ...) work on a direct navigation or refresh
  without 404ing.
- **Two independent auth layers**, deliberately kept separate
  ([`SecurityConfig`](warden-core/src/main/java/me/psikuvit/betterWarden/core/panel/auth/SecurityConfig.java)):
  - `/api/v1/**` + `/ws/nodes` — node-to-node traffic, guarded by the shared
    node-token interceptors (§8/§9), `permitAll` + CSRF-exempt in Spring
    Security's eyes (a machine client has no CSRF token to send and doesn't
    need one).
  - `/api/panel/**` — human staff, session-cookie auth via Spring Security
    with CSRF protection **on**, using the cookie-token pattern
    (`CookieCsrfTokenRepository.withHttpOnlyFalse()`) so a plain
    `fetch()` from React can read the token and echo it back.
- **RBAC**: `PanelRole` is a strict ladder — `OWNER > ADMIN > MODERATOR >
  VIEWER` — enforced via a Spring Security `RoleHierarchy` bean so
  `@PreAuthorize("hasRole('MODERATOR')")` also admits ADMIN/OWNER.
- **First-run setup**: `SetupBootstrap` prints a one-time setup code + panel
  URL to the console on `ApplicationReadyEvent` when no `PanelUser` exists
  yet; `/api/panel/setup/**` and `/api/panel/auth/{login,csrf}` are the only
  authenticated-panel endpoints left `permitAll` (the app has to be able to
  load a login/setup form before anyone is authenticated).
- Controllers: `DashboardController`, `PunishmentPanelController`,
  `PlayerPanelController`, `ReportPanelController`, `TicketPanelController`,
  `AppealPanelController`, `ChatFilterQueueController`, `StatsController`,
  `BrandingController`, `SetupController`, `PanelAuthController` — all under
  `core/panel/**`, one package per feature area.

---

## 13. Discord bot integration

Optional, off by default (`warden.discord.enabled: false`), and **always the
buyer's own bot token** — never a shared bot — configured per-server in
`config.yml` (`warden.discord.token`). Built on **JDA 6.x**
([`DiscordBotService`](warden-core/src/main/java/me/psikuvit/betterWarden/core/discord/DiscordBotService.java)),
whose component package layout (`net.dv8tion.jda.api.components.*`,
`net.dv8tion.jda.api.modals.Modal`) is a real break from JDA 5.x that this
codebase was built against directly (verified via `javap` against the real
jar, not assumed).

Pieces: `ModerationSlashCommands` (issue punishments from Discord),
`PunishmentFeedListener`/`ReportFeedListener` (post to configured feed
channels — `warden.discord.feeds.*`), `ReportEmbedBuilder`/`ReportEmbeds` +
`ReportInteractionListener` (interactive report triage embeds, extracted as
a leaf component specifically to break a circular Spring dependency that
existed between the bot service and the report listeners),
`DiscordLinkService`/`DiscordTargetResolver` (link a Discord account to a
Minecraft account so `/ban @someone` resolves), `WeeklyDigestScheduler`, and
`DiscordPermissions` (maps `warden.discord.staffRoleIds` — a flat allowlist
for now, a full role-tier mapping is a `PLAN.md` item — to command access).

---

## 14. Multi-replica scale-out (`warden-standalone` behind a load balancer)

Nothing before the standalone/Docker tier needed this — a single embedded
Core per network was always enough. Running more than one `warden-standalone`
replica behind a load balancer needs one extra piece: **Redis pub/sub**
keeping every replica's in-memory `PunishmentCache`/WebSocket hub consistent
with writes that landed on a *different* replica.

[`RedisPublisher`](warden-standalone/src/main/java/me/psikuvit/betterWarden/RedisPublisher.java)
is deliberately **one-directional in each place**:
- A local write fires `PunishmentChangedEvent` on the local `EventBus` (same
  as always) → `RedisPublisher`'s subscription forwards it to Redis
  (`warden:punishment-changed` channel).
- A message **arriving** from Redis updates the local cache and pushes to
  this replica's own connected CLIENT nodes **directly** — it never re-enters
  the local `EventBus`. If it did, every replica would re-forward every other
  replica's update forever.

`RedisConfig` just wires a `RedisMessageListenerContainer` onto that channel.
This is entirely absent from `warden-bukkit`/`warden-velocity`/`warden-bungee`
— it only exists in `warden-standalone`, since that's the only module where
running multiple instances of the *same* Core behind a shared database is a
real scenario. `docker-compose.yml` provisions the `redis` service
unconditionally (harmless for a single replica — the pub/sub channel just
has no other subscriber).

---

## 15. Internationalization

[`LangService`](warden-core/src/main/java/me/psikuvit/betterWarden/core/service/LangService.java)
resolves every user-facing string through a **3-tier fallback chain**:

1. Bundled English (`lang/en.yml`) — always loaded first, the guaranteed base
   so a lookup can never come back completely empty.
2. A bundled locale file, if one ships for `warden.language` (currently
   `lang/fr.yml`, `lang/de.yml` alongside `en.yml` in
   `warden-core/src/main/resources/lang/`).
3. A **user-supplied** `lang/<code>.yml` inside the plugin's own data
   folder — can override individual keys of a shipped language, or add an
   entirely new language BetterWarden itself doesn't ship, with zero code
   changes.

`LangService` is constructed two different ways depending on mode: as a real
Spring `@Service` bean in HOST mode, and as a plain `new LangService()` (no
Spring context at all) in CLIENT mode — `ConfigBootstrap` exposes the data
folder as a system property specifically so both construction paths can find
user lang files the same way.

---

## 16. Build system

- **Reactor**: root [`pom.xml`](pom.xml) lists all five backend modules;
  `mvn clean package` (or the `free` profile) from the repo root builds all
  of them in dependency order.
- **Java targets**: `paper-api` needs JDK 25 to *compile against*, but output
  bytecode is pinned to release 21 (`maven.compiler.release=21`) since the
  shade plugin's ASM can't rewrite JDK 25 class files yet — every module
  still runs on a JDK 21+ server.
- **Shading** (`warden-bukkit`, `warden-velocity`, similar in others):
  bundles `warden-core` and its dependencies into one fat jar per platform.
  Two packages are **deliberately never relocated**, both documented inline
  with why:
  - `tools.jackson` (Jackson 3.x) — Spring Boot 4's `JacksonAutoConfiguration`
    gates itself with a **string-literal** `@ConditionalOnClass(name=...)`
    check that shade relocation doesn't rewrite (it only rewrites real
    class/bytecode references, never string constants) — relocating it
    silently breaks Spring's own Jackson autoconfiguration. This was only
    ever caught by a real HOST-mode boot test on plain Spigot; every earlier
    manual test had been CLIENT mode, which never exercises that bean.
  - `net.kyori`/`org.slf4j` — same parent-classloader reasoning as the
    server's own bundled copies.
- **`warden-bukkit` ships two descriptors** in one jar — `paper-plugin.yml`
  and `plugin.yml` — so the same jar boots correctly as a Paper plugin *or* a
  plain Spigot plugin; each server's own loader reads only the descriptor it
  understands.
- **Free vs paid**: see §3 — the `free` Maven profile only changes a resource-
  filtered property and a jar-name suffix (`warden.jarSuffix=-Free`); no
  source code is excluded or duplicated between the two builds.

---

## 17. File map (where things live)

```
warden-core/src/main/java/me/psikuvit/betterWarden/core/
├── WardenSpringApp.java        @SpringBootApplication entry point (shared)
├── config/                     CoreConfig (typed config.yml), ConfigBootstrap
│                                (pre-Spring bootstrap), EditionService
├── platform/                   PlatformBridge seam, NoOpBridge, PlayerRef
├── network/                    CoreHandshake wire format, NodeAuthInterceptor,
│                                REST DTOs
├── client/                     RemoteCoreClient, RemotePunishmentCache,
│                                WriteJournal — the CLIENT-mode side
├── ws/                         NodeWebSocketHandler + handshake interceptor —
│                                the HOST-mode push side
├── api/                        NodeApiController, HealthController,
│                                NodeStatusController — what CLIENT nodes call
├── service/                    PunishmentService, ReportService, TicketService,
│                                ChatFilterService, AltDetectionService,
│                                StaffNoteService, EscalationService, LangService, ...
├── repo/, model/                JPA repositories and entities
├── panel/                       Web panel backend: auth/, dashboard/, player/,
│                                punishment/, report/, ticket/, appeal/,
│                                chatfilter/, setup/, stats/, branding/
├── discord/                     JDA 6 bot integration
└── event/                       In-process EventBus (PunishmentChangedEvent, ...)

warden-bukkit/src/main/java/me/psikuvit/betterWarden/
├── BetterWarden.java            Paper entry point
├── PlatformBootstrap.java       Shared config/CLIENT-boot logic (Paper + Spigot)
├── spigot/                      Plain-Spigot entry point + Spigot-specific classes
├── bridge/, listener/, command/, gui/, hook/, scheduler/, network/
                                  (top-level = shared or Paper-specific;
                                   spigot/ subpackage = Spigot-specific)

warden-velocity/, warden-bungee/  Proxy plugins — command/, listener/, bridge/
warden-standalone/                 Headless app — WardenStandaloneApp,
                                    RedisConfig/RedisPublisher (multi-replica)
warden-panel/                      React SPA (built separately, see §12)
```
