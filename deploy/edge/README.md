# Edge vhost for the access host

`nginx/ctt-server.conf` is the TLS terminator in front of the tunnel. It is the
only hop that learns two facts the application needs: who the caller is, and
that the caller used HTTPS.

The file lives in the repository before it serves traffic: installing it is part
of bringing the public hostname up, which also needs a DNS record and a
certificate, and the checks that prove it are run against that hostname from
outside.

## What the host must already have

| Requirement | Why | How to check |
| --- | --- | --- |
| nginx ≥ 1.18 | the file uses `listen … ssl http2`, which every 1.18+ release accepts; 1.25+ prints a deprecation warning and can switch to a bare `http2 on;` | `nginx -v` |
| a certificate for the public hostname | TLS terminates here | `ls /etc/letsencrypt/live/<host>/` |
| frps listening on `127.0.0.1:18080` | the `upstream` block dials the tunnel's local end | `ss -lntp \| grep 18080` |
| nothing else listening on 80/443 for this name | two vhosts answering the same SNI is a silent misroute | `nginx -T \| grep -n 'server_name'` |

## Install

```bash
sudo cp nginx/ctt-server.conf /etc/nginx/sites-available/ctt-server.conf
sudo ln -sfn /etc/nginx/sites-available/ctt-server.conf /etc/nginx/sites-enabled/ctt-server.conf
# replace server_name, the two certificate paths and the upstream port with this host's values
sudo nginx -t && sudo systemctl reload nginx
```

## Verify from outside, not by reading the file

The chain the application will see is the evidence. Ask a hop what it received:

```bash
# 1. The tunnel local end must not answer a plain client on the public interface.
ss -lntp | grep -E ':(80|443|18080)\b'

# 2. TLS-only: port 80 redirects, an unknown path still carries HSTS.
curl -sI http://<host>/            | head -1     # 301
curl -sI https://<host>/nonexistent | grep -i 'strict-transport-security'

# 3. The client address and the scheme survive both hops. The application masks
#    addresses in its own log (203.0.113.***), so read the unmasked value from
#    this host's access log instead.
tail -n 5 /var/log/nginx/ctt-access.log
```

## What this file does **not** secure

- **The tunnel port itself.** `frps` publishes 18080 so the tunnel can reach this
  hop; the firewall rule that keeps it off the public internet is part of the
  trust boundary. Reaching 18080 directly lets a caller forge `X-Forwarded-For`
  and `X-Forwarded-Proto`, so changing that rule requires re-running the checks
  above. The restriction and its revisit triggers live in
  `docs/decisions/ADR-0003-redis-source-restriction.md` and the runbook.
- **The application's own limits.** Per-user fairness is enforced in the
  application; this file only decides whether a request survives long enough to
  reach it.

## Keeping the two hops in step

This vhost and the Caddy edge (`deploy/local/Caddyfile`) both cap the body at the
same number of bytes — 4 MiB, stated as `4194304` here and `4MiB` there. Change
one and the other, then re-run the probes in `docs/ops-runbook.md`
("Edge headers and limits").
