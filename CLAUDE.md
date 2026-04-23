# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Minecraft Fabric mod (1.21.1) implementing a server-based economy system, plus a companion Next.js web dashboard. The mod adds currency, player shops, auction house, jobs, plots with taxation, marketplace, and service subscriptions.

## Build & Run Commands

### Minecraft Mod (Java/Gradle)
```bash
# Build the mod JAR (output: build/libs/rivers_economy-1.0.0.jar)
gradle build

# Clean build
gradle clean build

# Run development Minecraft server
gradle runServer

# Compile only
gradle compileJava
```

### Web Dashboard (Next.js)
```bash
cd riverside/my-app

npm run dev      # Development server at http://localhost:3000
npm run build    # Production build
npm start        # Start production server
npm run lint     # Run ESLint
```

## Architecture

### Mod Entry Point & Initialization
`CurrencyMod.java` is the Fabric mod entry point. On server start it:
1. Registers all 19 command handlers (one class per command group in `command/`)
2. Initializes singleton managers: `EconomyManager`, `AuctionManager`, `JobManager`, `PlotManager`, `ServiceManager`, `DataManager`
3. Creates a `ShopManager` per world on world load
4. Registers Fabric API event callbacks for server lifecycle, player join/disconnect, and server ticks
5. Starts the auto-save timer (5-minute intervals via `DataManager`)

### Data Persistence
`DataManager` is the central persistence layer using GSON for JSON serialization. All data lives under `currency_mod/` in the server directory:
- `economy.json`, `shops.json`, `auction_pending_items.json`, `jobs.json`, `plots.json`, `services.json`, etc.
- Auto-saves every 5 minutes and on server stop
- Maintains up to 10 timestamped backups; auto-rolls back to latest backup on corruption

### Mixin System
Five mixins in `mixin/` hook into Minecraft internals:
- `SignEditMixin` / `SignBlockMixin` / `SignBlockEntityMixin` — shop sign detection and interaction
- `ChestBlockMixin` — chest interactions for shops
- `WorldMixin` — world-level events

### Shop System
Eight classes in `shop/` handle sign-based shops. Shops are per-world (`ShopManager` holds a `Map<World, List<Shop>>`). Sign detection flows: mixin intercepts sign placement → `ShopManager` validates format → creates `Shop` object → persists via `DataManager`. Offline transaction tracking is built in.

### Auction System
`AuctionManager` (singleton) manages real-time bidding. Server tick events drive countdown notifications and auto-completion. Items for offline players queue in `auction_pending_items.json` and are delivered on player join.

### Jobs System
`JobManager` generates random jobs per player. Supports experience levels, streaks, booster items (basic/premium), and job skips. State tracked across `jobs.json`, `job_levels.json`, `job_streaks.json`, `job_boosters.json`, `job_skips.json`.

### Configuration
`ModConfig` (in `config/`) reads/writes `currency_mod/config/config.json`. Auto-created with defaults on first run. Configures plot prices, tax rates, job multipliers, and optional web sync API endpoint/key.

### Web Dashboard
Next.js app in `riverside/my-app/` with Firebase backend. API routes in `app/api/` poll server data. Pages cover shops, leaderboard, jobs, patch notes, and admin. Uses Tailwind CSS 4 and React 19.

## Tech Stack
- **Mod:** Java 17, Minecraft 1.21.1, Fabric Loader 0.15.7, Fabric API 0.115.1, GSON
- **Build:** Gradle 9 with Fabric Loom 1.10
- **Web:** Next.js 16, React 19, TypeScript 5, Tailwind CSS 4, Firebase 12
