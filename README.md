# SMS Gateway

Your own SMS gateway. Apps queue SMS over an HTTP API; Android phones with SIM cards take them, text them and report back. For testing, an app can be switched to Telegram instead: its messages are posted to a group for free. A built-in web console shows what is waiting, what went out, and which phones are online.

```
app ──POST /api/v1/messages──▶ gateway (queue in Postgres) ◀──POST /api/v1/phone/pull── Android phone ──SMS──▶ customer
                                                            ◀──POST /api/v1/phone/report──
```

Phones ask for work every few seconds, so they only need outgoing internet access: no port forwarding, no public IP.

## Run it

```bash
cp .env.example .env        # then fill in ADMIN_PASSWORD, DB_PASSWORD, …
docker compose up -d --build
```

Open http://localhost:8094 and sign in as `admin` with that password. Docker builds the code itself; no Java or Maven needed on the host.

To run from the IDE instead, start only the database (`docker compose up -d db`), put your settings in a `.env` file in this folder (git-ignored, never packed into the jar), and run `SmsGatewayApplication`:

```
ADMIN_PASSWORD=change-me
TELEGRAM_BOT_TOKEN=…
TELEGRAM_CHAT_ID=…
```

## First steps in the console

1. **Phones** → add a phone. Copy the key and the setup command it shows (shown once).
2. On the phone: install **Termux** and **Termux:API** from F-Droid, run `pkg install termux-api curl jq`, allow SMS, turn off battery optimisation for Termux, then paste the setup command. The phone turns **Online**.
3. **Send a test** → it should arrive within seconds.
4. **Apps** → add each app that will send SMS and give it the key shown.

## Free test delivery with Telegram

For testing you can skip real SMS: switch an app to **Telegram** on the Apps page, and its messages (codes included) are posted to a Telegram group by a bot instead of texted. Free, no phone needed. Use it for test environments only.

1. In Telegram, talk to **@BotFather**, send `/newbot`, and keep the token it gives you.
2. Make a group for your testers and add the bot to it. Send any message in the group.
3. Open `https://api.telegram.org/bot<TOKEN>/getUpdates` in a browser and find `"chat":{"id":-100…}`: that number is the chat id.
4. Optional, for a group with **Topics** on: open the topic, copy its link (`https://t.me/c/1234567890/42`) and take the last number (`42`) as `TELEGRAM_TOPIC_ID`. It is also `"message_thread_id"` in `getUpdates` after you post in the topic. Leave it out to post to General.
5. Start the gateway with `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` set (locally: the `.env` file, never `application.yml`). The Overview shows "Posting to group …".
6. On **Apps**, press **Use Telegram** on the test app. **Send a test** can also pick Telegram.

Telegram allows a bot about 20 messages a minute in one group; beyond that, messages are retried and may fail.

## API for apps

Header `X-Api-Key: <key from the Apps page>`.

```bash
curl -X POST http://localhost:8094/api/v1/messages \
  -H 'X-Api-Key: sgc_…' -H 'Content-Type: application/json' \
  -d '{"to": "012345678", "text": "123456 is your code.", "reference": "otp-42", "validSeconds": 300}'
```

| Field | |
|---|---|
| `to` | Local (`012345678`) or international (`+85512345678`) |
| `text` | Up to 1000 characters; Khmer is fine, long text goes in several parts |
| `reference` | Optional. Sending again with the same one returns the first message and sends nothing new |
| `validSeconds` | Optional, default 300. Still unsent after that, it expires instead of arriving late |

`GET /api/v1/messages/{id}` returns where it stands: `PENDING` → `SENDING` → `SENT`, or `FAILED` / `EXPIRED`.

Every answer is `{"success": true, "data": …}` or `{"success": false, "error": {"code", "message"}}`. Full docs at `/swagger-ui.html` (signed in).

## How messages move

