package net.currencymod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.currencymod.jobs.MarketplaceHandler;
import net.currencymod.jobs.MarketplaceManager;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Command for accessing the marketplace where players can buy items from completed jobs.
 */
public class MarketCommand {
    
    /**
     * Register the market command.
     * 
     * @param dispatcher The command dispatcher
     * @param registryAccess The registry access
     * @param environment The registration environment
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
            literal("market")
                .executes(MarketCommand::execute)
                .then(literal("setchest")
                    .requires(source -> source.hasPermissionLevel(2)) // Operator only
                    .executes(MarketCommand::setChestAtPlayer)
                    .then(argument("pos", BlockPosArgumentType.blockPos())
                        .executes(MarketCommand::setChestAtPos)
                    )
                )
        );
    }
    
    /**
     * Execute the market command.
     * 
     * @param context The command context
     * @return The command result
     */
    private static int execute(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Check if the command was run by a player
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Open the marketplace GUI using our enhanced implementation
        MarketplaceManager.getInstance().openMarketplace(player);
        
        // Return success
        return 1;
    }
    
    /**
     * Set the marketplace chest at the player's position.
     * 
     * @param context The command context
     * @return The command result
     */
    private static int setChestAtPlayer(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Check if the command was run by a player
        if (!(source.getEntity() instanceof ServerPlayerEntity player)) {
            source.sendError(Text.literal("This command can only be executed by a player"));
            return 0;
        }
        
        // Use the block the player is standing on
        BlockPos pos = player.getBlockPos().down();
        
        // Set the marketplace chest position
        MarketplaceHandler.setMarketplaceChestPos(pos);
        
        // Send feedback
        source.sendFeedback(() -> Text.literal("Set marketplace chest position to ")
            .append(Text.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).formatted(Formatting.GREEN)), true);
        
        return 1;
    }
    
    /**
     * Set the marketplace chest at a specific position.
     * 
     * @param context The command context
     * @return The command result
     */
    private static int setChestAtPos(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // Get the position from the command
        BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");
        
        // Set the marketplace chest position
        MarketplaceHandler.setMarketplaceChestPos(pos);
        
        // Send feedback
        source.sendFeedback(() -> Text.literal("Set marketplace chest position to ")
            .append(Text.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ()).formatted(Formatting.GREEN)), true);
        
        return 1;
    }
} 