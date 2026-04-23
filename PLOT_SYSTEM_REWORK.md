# Plot System Rework — Implementation Specification

## 1. Overview

This document describes the full rework of the plot system to bind abstract plot ownership records to real Minecraft world chunks, replace all player-facing text commands with chest GUIs, and add admin tooling to register and configure which chunks are buyable.

### Goals

- **Admin:** Register any chunk as a buyable plot via `/plots create`, configure which plot types it can be purchased as, edit later via `/plots edit`.
- **Player:** `/plots` opens an inventory GUI showing owned plots and a button to buy the current chunk (if it is registered and available).
- **Purchase flow:** Selecting a type in the buy UI feeds directly into the existing `PlotManager.buyPlot()` / `processPurchase()` logic, so taxation, discounting, and persistence all continue to work unchanged.
- **No breaking changes** to the existing `Plot` ownership model or tax collection system. The rework adds a layer on top.

---

## 2. Conceptual Model

### 2.1 Current model (unchanged)

```
playerPlots: Map<UUID, List<Plot>>
Plot { UUID id, PlotType type, long purchaseTimestamp }
```

A `Plot` is a pure ownership record — it carries no location. Tax collection and selling work against this model.

### 2.2 New model — WorldPlot (chunk registry)

```
registeredPlots: Map<ChunkKey, WorldPlot>
WorldPlot {
    ChunkKey location,          // chunk X/Z + dimension key
    Set<PlotType> enabledTypes, // which types the admin has toggled on
    UUID ownerUuid,             // null if unowned
    UUID plotId                 // matches Plot.id in playerPlots — null if unowned
}
```

A `ChunkKey` is a value type: `(int chunkX, int chunkZ, String dimensionKey)`.

The link between the two models is `WorldPlot.plotId == Plot.id`. When a player buys a chunk, a `Plot` object is created via the existing `processPurchase()` path and its UUID is stored in `WorldPlot.plotId`. When a player sells, the `Plot` is removed from `playerPlots` and `WorldPlot.ownerUuid`/`plotId` are cleared.

---

## 3. New & Modified Files

### 3.1 New files

| File                                  | Purpose                                           |
| ------------------------------------- | ------------------------------------------------- |
| `plots/ChunkKey.java`                 | Value type for chunk identity                     |
| `plots/WorldPlot.java`                | Chunk-bound plot registry entry                   |
| `ui/PlotTypeSelectScreenHandler.java` | Admin UI — toggle plot types for a chunk          |
| `ui/PlotInfoScreenHandler.java`       | Player UI — owned plot dashboard                  |
| `ui/PlotBuyScreenHandler.java`        | Player UI — select type to purchase current chunk |

### 3.2 Modified files

| File                        | Change summary                                                                |
| --------------------------- | ----------------------------------------------------------------------------- |
| `plots/PlotManager.java`    | Add `registeredPlots` map, persistence, and chunk-aware buy/sell wrappers     |
| `command/PlotsCommand.java` | Replace `/plots` base and add `/plots create`, `/plots edit`, `/plots remove` |
| `config/ModConfig.java`     | No structural change needed; `PlotType.values()` drives the UI slot count     |

---

## 4. Data Model Detail

### 4.1 ChunkKey.java

```java
package net.currencymod.plots;

import java.util.Objects;

public final class ChunkKey {
    public final int chunkX;
    public final int chunkZ;
    public final String dimension; // e.g. "minecraft:overworld"

    public ChunkKey(int chunkX, int chunkZ, String dimension) { ... }

    // equals/hashCode based on all three fields
    // toString: "chunkX,chunkZ,dimension" — used as JSON map key
    public static ChunkKey fromString(String s) { ... }
}
```

### 4.2 WorldPlot.java

```java
package net.currencymod.plots;

import java.util.Set;
import java.util.UUID;

public class WorldPlot {
    private final ChunkKey location;
    private Set<PlotType> enabledTypes;  // mutable — admin editable
    private UUID ownerUuid;              // null = unowned
    private UUID plotId;                 // matches Plot.id

    // constructors, getters, setters
    public boolean isOwned()       { return ownerUuid != null; }
    public boolean isAvailable()   { return !isOwned() && !enabledTypes.isEmpty(); }
}
```

