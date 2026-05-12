#!/usr/bin/env python3
"""Local debug SMTP server for workforce-hub development."""

import asyncore
import smtpd


class DebugServer(smtpd.DebuggingServer):
    pass


if __name__ == "__main__":
    server = DebugServer(("127.0.0.1", 1025), None)
    print("Debug SMTP listening on 127.0.0.1:1025")
    try:
        asyncore.loop()
    except KeyboardInterrupt:
        pass
