package net.currencymod.mixin;

import net.currencymod.shop.ShopManager;
import net.currencymod.shop.ShopSignHandler;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin to handle sign edit completion to automatically create shops
 */
@Mixin(SignBlockEntity.class)
public class SignEditMixin {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/SignEditMixin");

    /**
     * Inject at the method that is called when a player edits a sign
     * In Minecraft 1.21.1, the method is tryChangeText with a List<Text> parameter
     */
    @Inject(method = "tryChangeText", at = @At("RETURN"))
    private void onSignEditComplete(PlayerEntity player, boolean front, List<Text> messages, CallbackInfo ci) {
        // Only process on server side
        if (player.getWorld().isClient()) {
            return;
        }

        try {
            SignBlockEntity signEntity = (SignBlockEntity) (Object) this;
            ServerWorld world = (ServerWorld) player.getWorld();
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

            // Check if this sign could be a shop sign by checking the first row
            String firstLine = messages.get(0).getString().trim();
            String baseType = normalizeShopType(firstLine);
            boolean isShopCandidate = baseType.equalsIgnoreCase("[BUY]") || 
                                     baseType.equalsIgnoreCase("[SELL]") ||
                                     baseType.equalsIgnoreCase("[ADMINBUY]") || 
                                     baseType.equalsIgnoreCase("[ADMINSELL]");

            if (isShopCandidate) {
                LOGGER.info("Player {} completed editing a shop sign at {}", 
                    player.getName().getString(), signEntity.getPos());
                
                // Use the ShopSignHandler to process the sign edit
                ShopSignHandler.processNewShopSign(serverPlayer, world, signEntity);
            }
        } catch (Exception e) {
            LOGGER.error("Error processing sign edit completion: {}", e.getMessage(), e);
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
} 