### 4.3 Persistence — plots.json additions

The existing `plots.json` schema is extended with a top-level `"registeredPlots"` key. Existing keys (`lastTaxDay`, `taxedPlayers`, `playerPlots`) are untouched.

```json
{
  "lastTaxDay": 123456,
  "taxedPlayers": [...],
  "playerPlots": { ... },
  "registeredPlots": {
    "10,5,minecraft:overworld": {
      "enabledTypes": ["PERSONAL", "FARM"],
      "ownerUuid": null,
      "plotId": null
    },
    "11,5,minecraft:overworld": {
      "enabledTypes": ["BUSINESS"],
      "ownerUuid": "550e8400-e29b-41d4-a716-446655440000",
      "plotId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    }
  }
}
```

---

## 5. PlotManager Changes

### 5.1 New fields

```java
private final Map<ChunkKey, WorldPlot> registeredPlots = new HashMap<>();
```

### 5.2 New utility methods

```java
// Returns the WorldPlot for the chunk the player is standing in, or null.
public WorldPlot getWorldPlotAt(ServerPlayerEntity player)

// Returns the WorldPlot for an explicit chunk key, or null.
public WorldPlot getWorldPlot(ChunkKey key)

// Registers a chunk as a buyable plot with its initial enabled type set.
// Called by /plots create. Does nothing if already registered.
public void registerPlot(ChunkKey key, Set<PlotType> enabledTypes)

// Removes a chunk from the registry. Fails (returns false) if currently owned.
public boolean unregisterPlot(ChunkKey key)

// Updates the enabled types for an already-registered plot.
// Called when admin saves the PlotTypeSelectScreenHandler.
public void updateEnabledTypes(ChunkKey key, Set<PlotType> enabledTypes)
```

### 5.3 New chunk-aware buy method

```java
/**
 * Purchases the WorldPlot at the player's current chunk as the given type.
 * Validates: plot is registered, not owned, type is enabled, player has funds.
 * On success: calls processPurchase() for the existing Plot + tax logic,
 * then sets WorldPlot.ownerUuid and WorldPlot.plotId.
 */
public boolean buyWorldPlot(ServerPlayerEntity player, WorldPlot worldPlot, PlotType type)
```

This wraps the existing `processPurchase()` — it does not duplicate currency deduction or Plot creation logic.

### 5.4 Modified sell method

`sellPlot(ServerPlayerEntity player, PlotType type)` currently removes the first matching `Plot` from `playerPlots`. After the rework:

1. Before removing, scan `registeredPlots` for any `WorldPlot` whose `plotId` matches the removed `Plot.id`.
2. If found, clear `WorldPlot.ownerUuid` and `WorldPlot.plotId`.

This ensures world state stays consistent with ownership records. If no matching `WorldPlot` is found (legacy plots created before this rework), the sell proceeds as before.

### 5.5 loadData / saveData

The GSON serialization in `loadData()` and `saveData()` is extended to read/write the `"registeredPlots"` map. The `ChunkKey.toString()` / `ChunkKey.fromString()` methods provide the JSON map key encoding.

---

## 6. Commands

### 6.1 `/plots` — reworked for players

**Who:** Any player (no permission requirement).  
**What:** Opens `PlotInfoScreenHandler`.  
**Behaviour change:** The base `/plots` command no longer prints text. It always opens the GUI. All sub-commands for text-based buying/selling (`/plots buy`, `/plots sell`, `/plots confirm`, `/plots cancel`) are **removed** from player access. They can remain registered at op-level 2 as hidden admin escape hatches if desired, but should not appear in tab-complete for non-ops.

### 6.2 `/plots create` — new admin command

**Permission:** Level 2 (op).  
**Who:** Admin standing inside a chunk they want to register.

**Flow:**

