package net.currencymod.ui;

import net.currencymod.jobs.*;
import net.currencymod.CurrencyMod;
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
 * Main Jobs GUI – 6-row chest (54 slots).
 *
 * Layout:
 *   Row 0 [0-8]:   LEVEL(0) ■ ■ STREAK(3) ■ ■ SKIPS(6) ■ BOOSTER(8)
 *   Row 1 [9-17]:  all filler (visual padding)
 *   Row 2 [18-26]: ■ J1(19) J2(20) J3(21) J4(22) J5(23) J6(24) J7(25) ■
 *   Row 3 [27-35]: ■ J8(28) J9(29) J10(30) J11(31) J12(32) J13(33) J14(34) ■
 *   Row 4 [36-44]: all filler (visual padding)
 *   Row 5 [45-53]: ■ COMPLETE(46) ■ ABANDON(48) ■ SKIP(50) ■ ■ CLOSE(53)
 */
public class JobsScreenHandler extends GenericContainerScreenHandler {

    // Row 0 – info bar
    private static final int SLOT_LEVEL   = 0;
    private static final int SLOT_STREAK  = 3;
    private static final int SLOT_SKIPS   = 6;
    private static final int SLOT_BOOSTER = 8;

    // Job slots (14 max across rows 2 and 3)
    private static final int[] JOB_SLOTS = {
        19, 20, 21,   // jobs 1-3
        22, 23, 24,   // jobs 4-6
        25,           // job 7
        28, 29, 30,   // jobs 8-10
        31, 32, 33,   // jobs 11-13
        34            // job 14
    };

    // Row 5 – action buttons
    private static final int SLOT_COMPLETE = 46;
    private static final int SLOT_ABANDON  = 48;
    private static final int SLOT_SKIP     = 50;
    private static final int SLOT_CLOSE    = 53;

    private final ServerPlayerEntity player;
    private final Job[] renderedJobs = new Job[14];