- A phone takes up to 5 waiting messages and holds them for 2 minutes (`SMS_LEASE`). If it never reports, another phone may take them.
- A failed send is tried again, up to 3 times (`SMS_MAX_ATTEMPTS`), then `FAILED`.
- The text is wiped once a message is final, so codes do not stay in the database. The console never shows it.
- Several phones can run at once; each message goes to exactly one of them.

## Settings

| Variable | Default | |
|---|---|---|
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / none | Console sign-in. No password = nobody can sign in. A `{bcrypt}…` hash works too |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | local compose database | Postgres |
| `PORT` | `8094` | |
| `SMS_DEFAULT_VALIDITY` / `SMS_MAX_VALIDITY` | `5m` / `1h` | How long a message may wait |
| `SMS_LEASE` / `SMS_MAX_ATTEMPTS` | `2m` / `3` | |
| `PHONE_ONLINE_WITHIN` | `30s` | A phone seen within this is shown online |
| `TIME_ZONE` | `Asia/Phnom_Penh` | Times in the console |
| `PUBLIC_URL` | none | The outside HTTPS address, shown in setup commands |
| `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` | none | The bot and group for apps set to Telegram |
| `TELEGRAM_TOPIC_ID` | none | A topic inside that group; none posts to General |

Put it behind HTTPS (a reverse proxy) before phones or apps reach it over the internet: keys travel in headers.

## Host it on a Mac server

CI publishes `ghcr.io/siveing/sms-service` on every push to `main` (`latest` plus `sha-<commit>`, for Intel and Apple-silicon Macs). The server needs only Docker, this compose file and a `.env`:

1. **Docker:** install Docker Desktop (or OrbStack) and turn on *Start when you sign in*. Both containers restart on their own after that.
2. **Files:** on the server, make a folder (e.g. `~/sms-gateway`) with `deploy/docker-compose.server.yml` saved as `docker-compose.yml`, and `.env.example` saved as `.env`.
3. **Settings:** fill in `.env`: a real `ADMIN_PASSWORD` and `DB_PASSWORD` (the database keeps the password it was first created with), `PUBLIC_URL`, the Telegram values. Pin a version with `SMS_GATEWAY_IMAGE=ghcr.io/siveing/sms-service:sha-<commit>` if you prefer.
4. **Start:** `docker compose up -d`, then `docker compose ps` shows both healthy. The console is on http://<server>:8094.
5. **Update:** `docker compose pull && docker compose up -d`. Data stays in the `sms-gateway-db` volume.

Building from source instead (`docker compose up -d --build` with the root `docker-compose.yml`) also works and needs no registry.

### Put it on the internet

Apps and phones outside the office need an HTTPS address. The simplest is a **Cloudflare Tunnel** (`deploy/cloudflared.example.yml`): no router ports, free HTTPS, and it only lets `/api/…` and `/phone/…` through, so the console stays reachable on the office network only (http://macserver:8094).

Set `PUBLIC_URL` to that HTTPS address, so the console's setup commands and examples show it rather than the office address.

### Give another system access (e.g. SME SIT)

1. On the server's console, **Apps → Add**, name it after that system (e.g. "SME SIT"), copy the key (shown once). Press **Use Telegram** if it is a test environment.
2. Hand them the address and key. For SME: `SMS_PROVIDER=gateway`, `SMS_GATEWAY_URL=https://sms.example.com`, `SMS_GATEWAY_API_KEY=<key>`.
3. If a key leaks, press **New key** (or **Disable**) on that app; the old key stops working at once.

### Backups

`docker compose exec db pg_dump -U sms_gateway sms_gateway > backup.sql` — only message history and the app/phone list live there; texts are wiped once sent.

## Limits of sending from a phone

- Customers see the SIM's phone number, not a brand name.
- Operators may block a SIM that sends many similar messages; check the SIM's terms.
- "Sent" means the phone handed the SMS to Android; it does not prove the customer received it.
- Each phone sends roughly a handful per minute. Add phones for more.
