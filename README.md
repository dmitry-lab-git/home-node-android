# home-node
Home-Node turns an Android phone into a modular, local-first API server. It is designed to be a universal hub for syncing data, receiving telemetry, and integrating external services (like WhatsApp, Telegram, or smart home devices) without relying on external cloud providers.

# Home-Node (Android)

**Local-first personal server in your pocket.**

## Core Concept
- **Plug & Play API:** A lightweight HTTP/WebSocket server running inside an Android Foreground Service.
- **Modular by Design:** Start with the Traffic Tracker (TT) module, then extend with WhatsApp parsers, Ledger sync, or any custom API.
- **Local-First:** Your data stays on your devices (Phone + PC via WSL). No third-party cloud.

## Modules
1. **TT (Traffic Tracker):** Receives `up/down` reports from a PC agent and tracks daily limits.
2. **Ledger Sync:** Receives weekly reports from WSL background workers.
3. **WhatsApp Bridge (Planned):** Local API to sync messages, parse receipts, and send notifications to PC.
4. **Home Automation (Planned):** MQTT/HTTP gateway for local smart home devices.

## Architecture
- **Server Engine:** Ktor (embedded in Android).
- **Database:** Room (SQLite) for local caching and configuration.
- **Transport:** HTTP/REST (JSON) + WebSockets for real-time updates.
- **Security:** Token-based authentication for all incoming requests.

## Status
🚧 **Early Development** (Starting with the TT module integration).

## Related Repositories
- [Traffic Tracker Android](https://github.com/dmitry-lab-git/traffic-tracker-android) — The UI client for TT.
- (PC Agent & WSL Infra — coming soon)