1. Derive `ChunkKey` from the admin's current position (`player.getBlockPos().getX() >> 4`, `>> 4` for Z, dimension from `player.getWorld().getRegistryKey().getValue().toString()`).
2. If the chunk is already registered, send an error: `"This chunk is already registered. Use /plots edit to modify it."`.
3. Otherwise, call `PlotManager.registerPlot(key, defaultEnabledTypes)` where `defaultEnabledTypes` is a set containing the **first** `PlotType` in `PlotType.values()` (i.e., `PERSONAL` by default).
4. Immediately open `PlotTypeSelectScreenHandler` for the admin so they can adjust which types are enabled.

### 6.3 `/plots edit` — new admin command

**Permission:** Level 2 (op).  
**Who:** Admin standing inside an already-registered chunk.

**Flow:**

1. Derive `ChunkKey` from current position.
2. Look up `PlotManager.getWorldPlot(key)`.
3. If not found: `"No registered plot in this chunk. Use /plots create first."`.
4. Open `PlotTypeSelectScreenHandler` populated with the current `enabledTypes` state.

### 6.4 `/plots remove` — new admin command

**Permission:** Level 2 (op).  
**Who:** Admin standing inside a registered chunk.

**Flow:**

1. Derive `ChunkKey`.
2. If chunk is not registered: error message.
3. If chunk is currently owned: `"This plot is owned by a player. It cannot be removed while owned."`.
4. Otherwise: `PlotManager.unregisterPlot(key)`, confirm: `"Plot removed from registry."`.

---

## 7. UI Screens

All screens use `GenericContainerScreenHandler` with `ScreenHandlerType.GENERIC_9X3` (27 slots), matching the pattern in `ConfirmScreenHandler` and `MarketplaceScreenHandler`. All use `ConfirmScreenHandler.makeItem()` and `ConfirmScreenHandler.makeFiller()` (package-visible helpers) for item construction.

---

### 7.1 PlotTypeSelectScreenHandler — admin type-toggle UI

**Title:** `"Configure Plot Types"`  
**Opened by:** `/plots create`, `/plots edit`

#### Layout (27 slots, 3 rows of 9)

```
Row 0 (slots 0–8):   [ filler ][ filler ][ filler ][ filler ][ filler ][ filler ][ filler ][ filler ][ filler ]
Row 1 (slots 9–17):  [ filler ][ type_0 ][ type_1 ][ type_2 ][ type_3 ][ type_4 ][ type_5 ][ type_6 ][ filler ]
Row 2 (slots 18–26): [ filler ][ filler ][ filler ][ filler ][ SAVE   ][ filler ][ filler ][ filler ][ filler ]
```

- **Slots 9 and 17:** Grey stained glass pane fillers (row edge buffers).
- **Slots 10–16:** One slot per config-defined `PlotType`, in `PlotType.values()` order. Maximum 7 types (7 slots available). If fewer than 7 types are configured, the remaining slots are filler.
- **Slot 22:** Save button — lime concrete, name `"§a§lSave"`, lore `"§7Click to save changes."`.
- All other slots: filler (grey stained glass pane, blank name).

#### Per-type slot item

Each type slot item reflects whether the type is currently toggled **on** or **off**:

| State    | Item       | Name                  | Lore                                                                                                      |
| -------- | ---------- | --------------------- | --------------------------------------------------------------------------------------------------------- |
| Enabled  | `LIME_DYE` | `"§a§l<DisplayName>"` | `"§7Price: $<purchasePrice>"`, `"§7Daily Tax: $<dailyTax>/day"`, `"§a● Enabled"`, `"§7Click to disable."` |
| Disabled | `GRAY_DYE` | `"§7<DisplayName>"`   | `"§7Price: $<purchasePrice>"`, `"§7Daily Tax: $<dailyTax>/day"`, `"§8○ Disabled"`, `"§7Click to enable."` |

#### Interaction

