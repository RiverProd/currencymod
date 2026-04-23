package net.currencymod.ui;

import net.currencymod.jobs.Booster;
import net.currencymod.jobs.BoosterType;
import net.currencymod.jobs.JobManager;
import net.currencymod.jobs.PlayerBoosterData;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static net.currencymod.ui.ConfirmScreenHandler.makeItem;
import static net.currencymod.ui.ConfirmScreenHandler.makeFiller;

/**
 * 3-row booster selection GUI.
 * Slot layout (27 slots):
 *   Row 0 [0-8]:   all filler
 *   Row 1 [9-17]:  filler | BASIC(10) | filler | PREMIUM(13) | filler | ACTIVE_INFO(16) | filler...
 *   Row 2 [18-26]: all filler except BACK(22)
 */
public class BoosterScreenHandler extends GenericContainerScreenHandler {

    private static final int SLOT_BASIC   = 10;
    private static final int SLOT_PREMIUM = 13;
    private static final int SLOT_ACTIVE  = 16;
    private static final int SLOT_BACK    = 22;

    private final ServerPlayerEntity player;

    private BoosterScreenHandler(int syncId, PlayerInventory playerInv,
                                  SimpleInventory gui, ServerPlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X3, syncId, playerInv, gui, 3);
        this.player = player;
    }

    public static void open(ServerPlayerEntity player) {
        SimpleInventory gui = buildGui(player);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, p) -> new BoosterScreenHandler(syncId, inv, gui, player),
            Text.literal("Boosters")
        ));
    }

    private static SimpleInventory buildGui(ServerPlayerEntity player) {
        SimpleInventory gui = new SimpleInventory(27);
        ItemStack filler = makeFiller();
        for (int i = 0; i < 27; i++) gui.setStack(i, filler.copy());

        PlayerBoosterData bd = JobManager.getInstance().getPlayerBoosterData(player);

        gui.setStack(SLOT_BASIC,   makeBoosterItem(BoosterType.BASIC,    bd.getBoosterCount(BoosterType.BASIC)));
        gui.setStack(SLOT_PREMIUM, makeBoosterItem(BoosterType.PREMIUM,  bd.getBoosterCount(BoosterType.PREMIUM)));
        gui.setStack(SLOT_ACTIVE,  makeActiveInfo(bd));
        gui.setStack(SLOT_BACK,    makeItem(Items.RED_WOOL, "§c§lBack", List.of("§7Return to Jobs.")));

        return gui;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return;

        PlayerBoosterData bd = JobManager.getInstance().getPlayerBoosterData(sp);

        if (slotIndex == SLOT_BASIC) {
            if (bd.getBoosterCount(BoosterType.BASIC) <= 0) return;
            openBoosterConfirm(sp, BoosterType.BASIC, bd.getBoosterCount(BoosterType.BASIC));
            return;
        }
        if (slotIndex == SLOT_PREMIUM) {
            if (bd.getBoosterCount(BoosterType.PREMIUM) <= 0) return;
            openBoosterConfirm(sp, BoosterType.PREMIUM, bd.getBoosterCount(BoosterType.PREMIUM));
            return;
        }
        if (slotIndex == SLOT_BACK) {
            sp.closeHandledScreen();
            JobsScreenHandler.open(sp);
            return;
        }
        // Block all other interactions
    }

    private static void openBoosterConfirm(ServerPlayerEntity player, BoosterType type, int count) {
        PlayerBoosterData bd = JobManager.getInstance().getPlayerBoosterData(player);
        if (bd.hasActiveBooster()) {
            player.closeHandledScreen();
            player.sendMessage(Text.literal("§cYou already have an active booster! Wait for it to expire first."));
            JobsScreenHandler.open(player);
            return;
        }

        boolean isPremium = type == BoosterType.PREMIUM;
        String name = (isPremium ? "§6§l" : "§b§l") + type.getDisplayName();
        List<String> lore = new ArrayList<>();
        lore.add("§7+" + type.getRewardBonus() + "% reward bonus");
        if (type.getQuantityReduction() > 0) lore.add("§7-" + type.getQuantityReduction() + "% item requirements");
        lore.add("§7Duration: " + type.getDurationMinutes() + " minutes");
        lore.add("§7Owned: §f" + count);
        lore.add("");
        lore.add("§eClick §7to activate.");

        ConfirmScreenHandler.open(player,
            "Use " + type.getDisplayName() + "?",
            isPremium ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE,
            name, lore,
            () -> {
                PlayerBoosterData bd2 = JobManager.getInstance().getPlayerBoosterData(player);
                if (bd2.hasActiveBooster()) {
                    player.sendMessage(Text.literal("§cYou already have an active booster!"));
                } else if (!bd2.useBooster(type)) {
                    player.sendMessage(Text.literal("§cYou don't have any " + type.getDisplayName() + "s!"));
                } else {
                    JobManager.getInstance().save();
                    player.sendMessage(Text.literal("§aActivated §r" + type.getDisplayName()
                        + "§a! +" + type.getRewardBonus() + "% rewards for "
                        + type.getDurationMinutes() + " minutes."));
                }
                JobsScreenHandler.open(player);
            },
            () -> BoosterScreenHandler.open(player)
        );
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    private static ItemStack makeBoosterItem(BoosterType type, int count) {
        boolean isPremium = type == BoosterType.PREMIUM;
        ItemStack stack = new ItemStack(
            isPremium ? Items.ENCHANTED_GOLDEN_APPLE : Items.GOLDEN_APPLE,
            Math.max(1, Math.min(count, 64)));

        String name = (isPremium ? "§6§l" : "§b§l") + type.getDisplayName();
        List<String> lore = new ArrayList<>();
        lore.add("§7+" + type.getRewardBonus() + "% reward bonus");
        if (type.getQuantityReduction() > 0) lore.add("§7-" + type.getQuantityReduction() + "% item requirements");
        lore.add("§7Duration: " + type.getDurationMinutes() + " min");
        lore.add("§7Owned: §f" + count);
        lore.add(count > 0 ? "" : "");
        lore.add(count > 0 ? "§eClick to use." : "§8None owned.");

        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(name).styled(s -> s.withItalic(false)));
        stack.set(DataComponentTypes.LORE, new LoreComponent(
            lore.stream()
                .map(line -> (Text) Text.literal(line).styled(s -> s.withItalic(false)))
                .collect(Collectors.toList())));
        return stack;
    }

    private static ItemStack makeActiveInfo(PlayerBoosterData bd) {
        Booster active = bd.getActiveBooster();
        if (active == null) {
            return makeItem(Items.GRAY_DYE, "§7Active Booster", List.of("§8None active."));
        }
        BoosterType type = active.getType();
        boolean isPremium = type == BoosterType.PREMIUM;
        List<String> lore = new ArrayList<>();
        lore.add("§7+" + type.getRewardBonus() + "% reward bonus");
        if (type.getQuantityReduction() > 0) lore.add("§7-" + type.getQuantityReduction() + "% item requirements");
        lore.add("§7Time remaining: §f" + active.getFormattedRemainingTime());
        return makeItem(Items.CLOCK,
            (isPremium ? "§6" : "§b") + "§lActive: " + type.getDisplayName(), lore);
    }
}
