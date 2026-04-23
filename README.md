# Currency Mod for Minecraft Fabric 1.21

A comprehensive Minecraft Fabric mod that adds a server-based economy system with player shops, auctions, jobs, property ownership, and a marketplace.

## Features

- **Currency System:** A server-based economy system where players have a virtual currency balance
- **Commands:**

  - **Economy Commands:**
    - `/bal` or `/balance` - Check your current balance
    - `/balance <player>` - Check another player's balance
    - `/pay <player> <amount>` - Send money to another player
    - `/baltop` - View the top 10 richest players on the server
  - **Admin Commands:**
    - `/adminmoney pay <player> <amount>` - Give money to a player (admins only)
    - `/adminmoney fine <player> <amount>` - Remove money from a player (admins only)
  - **Trading Commands:**
    - `/trade <player> <amount>` - Send a trade request for the item in your hand
    - `/trade accept` - Accept a pending trade request
    - `/trade deny` - Deny a pending trade request
  - **Auction Commands:**
    - `/auction create <price> <minutes>` - Create an auction for the item in your hand
    - `/auction view` - View the currently running auction
    - `/auction cancel` - Cancel an auction you created (only if no bids yet)
    - `/bid <amount>` - Place a bid on the active auction
    - `/bid info` - View the current auction and your bid status
  - **Job Commands:**
    - `/jobs` - Show available commands for the jobs system
    - `/jobs list` - View available jobs with clickable activation
    - `/jobs info` - View your active job and progress
    - `/jobs complete` - Complete your active job and collect reward
    - `/jobs abandon` - Abandon your active job (with penalty)
  - **Plot Commands:**
    - `/plots` - View all plots you own and their tax information
    - `/plots buy <type>` - Purchase a plot of the specified type (Personal, Farm, or Business)
    - `/plots sell <type>` - Sell a plot you own
    - `/tax` - Force an immediate tax collection (admin only)
  - **Marketplace Commands:**
    - `/market` - Open the marketplace interface
    - `/market setchest` - Set the marketplace chest location (admins only)
  - **Data Management Commands:**
    - `/data save` - Force an immediate save of all data
    - `/data status` - View data management status and backup information
    - `/data backup create` - Create a manual backup of all data
    - `/data backup list` - List available backups
    - `/data backup restore <name>` - Restore data from a backup
    - `/testdata` - Run tests for data storage and file operations
    - `/modstatus` - Show comprehensive status information about the mod

- **Player Shops:** Create shops using signs attached to chests to buy and sell items

  - **Buy and Sell Shops:** Different shop types for different transaction directions
  - **Admin Shops:** Special shops with unlimited stock for server admins
  - **Transaction Notifications:** Get notified when players buy from or sell to your shops
  - **Offline Transaction Summary:** Receive a summary of all shop transactions that occurred while you were away

- **Trading System:** Send direct player-to-player trade requests for items

- **Auction House:** Create auctions for items and allow players to bid on them

  - **Real-time Bidding:** Players can place bids on active auctions
  - **Countdown Notifications:** Get notified when auctions are ending soon
  - **Item Recovery:** Automatically recover items if bidders or sellers are offline

- **Jobs System:** Complete jobs to earn money by collecting resources

  - **Randomized Jobs:** Jobs are randomly generated with varying requirements and rewards
  - **Progress Tracking:** Track your progress toward job completion
  - **Multiple Job Options:** Choose from multiple jobs based on your preferences

- **Plot System:** Purchase and manage different types of land plots

  - **Multiple Plot Types:** Personal, Farm, and Business plots with different pricing
  - **Daily Taxation:** Automatic tax collection based on plot type
  - **Tax Notifications:** Get notified when taxes are collected

- **Marketplace:** A global marketplace for buying items directly from the server

  - **Simple Interface:** Easy-to-use interface for browsing and purchasing items
  - **Centralized Shopping:** Buy common items without needing to find player shops

- **Robust Data Management:**
  - **Auto-Saves:** Regular automatic saving of all economy data
  - **Backup System:** Create and restore data backups
  - **Safe File Operations:** Protection against data corruption and loss
  - **Error Recovery:** Automatic recovery from data errors when possible

## Configuration

This mod uses a JSON-based configuration system that allows server administrators to customize various aspects without modifying code.

### Plot Configuration

Plot prices and taxes can be modified through the configuration file. By default, the configuration file is located at:

```
currency_mod/config/config.json
```

The configuration file is automatically created with default values when the server starts for the first time. You can modify this file to change plot prices and taxes.

Example configuration:

```json
{
  "plotConfig": {
    "plotTypes": {
      "PERSONAL": {
        "displayName": "Personal",
        "purchasePrice": 1000,
        "dailyTax": 1
      },
      "FARM": {
        "displayName": "Farm",
        "purchasePrice": 2000,
        "dailyTax": 2
      },
      "BUSINESS": {
        "displayName": "Business",
        "purchasePrice": 3000,
        "dailyTax": 3
      }
    }
  }
}
```

### Reloading Configuration