- Clicking a type slot toggles its enabled state in the handler's local `Set<PlotType> enabledTypes`. The item in that slot is immediately replaced with the updated visual (no round-trip to server storage until Save).
- Clicking Save: calls `PlotManager.updateEnabledTypes(chunkKey, enabledTypes)`, saves data, closes screen, sends feedback: `"Plot types saved."`.
- Clicking filler or anything else: no-op (blocked, same as `ConfirmScreenHandler`).
- `quickMove` returns `ItemStack.EMPTY` (shift-click disabled).

#### Constructor signature

```java
public PlotTypeSelectScreenHandler(
    int syncId,
    PlayerInventory playerInv,
    SimpleInventory gui,
    ChunkKey chunkKey,
    Set<PlotType> currentEnabledTypes,
    ServerPlayerEntity admin
)
```

Static `open()` method follows the same pattern as `ConfirmScreenHandler.open()`.

---

### 7.2 PlotInfoScreenHandler — player dashboard

**Title:** `"Your Plots"`  
**Opened by:** `/plots` (any player)

#### Layout

```
Row 0 (slots 0–8):   [ filler ][ filler ][ filler ][ INFO   ][ filler ][ filler ][ filler ][ filler ][ filler ]
Row 1 (slots 9–17):  [ type_0 ][ type_1 ][ type_2 ][ type_3 ][ filler ][ filler ][ filler ][ filler ][ filler ]
Row 2 (slots 18–26): [ filler ][ filler ][ filler ][ filler ][ filler ][ filler ][ filler ][ BUY    ][ filler ]
```

- **Slot 3 (INFO):** Summary item — `PAPER`, name `"§e§lYour Plot Summary"`.  
  Lore:
  - `"§7Total plots owned: §f<n>"`
  - One line per owned type with count (only types with count > 0): `"§7• <color><DisplayName>§7: §f<count>x"`
  - `"§7Daily tax: §6$<totalTax>/day"`
  - If no plots: `"§7You don't own any plots yet."`

- **Slots 9–12 (type items, 0–3):** One item per `PlotType` the player owns at least one of. Uses the type's display colour. Item is `GRASS_BLOCK` (Personal), `HAY_BLOCK` (Farm), `EMERALD_BLOCK` (Business), `IRON_BLOCK` (Industrial) as visual indicators. Name `"§<color>§l<DisplayName> Plot"`. Lore:
  - `"§7Owned: §f<count>x"`
  - `"§7Daily Tax: §6$<count × dailyTax>/day"`
  - `"§7Purchase Price: §f$<purchasePrice> each"`

  Slots for types the player does not own are filler.

- **Slot 25 (BUY):** Changes based on context:
  - **Default (not in a buyable plot):**  
    Item: `GRAY_CONCRETE`, name `"§7Browse Plots"`,  
    lore: `"§8Stand in a registered plot chunk"`, `"§8to purchase it."`.  
    Click: no-op (send chat message: `"You are not standing in an available plot."`).
  - **In an unowned, registered plot with ≥1 enabled type:**  
    Item: `LIME_CONCRETE`, name `"§a§lBuy This Plot"`,  
    lore: `"§7You are standing in an available plot."`, `"§7Click to see purchase options."`.  
    Click: close this screen and open `PlotBuyScreenHandler` for the current chunk.
  - **In an owned plot owned by this player:**  
    Item: `YELLOW_CONCRETE`, name `"§e§lSell This Plot"`,  
    lore: `"§7You own this plot."`, `"§7Refund: §6$<refundAmount> (20%)"`.  
    Click: close and trigger sell flow (see §8.1).
  - **In a plot owned by another player:**  
    Item: `RED_CONCRETE`, name `"§c§lPlot Taken"`,  
    lore: `"§7This plot is already owned."`.  
    Click: no-op.

> **Note on detecting current chunk context:** When `PlotInfoScreenHandler.open()` is called, derive `ChunkKey` from the player's position at that moment. Pass the resolved `WorldPlot` (or `null`) into the handler constructor so the BUY slot can be populated correctly.

#### Interaction

