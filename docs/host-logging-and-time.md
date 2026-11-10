# Host Logging and Time

Both hosts keep their clock disciplined by **chrony** and their logs inside explicit size caps.
The numbers differ by host because their disks differ; the mechanisms are identical.

## Time

`chrony` replaces `systemd-timesyncd` on both hosts and is enabled at boot.

| Host | `SystemMaxUse` |
| --- | --- |
| access host | `200M` |
| deployment host | `500M` |

Verify after any change:

```bash
chronyc tracking      # Stratum > 0; Last offset / RMS offset well below 1 s
timedatectl           # NTP service active, "System clock synchronized: yes"
```

A fresh installation can report `Stratum 0 / Not synchronised` for the first seconds; wait about
ten seconds before judging it. If the offset stays above one second, check that UDP 123 is not
filtered before touching the configuration.

## journald caps

The drop-in `/etc/systemd/journald.conf.d/99-caps.conf` sets:

| Setting | Access host | Deployment host |
| --- | --- | --- |
| `SystemMaxUse` | `200M` | `500M` |
| `SystemKeepFree` | `1G` | `2G` |
| `RuntimeMaxUse` | `64M` | `64M` |

Retention is **size-only** on purpose: no `MaxRetentionSec`, so old journals survive until the cap
pushes them out. A time cap would shorten the forensic window for no storage benefit.

Apply and enforce immediately:

```bash
sudo systemctl restart systemd-journald
sudo journalctl --vacuum-size=<cap>     # e.g. 500M
journalctl --disk-usage
```

## Rotation

| Pattern | Policy | Why |
| --- | --- | --- |
| `/var/log/nginx/*.log` | `daily`, `maxsize 20M`, `rotate 14`, `compress`, `delaycompress`, USR1 reopen | bounds a bot-driven access-log burst without waiting for midnight |
| `/var/log/btmp` | `monthly`, `maxsize 10M`, `rotate 3` | failed-login records grow with SSH scans (96 MB before the cap on the access host) |
| syslog, auth, kern, mail, user, cron | `weekly`, `rotate 4`, `maxsize 50M` | caps a single week that a brute-force burst would otherwise fill |

**Do not keep backups of rotation files inside `/etc/logrotate.d/`**: the directory is included
wholesale, so a `nginx.bak` is parsed as a second entry and logrotate exits with
`duplicate log entry`. Keep them under `/root/`.

## Verification

```bash
chronyc tracking
journalctl --disk-usage
sudo logrotate -d /etc/logrotate.conf >/dev/null 2>&1; echo $?    # must be 0
sudo logrotate -d /etc/logrotate.conf 2>&1 | grep '^error'       # must print nothing
du -sh /var/log
```

`logrotate -d` prints lines for log files whose names contain `error` (for example
`cups/error_log`); those are paths, not diagnostics. Judge the run by its exit code and by lines
starting with `error:`.

## Scope

Logs written by other services on the deployment host — desktop sharing, Redis and the Greenbone
components — are rotated by their own packages and are not covered by these caps.
