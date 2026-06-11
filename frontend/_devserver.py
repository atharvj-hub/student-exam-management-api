"""Dev-only static server for the frontend.

Plain `python -m http.server` lets the browser cache auth.js / *.jsx, so edits
don't show up on reload. This serves the same files with Cache-Control: no-store
so every reload re-fetches. Not used in production — the frontend is just static
files that any web server (or the allowed CORS origins :3000 / :5173) can host.
"""
import http.server
import os
import sys

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 3000
DIRECTORY = os.path.dirname(os.path.abspath(__file__))


class NoCacheHandler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIRECTORY, **kwargs)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0")
        self.send_header("Pragma", "no-cache")
        super().end_headers()


if __name__ == "__main__":
    with http.server.ThreadingHTTPServer(("127.0.0.1", PORT), NoCacheHandler) as httpd:
        # Open via localhost (not 127.0.0.1): the backend CORS allowlist matches by
        # exact origin, and the two hostnames are distinct origins to the browser.
        print(f"frontend dev server (no-cache) on http://localhost:{PORT}")
        httpd.serve_forever()
