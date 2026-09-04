# warden-panel

React + TypeScript + Vite. This is the Stage 5 staff/admin web panel - see
`docs/spec/04-PANEL.txt` for the full route/feature list and `PLAN.md`
Stage 5 for what's actually built so far (not much yet - a skeleton).

Not a Maven module - a plain npm project, built separately.

## Development

Needs a real Core running to talk to (`vite.config.ts` proxies `/api`,
`/health`, `/ws` to `http://localhost:8095`):

```bash
npm install
npm run dev
```

## Production build

Builds straight into `warden-core/src/main/resources/static/` - Spring
Boot's default static-resource handling serves it with zero extra config,
same "one embedded process" pattern as the rest of this project. Not
wired into the Maven build (yet) - run this **before** `mvn package`, or
the panel just won't be in the jar:

```bash
npm run build
```

`SpaForwardController` (in warden-core) forwards any non-API, non-static-
file path to `index.html` so React Router's client-side routes work on a
direct navigation or refresh, not just in-app link clicks.

## No authentication yet

Every route under `AppLayout` is reachable with no login check - see the
banner it renders, and PLAN.md Stage 5's auth section.