After modifying the configuration file, you can reload it without restarting the server using the following command:

```
/modconfig reload
```

This command requires operator permissions (permission level 2).

### Viewing Current Configuration

To view the current configuration settings, use:

```
/modconfig
```

This command will display the current plot prices and other configuration settings.

## Commands

- `/bal [player]` - Check your or another player's balance
- `/pay <player> <amount>` - Pay another player
- `/baltop` - View the richest players
- `/plots` - Manage your plots
- `/buyplot <type>` - Buy a plot
- `/sellplot <type>` - Sell a plot
- `/modconfig` - View and manage mod configuration

## How to Use

### Basic Currency Commands

- Check your balance: `/bal` or `/balance`
- Check another player's balance: `/balance <playername>`
- Send money to a player: `/pay <playername> <amount>`
  - Example: `/pay Steve 50` sends $50 to Steve
- View richest players: `/baltop` shows the top 10 richest players and their balances

### Direct Player Trading

The mod includes a direct player-to-player trading system:

1. **Initiating a Trade:**

   - Hold the item you want to sell in your main hand
   - Type `/trade <playername> <price>` to send a trade request
   - Example: `/trade Steve 50` offers to sell the item in your hand to Steve for $50

2. **Receiving a Trade:**

   - When someone sends you a trade request, you'll see a message with details about the offer
   - The message includes clickable [Accept] and [Deny] buttons
   - You can also type `/trade accept` or `/trade deny`
   - Trade requests expire after 1 minute if not accepted or denied

3. **Completing a Trade:**
   - When you accept a trade, the system checks that:
     - The buyer has enough money
     - The seller still has the item
     - The buyer has inventory space
   - If all conditions are met, the item is transferred to the buyer and the money to the seller
   - Both players receive confirmation messages

### Creating a Shop

1. Place a chest
2. Place a sign on the chest (either on top or attached to the side)
3. Fill the chest with the items you want to sell (for Buy shops) or leave space for items to be received (for Sell shops)
4. Write the following on the sign:

   - First line: `[Buy]` (for a shop where players buy from you) or `[Sell]` (for a shop where players sell to you)
   - Second line: The item name (e.g., `Diamond`)
   - Third line: The quantity (e.g., `64`)
   - Fourth line: The price with $ symbol (e.g., `$100`)
   - Example Buy Shop:
     ```
     [Buy]
     Diamond
     64
     $100
     ```
   - Example Sell Shop:
     ```
     [Sell]
     Cobblestone
     32
     $5
     ```

5. For admin shops, use `[AdminBuy]` or `[AdminSell]` on the first line (requires permission level 2+)
6. The shop will be automatically created when you place the sign, and a confirmation message will appear

### Using a Shop

- Right-click on a shop sign to buy or sell (depending on the shop type)
- **Buy Shop:** Take items from the chest and pay money to the shop owner
  - You need enough money in your balance
  - The chest must contain enough items
  - You must have inventory space for the items
- **Sell Shop:** Add items to the chest and receive money from the shop owner
  - The shop owner must have enough money
  - You must have the items in your inventory
  - The chest must have space for the items
- **Admin Shops:** Function like regular shops but have unlimited stock and never run out of money
- Transaction details and any errors will be displayed as messages

### Auction System

The mod includes an auction system that allows players to auction items and bid on them:

1. **Creating an Auction:**

   - Hold the item you want to auction in your main hand
   - Type `/auction create <startprice> <minutes>` to create an auction
   - Example: `/auction create 100 5` creates an auction starting at $100 that lasts for 5 minutes
   - The item will be removed from your inventory and placed in the auction

2. **Viewing the Active Auction:**

   - `/auction view` - View the currently running auction
   - Only one auction can be active at a time

3. **Placing Bids:**

   - Use `/bid <amount>` to place a bid on the active auction
   - Example: `/bid 150` to bid $150 on the current auction
   - You must have enough money in your balance to place the bid
   - The bid must be higher than the current highest bid by at least 5%

4. **Auction Completion:**

   - When an auction ends:
     - If there were bids, the highest bidder receives the item and the seller receives the money
     - If there were no bids, the item is returned to the seller
   - Players will be notified about auction results

5. **Cancelling an Auction:**
   - You can cancel your own auction if it has no bids yet
   - Use `/auction cancel` to cancel the current auction
   - When cancelled, the item is returned to your inventory
   - You cannot cancel an auction after someone has placed a bid

### Jobs System

The mod includes a jobs system that allows players to earn money by collecting resources:

1. **Viewing Available Jobs:**

   - Use `/jobs list` to see a list of available jobs
   - Each job requires collecting a specific quantity of a specific item
   - Jobs are randomly generated with variations in quantity and reward
   - Click directly on a job in the list to activate it

2. **Activating a Job:**

   - Click on a job in the list to activate it
   - You can only have one active job at a time
   - Active jobs are highlighted with a green 【ACTIVE】 tag

3. **Checking Your Active Job:**

   - Use `/jobs info` to see details about your active job
   - The command shows the item required, quantity, and reward
   - It also displays your progress, including how many items you've collected
   - If you have all required items, you'll see a completion prompt

