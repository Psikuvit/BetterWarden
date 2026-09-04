# Exposing the panel port

BetterWarden's core (embedded in `warden-paper` or `warden-velocity`,
depending on which mode you're running) listens on `warden.panel.port`
(default `8095`) over plain HTTP. As of this writing the only real endpoint
on it is `/health`; Stage 5's actual panel will live on the same port, so
this setup applies unchanged once that ships.

Don't expose port 8095 straight to the internet with a bare port-forward -
put something in front of it that terminates TLS. Pick one:

## Option A: Caddy (simplest - automatic HTTPS)

```
warden.example.com {
    reverse_proxy localhost:8095
}
```

That's the whole config. Caddy gets a cert automatically and forwards
`X-Forwarded-For`/`X-Forwarded-Proto` correctly by default.

## Option B: nginx

```nginx
server {
    listen 443 ssl;
    server_name warden.example.com;

    ssl_certificate     /etc/letsencrypt/live/warden.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/warden.example.com/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8095;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Option C: Cloudflare Tunnel

No open inbound port at all - `cloudflared` makes an outbound connection
to Cloudflare, which proxies to it.

```yaml
# cloudflared config.yml
tunnel: <your-tunnel-id>
credentials-file: /path/to/<tunnel-id>.json

ingress:
  - hostname: warden.example.com
    service: http://localhost:8095
  - service: http_status:404
```

```bash
cloudflared tunnel run <tunnel-name>
```

Cloudflare sets `X-Forwarded-For`/`X-Forwarded-Proto` (and `CF-Connecting-
IP`) on every request it proxies.

## Required: tell BetterWarden to trust the forwarded headers

Whichever option you use, set this in `config.yml` **only after** one of
the above is the sole way to reach the panel port:

```yaml
warden:
  panel:
    trust-forwarded-headers: true
```

This is `false` by default on purpose. `X-Forwarded-For`/`X-Forwarded-
Proto` are just regular headers - if nothing in front of BetterWarden
strips or overwrites them, any client can set them to whatever they want,
which would let someone spoof their apparent IP to anything reading that
header. Only flip it on once you've confirmed the panel port is not
reachable from anywhere except your reverse proxy/tunnel - firewall it,
or, if the proxy runs on the same host, set `warden.panel.bind-address:
"127.0.0.1"` so the port never listens on anything but loopback.
