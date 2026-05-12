#!/usr/bin/env python3
"""OpenAI-compatible stub model server for local IM x Agent demos."""

from http.server import BaseHTTPRequestHandler, HTTPServer
import json


def extract_last_user_text(payload):
    messages = payload.get("messages") or []
    for message in reversed(messages):
        if message.get("role") == "user":
            return (message.get("content") or "").strip()
    return ""


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_response(404)
            self.end_headers()
            return

        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length) if length > 0 else b"{}"
        payload = json.loads(raw.decode("utf-8") or "{}")
        user_text = extract_last_user_text(payload)
        reply = f"模型回复：{user_text}" if user_text else "模型回复：已收到"
        body = {
            "choices": [
                {
                    "message": {
                        "content": reply
                    }
                }
            ]
        }
        encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, fmt, *args):
        return


if __name__ == "__main__":
    server = HTTPServer(("127.0.0.1", 18080), Handler)
    print("Agent stub listening on http://127.0.0.1:18080/v1/chat/completions")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