- Clicking a type item: no-op (informational only).
- Clicking BUY slot: behaviour described above.
- All other slots: no-op.
- `quickMove` returns `ItemStack.EMPTY`.

---

### 7.3 PlotBuyScreenHandler — select type to purchase

**Title:** `"Buy Plot — Select Type"`  
**Opened by:** Clicking BUY in `PlotInfoScreenHandler` when in an available plot.

#### Layout (mirrors PlotTypeSelectScreenHandler but read-only)

```
Row 0 (slots 0–8):   [ filler × 9 ]
Row 1 (slots 9–17):  [ filler ][ type_0 ][ type_1 ][ ... up to type_6 ][ filler ]
Row 2 (slots 18–26): [ filler ][ filler ][ filler ][ filler ][ BACK   ][ filler ][ filler ][ filler ][ filler ]
```

- **Slots 10–16:** Only the **enabled** types for this `WorldPlot` are shown as clickable. Disabled types for this chunk are either hidden (filler) or shown as grey dye with lore `"§8Not available for this plot."` — choose the greyed-out approach for transparency.
- **Slot 22 (BACK):** Red concrete, `"§c§lBack"`, lore `"§7Return to your plot info."`. Click: close and re-open `PlotInfoScreenHandler`.

#### Per-type slot item (enabled types only)

Item: `LIME_DYE`, name `"§a§l<DisplayName>"`.  
Lore:

- `"§7Purchase Price: §6$<purchasePrice>"`
- `"§7Daily Tax: §6$<dailyTax>/day"`
- `""`
- `"§eClick to purchase this plot as <DisplayName>."`

#### Interaction

- Clicking an **enabled type slot**: close screen and open `ConfirmScreenHandler` (already exists).
  - Info item: the same dye item as in the slot.
  - Info lore: `"§7Purchase plot as: §a<DisplayName>"`, `"§7Cost: §6$<purchasePrice>"`, `"§7Daily Tax: §6$<dailyTax>/day"`.
  - `onConfirm` lambda: calls `PlotManager.buyWorldPlot(player, worldPlot, selectedType)`. On success send feedback `"§aPlot purchased!"`. On failure send error `"§cYou cannot afford this plot."`.
  - `onCancel` lambda: re-open `PlotBuyScreenHandler`.
- Clicking a **disabled type slot** (greyed out): no-op.
- Clicking BACK: re-open `PlotInfoScreenHandler`.

---

## 8. Supplementary Flows

### 8.1 Sell from PlotInfoScreenHandler

When the player is standing in a plot they own and clicks the SELL button:

1. Close the PlotInfoScreenHandler.
2. Open `ConfirmScreenHandler`:
   - Info item: `BARRIER`, name `"§c§lSell Plot"`.
   - Lore: `"§7Sell your <DisplayName> plot?"`, `"§7Refund: §6$<refundAmount> (20% of $<purchasePrice>)"`.
   - `onConfirm`: call `PlotManager.sellPlot(player, worldPlot.getType())` where `worldPlot` is the chunk the player was in. The sell method (§5.4) will clear `WorldPlot.ownerUuid/plotId`.
   - `onCancel`: re-open `PlotInfoScreenHandler`.

> The sell action must resolve _which_ Plot to remove. Since `WorldPlot.plotId` now stores the exact `Plot.id`, `PlotManager.sellPlot` should be extended (or a new overload added) that accepts a `UUID plotId` to remove the precise plot rather than the first match by type.

### 8.2 Legacy plots (no chunk binding)

Players may own `Plot` objects created before this rework. These have no `WorldPlot.plotId` pointing at them. They continue to generate daily tax and appear in the PlotInfoScreenHandler's summary. They cannot be sold via the chunk-based UI since there is no chunk to stand in — the existing `/plots sell <type>` command can remain available at op level, or a "sell legacy plot" item can be added to the dashboard for types where the player owns more plots than there are bound WorldPlots.

This is a known edge case; the simplest resolution is to keep `/plots sell <type>` accessible to all players as a text command specifically for unbound legacy plots, while the GUI handles bound plots.