4. **Completing a Job:**

   - Collect the required items in your inventory
   - Use `/jobs complete` to turn in the items and receive your reward
   - The items will be removed from your inventory and you'll receive the money
   - A new job will be generated to replace the completed one

5. **Abandoning a Job:**
   - If you decide you don't want to complete a job, use `/jobs abandon`
   - You'll be charged a penalty of 50% of the job's reward
   - You must confirm the abandonment with `/jobs abandon confirm`
   - The job will be removed and a new one will be generated

### Plot System

The mod includes a property ownership system with different types of plots:

1. **Plot Types:**

   - **Personal Plot:** Basic plot for personal use. Cost: $1000, Tax: $1/day
   - **Farm Plot:** Medium plot for farming purposes. Cost: $2000, Tax: $2/day
   - **Business Plot:** Premium plot for commercial uses. Cost: $3000, Tax: $3/day

2. **Buying Plots:**

   - Use `/plots buy <type>` to purchase a plot of the specified type
   - Example: `/plots buy farm` to buy a Farm plot
   - You must have enough money for the purchase price

3. **Viewing Your Plots:**

   - Use `/plots` to see all plots you own
   - The command shows the number of each plot type and the daily tax amount

4. **Selling Plots:**

   - Use `/plots sell <type>` to sell a plot
   - You'll receive a partial refund of the purchase price
   - Example: `/plots sell personal` to sell a Personal plot

5. **Tax System:**
   - Taxes are collected automatically once per day
   - You must have enough money to pay taxes or you'll be notified of tax debt
   - Admins can force tax collection with the `/tax` command (requires permission level 2+)

### Marketplace System

The mod includes a global marketplace for purchasing items directly from the server:

1. **Opening the Marketplace:**

   - Use `/market` to open the marketplace interface
   - Browse through available items and their prices

2. **Purchasing Items:**

   - Click on an item in the marketplace to view details
   - Confirm your purchase to buy the item
   - The item will be added to your inventory and the cost deducted from your balance

3. **Setting Up the Marketplace Chest (Admin Only):**
   - Place a chest where you want to access the marketplace
   - Use `/market setchest` to designate the chest as a marketplace access point
   - Players can now right-click the chest to open the marketplace

### Data Management System

The mod includes a comprehensive data management system:

1. **Viewing Data Status:**

   - Use `/data status` to see the current status of data management
   - View information about auto-saves, last save time, and available backups

2. **Manual Saving:**

   - Use `/data save` to force an immediate save of all data
   - Useful before making major changes or when testing

3. **Backup Management:**

   - Create backups: `/data backup create`
   - List available backups: `/data backup list`
   - Restore from a backup: `/data backup restore <name>`

4. **Diagnostic Tools:**
   - Use `/testdata` to run diagnostic tests on data storage
   - Options include checking paths, testing file operations, and checking permissions
   - Use `/modstatus` to view comprehensive status information about all mod components

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21
2. Place the rivers_economy-1.0.0.jar file in your mods folder
3. Start the game and enjoy!

## Requirements

- Minecraft 1.21
- Fabric Loader 0.15.7 or higher
- Fabric API 0.115.1+1.21.1 or higher

## Known Limitations

- Item matching is based on item name or registry ID, not specific attributes like damage or enchantments
- For multiplayer servers, item name matching might be affected by language settings
- Shop signs don't visually update to show their status (stocked/unstocked)

## License

This mod is available under the MIT License.

## Recent Changes

### Sign Shop System

We've implemented an automatic shop creation system that detects when a player finishes editing a sign with shop-related text:

1. When a player creates a sign with "[BUY]", "[SELL]", "[ADMINBUY]", or "[ADMINSELL]" on the first line, the system automatically processes this as a shop sign.
2. The system checks for permissions (for admin shops) and proper placement (connected to a chest for regular shops).
3. The sign text would ideally be colorized (green for buy signs, blue for sell signs) but currently this is just logged due to API limitations.

### Recent Fixes

1. **Removed unnecessary files:** Removed the empty `SignBlockEntityAccessor.java` file and its reference in the mixins.json file, which was causing bootstrap errors.

2. **Updated method injection point:** Fixed the `SignEditMixin` to target the correct method in Minecraft 1.21.1. Changed from targeting `finalizeText` to `tryChangeText`, which is the correct method called when a player finishes editing a sign in the current version.

3. **Simplified sign coloring approach:** Due to API limitations in Minecraft 1.21.1, sign text colorization is now handled with a simpler approach that logs the intent without causing compatibility issues.

### Known Issues and Limitations

- Sign text colorization is currently only logged and not actually applied, due to API limitations in Minecraft 1.21.1.

## Build Status

The mod now builds successfully with Gradle and launches properly in Minecraft 1.21.1.

## Future Improvements

1. Properly implement sign text colorization using the correct APIs for Minecraft 1.21.1.
2. Add comprehensive documentation for shop creation and usage.

## Compatibility

This mod is designed for Minecraft 1.21.1 with Fabric.