    private JobsScreenHandler(int syncId, PlayerInventory playerInv,
                               SimpleInventory gui, ServerPlayerEntity player,
                               Job[] renderedJobs) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, gui, 6);
        this.player = player;
        System.arraycopy(renderedJobs, 0, this.renderedJobs, 0, 14);
    }

    /** Opens (or re-opens) the Jobs GUI for the given player. */
    public static void open(ServerPlayerEntity player) {
        Job[] renderedJobs = new Job[14];
        SimpleInventory gui = buildGui(player, renderedJobs);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, p) -> new JobsScreenHandler(syncId, inv, gui, player, renderedJobs),
            Text.literal("Jobs")
        ));
    }

    // -------------------------------------------------------------------------
    // GUI builder
    // -------------------------------------------------------------------------

    private static SimpleInventory buildGui(ServerPlayerEntity player, Job[] renderedJobs) {
        SimpleInventory gui = new SimpleInventory(54);
        ItemStack filler = makeFiller();
        for (int i = 0; i < 54; i++) gui.setStack(i, filler.copy());

        JobManager        jm          = JobManager.getInstance();
        PlayerJobLevel    jobLevel    = jm.getPlayerJobLevel(player);
        PlayerJobStreak   jobStreak   = jm.getPlayerJobStreak(player);
        PlayerBoosterData boosterData = jm.getPlayerBoosterData(player);
        PlayerJobSkipData skipData    = jm.getPlayerSkipData(player);

        int[] bonuses       = JobManager.getBonusPercentages(jobLevel, jobStreak, boosterData);
        double totalMult    = 1.0 + bonuses[3] / 100.0;

        // Row 0: info bar
        gui.setStack(SLOT_LEVEL,   makeLevelItem(jobLevel, bonuses));
        gui.setStack(SLOT_STREAK,  makeStreakItem(jobStreak));
        gui.setStack(SLOT_SKIPS,   makeSkipsItem(skipData));
        gui.setStack(SLOT_BOOSTER, makeBoosterStatusItem(boosterData));

        // Job slots
        List<Job> jobs      = jm.getPlayerJobs(player);
        Job       activeJob = jm.getActiveJob(player);
        int       allowed   = jobLevel.getJobsAllowed();

        for (int i = 0; i < JOB_SLOTS.length; i++) {
            int guiSlot = JOB_SLOTS[i];
            if (i < jobs.size()) {
                Job job      = jobs.get(i);
                renderedJobs[i] = job;
                int adjQty   = JobManager.getAdjustedQuantity(job.getQuantity(), boosterData);
                int adjReward = (int) Math.ceil(job.getReward() * totalMult);
                boolean isActive = job.equals(activeJob);
                int haveCount = isActive ? jm.getItemCount(player, job.getItem()) : 0;
                gui.setStack(guiSlot, makeJobItem(job, adjQty, adjReward, isActive, haveCount, bonuses));
            } else if (i < allowed) {
                renderedJobs[i] = null;
            } else {
                gui.setStack(guiSlot, makeLockedSlot(unlockLevelForSlot(i)));
                renderedJobs[i] = null;
            }
        }

        // Row 5: action buttons
        boolean canComplete = activeJob != null &&
            jm.getItemCount(player, activeJob.getItem()) >=
            JobManager.getAdjustedQuantity(activeJob.getQuantity(), boosterData);

        gui.setStack(SLOT_COMPLETE, makeCompleteButton(activeJob != null, canComplete));
        gui.setStack(SLOT_ABANDON,  makeAbandonButton(activeJob != null));
        gui.setStack(SLOT_SKIP,     makeSkipButton(activeJob != null, skipData.getSkipCount()));
        gui.setStack(SLOT_CLOSE,    makeItem(Items.BARRIER, "§c§lClose", List.of("§7Close this menu.")));

        return gui;
    }

    // -------------------------------------------------------------------------
    // Click handling
    // -------------------------------------------------------------------------

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity sp)) return;

        if (slotIndex == SLOT_CLOSE)    { sp.closeHandledScreen(); return; }
        if (slotIndex == SLOT_BOOSTER)  { sp.closeHandledScreen(); BoosterScreenHandler.open(sp); return; }
        if (slotIndex == SLOT_COMPLETE) { handleComplete(sp); return; }
        if (slotIndex == SLOT_ABANDON)  { handleAbandon(sp); return; }
        if (slotIndex == SLOT_SKIP)     { handleSkip(sp); return; }

        for (int i = 0; i < JOB_SLOTS.length; i++) {
            if (slotIndex == JOB_SLOTS[i]) { handleJobClick(sp, i); return; }
        }
        // Block everything else
    }

    private void handleJobClick(ServerPlayerEntity sp, int jobIndex) {
        Job job = renderedJobs[jobIndex];
        if (job == null) return;

        JobManager jm     = JobManager.getInstance();
        Job        active = jm.getActiveJob(sp);

        if (job.equals(active)) return; // already active, use action buttons
        if (active != null) {
            sp.sendMessage(Text.literal("§cYou already have an active job. Abandon or finish it first."));
            return;
        }
        jm.activateJob(sp, job.getId().toString());
        sp.closeHandledScreen();
        open(sp);
    }

    private void handleComplete(ServerPlayerEntity sp) {
        JobManager jm     = JobManager.getInstance();
        Job        active = jm.getActiveJob(sp);
        if (active == null) { sp.sendMessage(Text.literal("§cNo active job.")); return; }

        PlayerBoosterData bd     = jm.getPlayerBoosterData(sp);
        int adjQty = JobManager.getAdjustedQuantity(active.getQuantity(), bd);
        if (jm.getItemCount(sp, active.getItem()) < adjQty) {
            sp.sendMessage(Text.literal("§cYou don't have enough items yet."));
            return;
        }
        PlayerJobLevel  jl = jm.getPlayerJobLevel(sp);
        PlayerJobStreak js = jm.getPlayerJobStreak(sp);
        int finalReward = (int) Math.ceil(active.getReward() * JobManager.getTotalMultiplier(jl, js, bd));
        String itemName = active.getItem().getName().getString();

        if (jm.completeJob(sp)) {
            sp.sendMessage(Text.literal("§a§lJob Completed! §r§7Collected " + adjQty + " " + itemName + " • Earned §a$" + finalReward));
            sp.closeHandledScreen();
            open(sp);
        } else {
            sp.sendMessage(Text.literal("§cFailed to complete job."));
        }
    }

    private void handleAbandon(ServerPlayerEntity sp) {
        JobManager jm     = JobManager.getInstance();
        Job        active = jm.getActiveJob(sp);
        if (active == null) { sp.sendMessage(Text.literal("§cNo active job to abandon.")); return; }

        PlayerJobLevel  jl = jm.getPlayerJobLevel(sp);
        PlayerJobStreak js = jm.getPlayerJobStreak(sp);
        int adjustedReward = (int) Math.ceil(active.getReward() * JobManager.getTotalMultiplier(jl, js));
        int penalty = active.isMegaJob() ? 0 : Math.max(1, (int)(adjustedReward * 0.75));

        List<String> lore = new ArrayList<>();
        lore.add("§7Collect " + active.getQuantity() + " " + active.getItem().getName().getString());
        lore.add(active.isMegaJob() ? "§aMEGA job — no penalty!" : "§cPenalty: §f$" + penalty);
        lore.add("");
        lore.add("§eConfirm to abandon and generate a new job.");

        sp.closeHandledScreen();
        ConfirmScreenHandler.open(sp, "Abandon Job?",
            active.getItem(),
            "§c§lAbandon: " + active.getItem().getName().getString(),
            lore,
            () -> { jm.abandonJob(sp); open(sp); },
            () -> open(sp));
    }

    private void handleSkip(ServerPlayerEntity sp) {
        JobManager        jm       = JobManager.getInstance();
        Job               active   = jm.getActiveJob(sp);
        PlayerJobSkipData skipData = jm.getPlayerSkipData(sp);

        if (active == null)              { sp.sendMessage(Text.literal("§cNo active job to skip.")); return; }
        if (skipData.getSkipCount() <= 0) { sp.sendMessage(Text.literal("§cNo skips available.")); return; }

        List<String> lore = new ArrayList<>();
        lore.add("§7Skip: Collect " + active.getQuantity() + " " + active.getItem().getName().getString());
        lore.add("§7Skips remaining: §f" + skipData.getSkipCount());
        lore.add("");
        lore.add("§eUsing 1 skip. A new job will be generated.");

        sp.closeHandledScreen();
        ConfirmScreenHandler.open(sp, "Skip Job?",
            Items.YELLOW_DYE,
            "§e§lSkip Current Job",
            lore,
            () -> { jm.useJobSkip(sp); open(sp); },
            () -> open(sp));
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }

    @Override
    public boolean canUse(PlayerEntity player) { return true; }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    private static ItemStack makeLevelItem(PlayerJobLevel jl, int[] bonuses) {
        int level    = jl.getLevel();
        int completed = jl.getCompletedJobs();
        int required  = jl.getRequiredJobsForNextLevel();
        boolean maxed = level >= PlayerJobLevel.MAX_LEVEL;

        List<String> lore = new ArrayList<>();
        lore.add("§7Level: §b§l" + level + "§r §7/ 30");
        lore.add(maxed ? "§a§lMax Level!" : "§7Progress: §f" + completed + " §7/ §f" + required + " jobs");
        lore.add("");
        if (bonuses[0] > 0) lore.add("§7Level bonus:   §a+" + bonuses[0] + "%");
        if (bonuses[1] > 0) lore.add("§7Streak bonus:  §b+" + bonuses[1] + "%");
        if (bonuses[2] > 0) lore.add("§7Booster bonus: §d+" + bonuses[2] + "%");
        if (bonuses[3] > 0) lore.add("§7Total bonus:   §a§l+" + bonuses[3] + "%");
        else                lore.add("§8No active bonuses.");

        return makeStackedItem(Items.EXPERIENCE_BOTTLE, Math.max(1, Math.min(level, 64)),
            "§b§lLevel " + level, lore);
    }

    private static ItemStack makeStreakItem(PlayerJobStreak streak) {
        int days     = streak.getStreakLength();
        int bonusPct = (int)((streak.getStreakBonus() - 1.0) * 100);

        List<String> lore = new ArrayList<>();
        if (days == 0) {
            lore.add("§8No active streak.");
            lore.add("§7Complete a job each Minecraft day");
            lore.add("§7to build your streak.");
        } else {
            lore.add("§7Streak: §d§l" + days + " day" + (days == 1 ? "" : "s"));
            if (bonusPct > 0) lore.add("§7Bonus: §a+" + bonusPct + "%");
        }
        return makeStackedItem(Items.AMETHYST_SHARD, Math.max(1, Math.min(days, 64)),
            "§d§lStreak", lore);
    }

    private static ItemStack makeSkipsItem(PlayerJobSkipData skipData) {
        int skips = skipData.getSkipCount();
        return makeStackedItem(Items.YELLOW_DYE, Math.max(1, Math.min(skips, 64)),
            "§e§lJob Skips",
            List.of("§7Available: §e§l" + skips,
                    "§7Use the Skip button below",
                    "§7to skip a job without penalty."));
    }

    private static ItemStack makeBoosterStatusItem(PlayerBoosterData bd) {
        Booster active = bd.getActiveBooster();
        if (active != null) {
            BoosterType type = active.getType();
            boolean isPrem = type == BoosterType.PREMIUM;
            List<String> lore = new ArrayList<>();
            lore.add("§7+" + type.getRewardBonus() + "% reward bonus");
            if (type.getQuantityReduction() > 0) lore.add("§7-" + type.getQuantityReduction() + "% items");
            lore.add("§7Remaining: §f" + active.getFormattedRemainingTime());
            lore.add("");
            lore.add("§eClick to manage boosters.");
            return makeItem(Items.CLOCK,
                (isPrem ? "§6" : "§b") + "§lActive: " + type.getDisplayName(), lore);
        }
        int basic   = bd.getBoosterCount(BoosterType.BASIC);
        int premium = bd.getBoosterCount(BoosterType.PREMIUM);
        return makeItem(Items.GOLDEN_APPLE, "§6§lBoosters",
            List.of("§7Premium: §f" + premium, "§7Basic: §f" + basic, "", "§eClick to manage boosters."));
    }

    private static ItemStack makeJobItem(Job job, int adjQty, int adjReward,
                                          boolean isActive, int haveCount, int[] bonuses) {
        ItemStack stack = new ItemStack(job.getItem());

        String prefix;
        if (job.isMegaJob() && isActive)  prefix = "§6§l[MEGA] §a§l[ACTIVE] ";
        else if (job.isMegaJob())          prefix = "§6§l[MEGA] ";
        else if (isActive)                 prefix = "§a§l[ACTIVE] ";
        else                               prefix = "§b";

        String displayName = prefix + job.getItem().getName().getString();

        List<String> lore = new ArrayList<>();
        lore.add("§7Collect: §f" + adjQty + " " + job.getItem().getName().getString());
        lore.add("§7Reward:  §a$" + adjReward);
        if (bonuses[3] > 0) lore.add("§7Bonus:   §a+" + bonuses[3] + "%");
        if (isActive) {
            lore.add("");
            lore.add("§7Progress: §f" + haveCount + " §7/ §f" + adjQty
                + " §7(" + (adjQty > 0 ? haveCount * 100 / adjQty : 0) + "%)");
        } else {
            lore.add("");
            lore.add("§eClick to activate.");
        }

        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(displayName).styled(s -> s.withItalic(false)));
        stack.set(DataComponentTypes.LORE, new LoreComponent(
            lore.stream()
                .map(line -> (Text) Text.literal(line).styled(s -> s.withItalic(false)))
                .collect(Collectors.toList())));

        // Enchant glow to highlight active job
        if (isActive) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }

        return stack;
    }

    private static ItemStack makeLockedSlot(int unlockLevel) {
        return makeItem(Items.BLACK_STAINED_GLASS_PANE,
            "§8§lLocked",
            List.of("§7Reach §bLevel " + unlockLevel + "§7 to unlock."));
    }

    private static ItemStack makeCompleteButton(boolean hasActive, boolean canComplete) {
        if (!hasActive)   return makeItem(Items.GRAY_DYE, "§7Complete Job", List.of("§8No active job."));
        if (canComplete)  return makeItem(Items.LIME_DYE, "§a§lComplete Job",
            List.of("§7You have all the required items!", "§eClick to collect your reward."));
        return makeItem(Items.LIGHT_BLUE_DYE, "§b§lComplete Job",
            List.of("§7Job in progress...", "§7Collect all required items first."));
    }

    private static ItemStack makeAbandonButton(boolean hasActive) {
        if (!hasActive) return makeItem(Items.GRAY_DYE, "§7Abandon Job", List.of("§8No active job."));
        return makeItem(Items.RED_DYE, "§c§lAbandon Job",
            List.of("§7Give up the current job.", "§cA penalty will be charged.", "§eClick to confirm."));
    }

    private static ItemStack makeSkipButton(boolean hasActive, int skipsLeft) {
        if (!hasActive)    return makeItem(Items.GRAY_DYE, "§7Skip Job", List.of("§8No active job."));
        if (skipsLeft <= 0) return makeItem(Items.GRAY_DYE, "§7Skip Job",
            List.of("§8No skips available.", "§7Level up to earn more skips."));
        return makeItem(Items.YELLOW_DYE, "§e§lSkip Job",
            List.of("§7Skips available: §e" + skipsLeft, "§7Skip without penalty.", "§eClick to confirm."));
    }

    private static ItemStack makeStackedItem(net.minecraft.item.Item item, int count,
        String name, List<String> lore) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponentTypes.CUSTOM_NAME,
            Text.literal(name).styled(s -> s.withItalic(false)));
        if (!lore.isEmpty()) {
            stack.set(DataComponentTypes.LORE, new LoreComponent(
                lore.stream()
                    .map(line -> (Text) Text.literal(line).styled(s -> s.withItalic(false)))
                    .collect(Collectors.toList())));
        }
        return stack;
    }

    private static int unlockLevelForSlot(int slotIndex) {
        if (slotIndex < 5)  return 0;   // slots 0-4: level 0 (5 jobs)
        if (slotIndex < 6)  return 5;   // slot 5: level 5 (6 jobs)
        if (slotIndex < 7)  return 10;  // slot 6: level 10 (7 jobs)
        if (slotIndex < 8)  return 15;  // slot 7: level 15 (8 jobs)
        if (slotIndex < 10) return 20;  // slots 8-9: level 20 (10 jobs)
        if (slotIndex < 12) return 25;  // slots 10-11: level 25 (12 jobs)
        return 30;                     // slots 12-13: level 30 (14 jobs)
    }
}
