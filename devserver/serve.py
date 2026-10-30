#!/usr/bin/env python3
"""MotorDash hot reload dev server.

Runs `./gradlew :dashboard:hotPayload --continuous` and serves the resulting dex payload
to the app on the car. Put it behind an HTTPS tunnel (ngrok, Pinggy, cloudflared, ...).

  GET /wait?since=<version>  -> blocks up to 25s until the payload version differs, returns it
  GET /payload               -> the current payload jar

Every request must carry the X-MotorDash-Token header.
"""

import argparse
import hmac
import re
import secrets
import subprocess
import sys
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

ROOT = Path(__file__).resolve().parent.parent
PAYLOAD = ROOT / "dashboard/build/hot/dashboard-payload.jar"
LOCAL_PROPS = ROOT / "local.properties"
TOKEN_KEY = "motordash.devToken"
WAIT_SECONDS = 25
SETTLE_SECONDS = 0.5  # don't serve a jar d8 is still writing
API_SOURCE = ROOT / "dashboard-api/src/main/kotlin/app/motordash/api/DashboardUi.kt"


def api_version() -> str:
    """API_VERSION the current payload is built against; apps built against another must not load it."""
    match = re.search(r"const val API_VERSION = (\d+)", API_SOURCE.read_text())
    return match.group(1) if match else "?"


def load_or_create_token() -> str:
    lines = LOCAL_PROPS.read_text().splitlines() if LOCAL_PROPS.exists() else []
    for line in lines:
        key, _, value = line.partition("=")
        if key.strip() == TOKEN_KEY and value.strip():
            return value.strip()
    token = secrets.token_urlsafe(24)
    lines.append(f"{TOKEN_KEY}={token}")
    LOCAL_PROPS.write_text("\n".join(lines) + "\n")
    print(f"Generated a dev token and saved it to {LOCAL_PROPS}", flush=True)
    return token


def current_version() -> int:
    """Payload mtime in ns, or 0 if there is no settled payload yet."""
    try:
        mtime = PAYLOAD.stat().st_mtime_ns
    except FileNotFoundError:
        return 0
    if time.time_ns() - mtime < SETTLE_SECONDS * 1e9:
        return 0
    return mtime


def make_handler(token: str):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if not hmac.compare_digest(self.headers.get("X-MotorDash-Token", ""), token):
                return self.send_error(401)
            url = urlparse(self.path)
            query = parse_qs(url.query)
            if query.get("api", [""])[0] != api_version():
                # Older APKs send no api param and show this as "HTTP 409".
                return self.send_error(409, "APK outdated, reinstall")
            if url.path == "/wait":
                since = int(query.get("since", ["0"])[0])
                deadline = time.monotonic() + WAIT_SECONDS
                version = current_version()
                while version in (0, since) and time.monotonic() < deadline:
                    time.sleep(0.2)
                    version = current_version()
                self.reply(str(version).encode(), "text/plain")
            elif url.path == "/payload":
                if not PAYLOAD.exists():
                    return self.send_error(404, "no payload built yet")
                self.reply(PAYLOAD.read_bytes(), "application/java-archive")
                print(f"→ pushed payload to {self.client_address[0]}", flush=True)
            else:
                self.send_error(404)

        def reply(self, body: bytes, content_type: str):
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *args):
            pass  # long-polls would flood the console

    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, default=8090)
    parser.add_argument("--host", default="127.0.0.1", help="use 0.0.0.0 to serve the LAN directly")
    parser.add_argument("--no-gradle", action="store_true", help="don't start the continuous Gradle build")
    args = parser.parse_args()

    token = load_or_create_token()
    # Bind first so a busy port fails fast instead of orphaning the Gradle build.
    server = ThreadingHTTPServer((args.host, args.port), make_handler(token))
    server.daemon_threads = True
    gradle = None
    if not args.no_gradle:
        gradle = subprocess.Popen(
            [str(ROOT / "gradlew"), ":dashboard:hotPayload", "--continuous", "--console=plain"],
            cwd=ROOT,
        )

    print(f"MotorDash dev server on http://{args.host}:{args.port}  token: {token}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        if gradle:
            gradle.terminate()
            gradle.wait()
    sys.exit(0)


if __name__ == "__main__":
    main()
