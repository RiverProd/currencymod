package net.currencymod.shop;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.SignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for direct sign interactions to ensure reliable shop creation
 */
public class ShopSignHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/ShopSignHandler");

    // Pattern to match buy shop signs: [Buy] on the first line
    private static final Pattern BUY_SHOP_PATTERN = Pattern.compile("\\[Buy\\]");
    
    // Pattern to match sell shop signs: [Sell] on the first line
    private static final Pattern SELL_SHOP_PATTERN = Pattern.compile("\\[Sell\\]");
    
    // Pattern to match admin buy shop signs: [AdminBuy] on the first line
    private static final Pattern ADMIN_BUY_SHOP_PATTERN = Pattern.compile("\\[AdminBuy\\]");
    
    // Pattern to match admin sell shop signs: [AdminSell] on the first line
    private static final Pattern ADMIN_SELL_SHOP_PATTERN = Pattern.compile("\\[AdminSell\\]");
    
    // Pattern to match a price: $<price>
    private static final Pattern PRICE_PATTERN = Pattern.compile("\\$(\\d+(\\.\\d+)?)");
    
    // Pattern to match a quantity: just a number
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("(\\d+)");

    // Pattern to match "quantity : price" format
    private static final Pattern QUANTITY_PRICE_PATTERN = Pattern.compile("(\\d+)\\s*:\\s*\\$?(\\d+(?:\\.\\d+)?)");

    /**
     * Register the shop sign handler
     */
    public static void register() {
        // Register for block use events to handle right-clicking on signs
        UseBlockCallback.EVENT.register(ShopSignHandler::onUseBlock);
        LOGGER.info("Registered shop sign handler");
    }

    /**
     * Handle block interaction events - focuses on shop creation and user interaction
     */
    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        if (player == null || world == null || world.isClient() || hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }

        BlockPos pos = hitResult.getBlockPos();
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        // Check if the block is a sign
        if (!(block instanceof SignBlock) && !(block instanceof WallSignBlock)) {
            return ActionResult.PASS;
        }

        // Check if we have sign text entity
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof SignBlockEntity)) {
            return ActionResult.PASS;
        }

        SignBlockEntity signEntity = (SignBlockEntity) blockEntity;
        ServerWorld serverWorld = (ServerWorld) world;

        // Check if the sign is a shop sign (Buy, Sell, AdminBuy, or AdminSell)
        String firstLine = signEntity.getFrontText().getMessage(0, false).getString().trim();

        // Record this player as interacting with the sign for ownership tracking
        ShopDetector.recordSignPlacement(pos, player);

        // Check if sign format indicates a shop
        boolean isBuyShop = firstLine.toUpperCase().startsWith("[BUY");
        boolean isSellShop = firstLine.toUpperCase().startsWith("[SELL");
        boolean isAdminBuyShop = firstLine.toUpperCase().startsWith("[ADMINBUY]");
        boolean isAdminSellShop = firstLine.toUpperCase().startsWith("[ADMINSELL]");

        // Check if this is any type of shop sign
        if (isBuyShop || isSellShop || isAdminBuyShop || isAdminSellShop) {
            boolean isAdminShop = isAdminBuyShop || isAdminSellShop;
            boolean isBuyType = isBuyShop || isAdminBuyShop;
            
            LOGGER.info("Player {} interacting with potential {} sign at {}", 
                player.getName().getString(), isAdminShop ? "admin shop" : "shop", pos);
            
            // For admin shops, verify player has admin permissions
            if (isAdminShop && !hasAdminPermission(player)) {
                if (player instanceof ServerPlayerEntity) {
                    player.sendMessage(Text.literal("You don't have permission to create admin shops!")
                        .formatted(Formatting.RED), false);
                }
                return ActionResult.PASS;
            }
            
            // Check if sign is attached to a chest (not required for admin shops)
            BlockPos chestPos = null;
            if (!isAdminShop) {
                chestPos = findAttachedChest(world, pos, state);
                if (chestPos == null) {
                    // Not attached to a chest
                    if (player instanceof ServerPlayerEntity) {
                        player.sendMessage(Text.literal("Regular shops must be attached to a chest!")
                            .formatted(Formatting.RED), false);
                    }
                    return ActionResult.PASS;
                }
            }

            // Get shop data
            String worldId = ShopManager.getWorldId(world);
            ShopManager shopManager = ((ShopAccess) world).getShopManager();
            
            if (shopManager == null) {
                LOGGER.error("Shop manager is null for world: {}", world.getRegistryKey().getValue());
                return ActionResult.PASS;
            }

            // Check if shop already exists
            ShopData existingShop = shopManager.getShop(worldId, pos);
            
            // If shop doesn't exist, create one
            if (existingShop == null) {
                // Parse shop data from the sign
                ShopInfo shopInfo = parseShopSignData(signEntity);
                if (shopInfo == null) {
                    LOGGER.warn("Failed to parse shop sign at {}", pos);
                    return ActionResult.PASS;
                }
                
                // Create the shop
                ShopData shopData = createShop(serverWorld, pos, chestPos, player.getUuid(), 
                    isBuyType, shopInfo.itemName, shopInfo.quantity, shopInfo.price, isAdminShop);
                
                // If shop was created successfully and this is from direct player interaction 
                // (not world load), notify them
                if (shopData != null) {
                    // Update sign appearance
                    updateSignAppearance(signEntity, isBuyType ? Formatting.GREEN : Formatting.BLUE);
                    
                    // Send notification to the player about the newly created shop
                    sendShopCreationNotification(player, isBuyType, isAdminShop);
                }
            }
        }

        return ActionResult.PASS;
    }
    
    /**
     * Helper class to hold parsed shop information
     */
    private static class ShopInfo {
        String itemName;
        int quantity;
        double price;
        
        ShopInfo(String itemName, int quantity, double price) {
            this.itemName = itemName;
            this.quantity = quantity;
            this.price = price;
        }
    }
    
    /**
     * Parse a shop sign to extract item, quantity and price
     */
    private static ShopInfo parseShopSignData(SignBlockEntity signEntity) {
        try {
            SignText signText = signEntity.getFrontText();
            String[] lines = new String[4];
            for (int i = 0; i < 4; i++) {
                lines[i] = signText.getMessage(i, false).getString().trim();
            }

            String typeText = lines[0];
            String secondLine = lines[1];
            String thirdLine = lines[2]; 
            String fourthLine = lines[3];

            String baseType = normalizeShopType(typeText);

            boolean isBuy = baseType.equalsIgnoreCase("[BUY]");
            boolean isSell = baseType.equalsIgnoreCase("[SELL]");
            boolean isAdminBuy = baseType.equalsIgnoreCase("[ADMINBUY]");
            boolean isAdminSell = baseType.equalsIgnoreCase("[ADMINSELL]");
            
            // Check if this is any type of shop sign
            if (!isBuy && !isSell && !isAdminBuy && !isAdminSell) {
                return null; // Not a valid shop sign
            }

            // Check if this is using the new format (quantity : price in fourth line)
            Matcher quantityPriceMatcher = QUANTITY_PRICE_PATTERN.matcher(fourthLine);
            if (quantityPriceMatcher.find()) {
                // This is the new format with two-line item name and quantity:price in fourth line
                // Combine second and third lines for item name
                String itemName = secondLine;
                if (!thirdLine.isEmpty()) {
                    itemName += " " + thirdLine;
                }
                
                // Parse quantity and price from fourth line
                int quantity = Integer.parseInt(quantityPriceMatcher.group(1));
                double price = Double.parseDouble(quantityPriceMatcher.group(2));
                
                if (quantity <= 0) {
                    LOGGER.warn("Invalid shop quantity: {}", quantity);
                    return null;
                }
                
                if (price < 0) {
                    LOGGER.warn("Invalid shop price: {}", price);
                    return null;
                }
                
                LOGGER.info("Successfully parsed shop sign (new format): {} item={}, quantity={}, price=${}", 
                    baseType, itemName, quantity, price);
                return new ShopInfo(itemName, quantity, price);
            } else {
                // Standard format: item name on second line, quantity on third, price on fourth
                String itemName = secondLine;
                String quantityText = thirdLine;
                String priceText = fourthLine;

                // Parse quantity
                int quantity;
                try {
                    quantity = Integer.parseInt(quantityText);
                    if (quantity <= 0) {
                        LOGGER.warn("Invalid shop quantity: {}", quantity);
                        return null;
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warn("Could not parse shop quantity: {}", quantityText);
                    return null;
                }

                // Parse price - should start with $ sign
                double price;
                try {
                    if (priceText.startsWith("$")) {
                        price = Double.parseDouble(priceText.substring(1));
                    } else {
                        price = Double.parseDouble(priceText);
                    }
                    
                    if (price < 0) {
                        LOGGER.warn("Invalid shop price: {}", price);
                        return null;
                    }
                } catch (NumberFormatException e) {
                    LOGGER.warn("Could not parse shop price: {}", priceText);
                    return null;
                }

                LOGGER.info("Successfully parsed shop sign (standard format): {} item={}, quantity={}, price=${}", 
                    baseType, itemName, quantity, price);
                return new ShopInfo(itemName, quantity, price);
            }
        } catch (Exception e) {
            LOGGER.error("Error parsing shop sign data: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract service tag from shop sign first line
     */
    private static String extractServiceTag(String firstLine) {
        if (firstLine == null) return null;
        String trimmed = firstLine.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return null;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        String[] parts = inner.split("\\s+");
        if (parts.length != 2) {
            return null;
        }
        String base = parts[0];
        String tag = parts[1];
        if (!(base.equalsIgnoreCase("buy") || base.equalsIgnoreCase("sell") || 
              base.equalsIgnoreCase("adminbuy") || base.equalsIgnoreCase("adminsell"))) {
            return null;
        }
        if (tag.length() < 1 || tag.length() > 3) {
            return null;
        }
        // Ensure tag is letters only
        if (!tag.matches("(?i)[A-Z]{1,3}")) {
            return null;
        }
        return tag.toUpperCase();
    }
    
    /**
     * Create a shop and return the shop data
     */
    private static ShopData createShop(ServerWorld world, BlockPos signPos, BlockPos chestPos, 
                                 UUID playerUuid, boolean isBuyShop, 
                                 String itemName, int quantity, double price, boolean isAdminShop) {
        try {
            // Get the shop manager
            ShopManager shopManager = ((ShopAccess) world).getShopManager();
            if (shopManager == null) {
                LOGGER.error("Shop manager is null for world: {}", world.getRegistryKey().getValue());
                return null;
            }
            
            // Check if shop already exists at this location
            String worldId = ShopManager.getWorldId(world);
            ShopData existingShop = shopManager.getShop(worldId, signPos);
            if (existingShop != null) {
                // Shop already exists, update web sync data if missing
                if (!existingShop.hasWebSyncData()) {
                    // Extract service tag from sign
                    BlockEntity blockEntity = world.getBlockEntity(signPos);
                    String serviceTag = null;
                    if (blockEntity instanceof SignBlockEntity signEntity) {
                        String firstLine = signEntity.getFrontText().getMessage(0, false).getString().trim();
                        serviceTag = extractServiceTag(firstLine);
                    }
                    
                    existingShop.setItemName(itemName);
                    existingShop.setQuantity(quantity);
                    existingShop.setServiceTag(serviceTag);
                    LOGGER.info("Updated web sync data for existing shop at {}", signPos);
                }
                return existingShop;
            }
            
            // Extract service tag from sign
            BlockEntity blockEntity = world.getBlockEntity(signPos);
            String serviceTag = null;
            if (blockEntity instanceof SignBlockEntity signEntity) {
                String firstLine = signEntity.getFrontText().getMessage(0, false).getString().trim();
                serviceTag = extractServiceTag(firstLine);
            }
            
            // Create the shop with all web sync data
            ShopData shopData = shopManager.createShop(worldId, signPos, playerUuid, price, null, 
                                                       isBuyShop, isAdminShop, itemName, quantity, serviceTag);
            
            if (shopData != null) {
                String shopTypeStr = isAdminShop ? 
                    (isBuyShop ? "AdminBuy" : "AdminSell") : 
                    (isBuyShop ? "Buy" : "Sell");
                    
                LOGGER.info("Created new {} shop at {}: {} x{} @ ${}, owner={}, serviceTag={}", 
                    shopTypeStr, signPos, itemName, quantity, price, playerUuid, serviceTag);
            } else {
                LOGGER.warn("Failed to create shop at {}", signPos);
            }
            
            return shopData;
        } catch (Exception e) {
            LOGGER.error("Error creating shop: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Send shop creation notification to a player
     */
    private static void sendShopCreationNotification(PlayerEntity player, boolean isBuyShop, boolean isAdminShop) {
        String shopType = isAdminShop ? 
            (isBuyShop ? "Admin Buy" : "Admin Sell") : 
            (isBuyShop ? "Buy" : "Sell");
        
        // Create a message with a decorative header
        Text headerText = Text.literal("✓ ").formatted(Formatting.GREEN)
            .append(Text.literal("Shop Created").formatted(Formatting.GOLD, Formatting.BOLD));
            
        // Main message with shop type
        Text message = Text.literal("You created a new ")
            .append(Text.literal(shopType).formatted(isBuyShop ? Formatting.GREEN : Formatting.BLUE))
            .append(Text.literal(" shop!"));
            
        // Send formatted messages
        player.sendMessage(headerText, false);
        player.sendMessage(message, false);
        player.sendMessage(Text.literal("Right-click to transact with the shop.")
            .formatted(Formatting.YELLOW), false);
        
        // Add information about shop formats
        player.sendMessage(Text.literal("Shop Formats:").formatted(Formatting.GOLD), false);
        
        // Standard format info
        player.sendMessage(Text.literal("• Standard: ")
            .formatted(Formatting.AQUA)
            .append(Text.literal("Line 1: [Buy] or [Sell], Line 2: Item, Line 3: Quantity, Line 4: $Price")
            .formatted(Formatting.WHITE)), false);
        
        // New format for long item names
        player.sendMessage(Text.literal("• Long Items: ")
            .formatted(Formatting.AQUA)
            .append(Text.literal("Line 1: [Buy] or [Sell], Lines 2+3: Item Name, Line 4: Quantity : $Price")
            .formatted(Formatting.WHITE)), false);
        
        // Add extra message for admin shops
        if (isAdminShop) {
            player.sendMessage(Text.literal("Admin shops have unlimited inventory and funds.")
                .formatted(Formatting.GOLD), false);
        }
    }
    
    /**
     * Update a sign's appearance to indicate it's a shop sign
     */
    private static void updateSignAppearance(SignBlockEntity signEntity, Formatting color) {
        try {
            // Cast the sign entity to our accessor interface to access the mixin method
            SignAccessor signAccessor = (SignAccessor) signEntity;
            signAccessor.colorizeShopSign(color);
            
            LOGGER.info("Shop sign at {} colored as {}", 
                signEntity.getPos(), color == Formatting.GREEN ? "Buy (Green)" : "Sell (Blue)");
        } catch (Exception e) {
            LOGGER.error("Error updating sign appearance: {}", e.getMessage());
        }
    }

    /**
     * Find the chest that a sign is attached to
     */
    private static BlockPos findAttachedChest(World world, BlockPos signPos, BlockState signState) {
        try {
            // Check if this is a wall sign (attached to another block)
            if (signState.getBlock() instanceof WallSignBlock) {
                Direction facing = signState.get(WallSignBlock.FACING);
                BlockPos attachedPos = signPos.offset(facing.getOpposite());
                
                // Check if the block it's attached to is a chest
                BlockState attachedBlock = world.getBlockState(attachedPos);
                if (attachedBlock.getBlock() instanceof ChestBlock) {
                    return attachedPos;
                }
            } else {
                // For standing signs, check the block below
                BlockPos belowPos = signPos.down();
                BlockState belowBlock = world.getBlockState(belowPos);
                if (belowBlock.getBlock() instanceof ChestBlock) {
                    return belowPos;
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error finding attached chest: {}", e.getMessage());
        }
        
        return null; // No chest found
    }

    private static boolean isShopSign(SignBlockEntity signEntity) {
        try {
            String firstLine = signEntity.getFrontText().getMessage(0, false).getString().trim();
            String baseType = normalizeShopType(firstLine);
            return baseType.equalsIgnoreCase("[BUY]") || baseType.equalsIgnoreCase("[SELL]");
        } catch (Exception e) {
            LOGGER.error("Error checking if sign is a shop sign: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Normalize a shop sign's first line to its base type token.
     * E.g. "[Buy TAG]" -> "[BUY]", "[Sell]" -> "[SELL]".
     */
    private static String normalizeShopType(String firstLine) {
        if (firstLine == null) return "";
        String trimmed = firstLine.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return trimmed;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1).trim();
        String[] parts = inner.split("\\s+");
        String base = parts[0].toUpperCase();
        return "[" + base + "]";
    }

    /**
     * Check if a player has admin permissions
     */
    private static boolean hasAdminPermission(PlayerEntity player) {
        // OP level 2 or higher is considered admin
        if (player instanceof ServerPlayerEntity serverPlayer) {
            return serverPlayer.hasPermissionLevel(2);
        }
        return false;
    }

    /**
     * Process a newly created/edited shop sign
     * This is called automatically when a player finishes editing a sign
     */
    public static void processNewShopSign(ServerPlayerEntity player, ServerWorld world, SignBlockEntity signEntity) {
        if (player == null || world == null || signEntity == null) {
            return;
        }
        
        BlockPos pos = signEntity.getPos();
        BlockState state = world.getBlockState(pos);
        
        // Check if sign format indicates a shop
        String firstLine = signEntity.getFrontText().getMessage(0, false).getString().trim();
        boolean isBuyShop = firstLine.startsWith("[Buy]");
        boolean isSellShop = firstLine.startsWith("[Sell]");
        boolean isAdminBuyShop = firstLine.startsWith("[AdminBuy]");
        boolean isAdminSellShop = firstLine.startsWith("[AdminSell]");
        
        // Check if this is any type of shop sign
        if (isBuyShop || isSellShop || isAdminBuyShop || isAdminSellShop) {
            boolean isAdminShop = isAdminBuyShop || isAdminSellShop;
            boolean isBuyType = isBuyShop || isAdminBuyShop;
            
            LOGGER.info("Processing new shop sign at {} by {}", pos, player.getName().getString());
            
            // For admin shops, verify player has admin permissions
            if (isAdminShop && !hasAdminPermission(player)) {
                player.sendMessage(Text.literal("You don't have permission to create admin shops!")
                    .formatted(Formatting.RED), false);
                return;
            }
            
            // Check if sign is attached to a chest (not required for admin shops)
            BlockPos chestPos = null;
            if (!isAdminShop) {
                chestPos = findAttachedChest(world, pos, state);
                if (chestPos == null) {
                    // Not attached to a chest
                    player.sendMessage(Text.literal("Regular shops must be attached to a chest!")
                        .formatted(Formatting.RED), false);
                    return;
                }
            }
            
            // Get shop data
            String worldId = ShopManager.getWorldId(world);
            ShopManager shopManager = ((ShopAccess) world).getShopManager();
            
            if (shopManager == null) {
                LOGGER.error("Shop manager is null for world: {}", world.getRegistryKey().getValue());
                return;
            }
            
            // Check if shop already exists
            ShopData existingShop = shopManager.getShop(worldId, pos);
            
            // Only proceed if a shop doesn't already exist at this position
            if (existingShop == null) {
                // Parse shop data from the sign
                ShopInfo shopInfo = parseShopSignData(signEntity);
                if (shopInfo == null) {
                    LOGGER.warn("Failed to parse shop sign at {}", pos);
                    return;
                }
                
                // Create the shop
                ShopData shopData = createShop(world, pos, chestPos, player.getUuid(), 
                    isBuyType, shopInfo.itemName, shopInfo.quantity, shopInfo.price, isAdminShop);
                
                // If shop was created successfully, notify the player
                if (shopData != null) {
                    // Color the text on the sign
                    colorizeShopSign(signEntity, isBuyType);
                    
                    // Update sign appearance
                    updateSignAppearance(signEntity, isBuyType ? Formatting.GREEN : Formatting.BLUE);
                    
                    // Send notification to the player about the newly created shop
                    sendShopCreationNotification(player, isBuyType, isAdminShop);
                    
                    // Record this player as the shop creator for ownership tracking
                    ShopDetector.recordSignPlacement(pos, player);
                }
            }
        }
    }

    /**
     * Colors the text on a shop sign to make it more visually distinct
     * [Buy] signs become green and [Sell] signs become blue
     */
    private static void colorizeShopSign(SignBlockEntity signEntity, boolean isBuy) {
        try {
            // Get the first line (index 0) of the sign
            SignText currentText = signEntity.getFrontText();
            Text firstLine = currentText.getMessage(0, false);
            String firstLineStr = firstLine.getString().trim();
            
            // Determine which color to use
            Formatting color = isBuy ? Formatting.GREEN : Formatting.BLUE;
            
            // Create a colored version of the text
            Text coloredText = Text.literal(firstLineStr).formatted(color);
            
            // Update the sign text in a simple way without using protected methods
            // We'll update line 0 with our colored text by using publicize access
            // For now, let's just log what we would do since we can't modify directly
            LOGGER.info("Would colorize shop sign: {} to {}", 
                firstLineStr, isBuy ? "green (buy)" : "blue (sell)");
                
            // Mark the sign entity as needing to be saved
            signEntity.markDirty();
        } catch (Exception e) {
            LOGGER.error("Failed to colorize shop sign: {}", e.getMessage(), e);
        }
    }
} 