---

## 9. ChunkKey Derivation Helper

All commands and screen opens need to compute a `ChunkKey` from a player's current position:

```java
private static ChunkKey chunkKeyFromPlayer(ServerPlayerEntity player) {
    BlockPos pos = player.getBlockPos();
    int chunkX = pos.getX() >> 4;
    int chunkZ = pos.getZ() >> 4;
    String dimension = player.getWorld().getRegistryKey().getValue().toString();
    return new ChunkKey(chunkX, chunkZ, dimension);
}
```

This should be a static utility either in `ChunkKey` itself or in `PlotManager`.

---

## 10. Implementation Order

The following sequence minimises risk of merge conflicts and allows each step to be tested in isolation:

1. **`ChunkKey.java`** — pure value type, no dependencies.
2. **`WorldPlot.java`** — depends on `ChunkKey` and `PlotType`.
3. **`PlotManager` additions** — add `registeredPlots` map, load/save extensions, `registerPlot`, `unregisterPlot`, `updateEnabledTypes`, `getWorldPlotAt`, `buyWorldPlot`, and the sell modification (§5.4).
4. **`PlotTypeSelectScreenHandler.java`** — admin type-toggle UI. Depends on `PlotManager` and existing `ConfirmScreenHandler` helpers.
5. **`PlotBuyScreenHandler.java`** — player type-select UI. Depends on `PlotManager` and `ConfirmScreenHandler`.
6. **`PlotInfoScreenHandler.java`** — player dashboard. Depends on all of the above.
7. **`PlotsCommand.java`** — rework `/plots` and add `/plots create`, `/plots edit`, `/plots remove`. Depends on all screen handlers.
8. **Test & verify** — register a chunk, set types, buy as player, confirm WorldPlot is updated, verify tax still fires, sell and confirm WorldPlot is cleared.

---

## 11. Things That Do Not Change

| Concern                                                        | Status                                                                        |
| -------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| `Plot.java` data model                                         | Unchanged                                                                     |
| `PlotType` enum and config resolution                          | Unchanged                                                                     |
| `ModConfig` / `PlotTypeConfig`                                 | Unchanged                                                                     |
| Daily tax collection (scheduler, sleep detection, player join) | Unchanged                                                                     |
| `DataManager` auto-save / backup schedule                      | Unchanged — `saveData()` additions in PlotManager are automatically included  |
| `BuyPlotCommand.java` / `SellPlotCommand.java`                 | Can be left in place or removed; they bypass chunk checks but do not conflict |
| 20% refund on sell                                             | Unchanged (applied via existing `sellPlot()` path)                            |
| Bulk purchase discount                                         | Not applicable to chunk purchases (one chunk = one plot)                      |

---

## 12. Open Questions for Admin Review Before Implementation

1. **Should `/plots sell <type>` remain for players** to handle legacy unbound plots, or should legacy plots simply continue to tax without a GUI sell path?
   we will remove the existing plot tax systeme entirely, meaning i will have all players go in world and buy back their plots with the new system.

2. **Should owned WorldPlots display the owner's name** in a sign or particle effect in-world, or is in-UI display sufficient?
   in ui is sufficient, doing /plots should also have a item to show the current plot info and whether it is owned and by who or buyable

3. **Should `/plots create` auto-register all types as enabled** (requiring admin to deselect) or start with only the first type enabled (requiring opt-in)? The spec currently uses first-type-only.
   auto register all types as enabled

4. **Is bulk purchase (`/plots buy <type> <quantity>`) still desired** for buying multiple chunks at once, or is the new model strictly one-chunk-at-a-time?
   we will implement bulk purchasing later, remove it in the new gui, dont remove the old code

5. **What happens if a player stands in a registered plot but has insufficient funds?** The current spec shows the BUY button and lets the ConfirmScreenHandler's `onConfirm` return an error. Should the PlotBuyScreenHandler pre-check balance and grey out types they cannot afford?
   add a precheck balance and grey out types they cannot afford
