package net.currencymod.jobs;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.currencymod.CurrencyMod;
import net.currencymod.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Random;
import java.util.UUID;

/**
 * Represents a job that a player can complete.
 */
public class Job {
    private static final Logger LOGGER = LoggerFactory.getLogger("CurrencyMod/Job");
    private static final Random RANDOM = new Random();
    
    // 2% chance for a job to be a Mega job
    private static final double MEGA_JOB_CHANCE = 0.02;
    
    private final UUID id;
    private final String itemId;
    private final int quantity;
    private final int reward;
    private boolean active;
    // Flag to indicate if this is a Mega job
    private final boolean isMegaJob;
    
    /**
     * Creates a new job.
     *
     * @param id       The unique ID of the job
     * @param itemId   The item ID
     * @param quantity The quantity
     * @param reward   The reward
     * @param isMegaJob Whether this is a Mega job requiring more items for higher reward
     */
    public Job(UUID id, String itemId, int quantity, int reward, boolean isMegaJob) {
        this.id = id;
        this.itemId = itemId;
        this.quantity = quantity;
        this.reward = reward;
        this.active = false;
        this.isMegaJob = isMegaJob;
    }
    
    /**
     * Creates a job from a template.
     *
     * @param template The template
     * @return The job
     */
    public static Job fromTemplate(JobConfig.JobTemplate template) {
        // Determine if this will be a Mega job (2% chance)
        boolean isMegaJob = RANDOM.nextDouble() < MEGA_JOB_CHANCE;
        
        // Apply variations to quantity and reward
        int quantity = applyVariation(template.baseQuantity, template.quantityVariation);
        int reward = applyVariation(template.baseReward, template.rewardVariation);
        
        // Ensure minimum values
        quantity = Math.max(1, quantity);
        reward = Math.max(1, reward);
        
        // For Mega jobs, multiply quantity by 10 and increase reward by 50%
        if (isMegaJob) {
            quantity *= 10;
            reward = (int)(reward * 15);
            LOGGER.info("Created a Mega job requiring {} x {} with reward ${}", 
                       quantity, template.itemId, reward);
        }
        
        // Apply the 0.8 reward reduction
        reward = (int)(reward * 0.8);
        
        // Apply config multipliers (affects all jobs individually)
        ModConfig config = ModConfig.getInstance();
        double quantityMultiplier = config.getJobQuantityMultiplier();
        double payoutMultiplier = config.getJobPayoutMultiplier();
        
        // Apply quantity multiplier
        quantity = Math.max(1, (int)Math.round(quantity * quantityMultiplier));
        
        // Apply payout multiplier: reward scales with quantity multiplier to maintain payout per item,
        // then payout multiplier is applied on top
        // Example: 64 cobblestone for $10, quantity=0.5, payout=1.0 -> 32 cobblestone for $5
        reward = Math.max(1, (int)Math.round(reward * quantityMultiplier * payoutMultiplier));
        
        return new Job(UUID.randomUUID(), template.itemId, quantity, reward, isMegaJob);
    }
    
    /**
     * Applies variation to a base value.
     *
     * @param baseValue The base value
     * @param variation The variation percentage (0-100)
     * @return The value with variation applied
     */
    private static int applyVariation(int baseValue, int variation) {
        if (variation <= 0) {
            return baseValue;
        }
        
        // Calculate the range of variation
        double variationRange = baseValue * (variation / 100.0);
        
        // Generate a random value within the range (-variationRange to +variationRange)
        double randomVariation = (RANDOM.nextDouble() * 2 - 1) * variationRange;
        
        // Apply the variation to the base value and round to the nearest integer
        return (int) Math.round(baseValue + randomVariation);
    }
    
    /**
     * Gets the item for this job.
     *
     * @return The item
     */
    public Item getItem() {
        // Parse the item ID, handling both formats: "minecraft:diamond" and "diamond"
        Identifier itemIdentifier;
        if (itemId.contains(":")) {
            // Already has namespace, use directly
            itemIdentifier = Identifier.of(itemId);
        } else {
            // Add minecraft namespace
            itemIdentifier = Identifier.of("minecraft", itemId);
        }
        return Registries.ITEM.get(itemIdentifier);
    }
    
    /**
     * Gets the item ID for this job.
     *
     * @return The item ID
     */
    public String getItemId() {
        return itemId;
    }
    
    /**
     * Gets the quantity for this job.
     *
     * @return The quantity
     */
    public int getQuantity() {
        return quantity;
    }
    
    /**
     * Gets the reward for this job.
     *
     * @return The reward
     */
    public int getReward() {
        return reward;
    }
    
    /**
     * Gets the unique ID of this job.
     *
     * @return The unique ID
     */
    public UUID getId() {
        return id;
    }
    
    /**
     * Checks if this job is active.
     *
     * @return True if active, false otherwise
     */
    public boolean isActive() {
        LOGGER.debug("Checking if job {} is active: {}", id, active);
        return active;
    }
    
    /**
     * Checks if this is a Mega job.
     *
     * @return True if this is a Mega job, false otherwise
     */
    public boolean isMegaJob() {
        return isMegaJob;
    }
    
    /**
     * Sets whether this job is active.
     *
     * @param active True if active, false otherwise
     */
    public void setActive(boolean active) {
        LOGGER.debug("Setting job {} active state to: {}", id, active);
        this.active = active;
    }
    
    /**
     * Gets the display text for this job.
     *
     * @param includeStatus Whether to include the status
     * @return The display text
     */
    public Text getDisplayText(boolean includeStatus) {
        Item item = getItem();
        MutableText text = Text.empty();
        
        // Add status if requested
        if (includeStatus) {
            if (isActive()) {
                text = text.append(Text.literal("【ACTIVE】 ").styled(style -> style.withColor(Formatting.GREEN).withBold(true)));
            } else {
                text = text.append(Text.literal("【JOB】 ").styled(style -> style.withColor(Formatting.AQUA)));
            }
        }
        
        // If this is a mega job, add a special prefix
        if (isMegaJob) {
            text = text.append(Text.literal("【MEGA】 ").styled(style -> style.withColor(Formatting.GOLD).withBold(true)));
        }
        
        // Format item name correctly with proper capitalization
        String itemName = item.getName().getString();
        
        // Add job details with better formatting
        MutableText jobText = Text.literal("Collect ")
                .append(Text.literal(String.valueOf(quantity)).styled(style -> style.withColor(Formatting.YELLOW).withBold(true)))
                .append(" ")
                .append(Text.literal(itemName).styled(style -> style.withColor(Formatting.AQUA).withBold(true)))
                .append(Text.literal(" • Reward: ").styled(style -> style.withColor(Formatting.GRAY)))
                .append(Text.literal("$" + reward).styled(style -> style.withColor(Formatting.GREEN).withBold(true)));
        
        return text.append(jobText);
    }
    
    /**
     * Create clickable text that activates the job when clicked
     * 
     * @param command Command prefix
     * @return Clickable text
     */
    public MutableText createClickableText(String command) {
        MutableText text = Text.empty();
        
        // Add MEGA prefix if it's a mega job
        if (isMegaJob) {
            text = text.append(Text.literal("[MEGA] ").formatted(Formatting.GOLD, Formatting.BOLD));
        }
        
        text = text.append(getDisplayText(true));
        
        // Make text clickable
        text = text.styled(style -> style.withClickEvent(
            new ClickEvent(ClickEvent.Action.RUN_COMMAND, command + id.toString())
        ).withHoverEvent(
            new HoverEvent(HoverEvent.Action.SHOW_TEXT, 
                Text.literal("Click to activate this job\n\n")
                    .formatted(Formatting.YELLOW)
                    .append(Text.literal("- Collect " + quantity + " " + getItem().getName().getString() + "\n")
                        .formatted(Formatting.GRAY))
                    .append(Text.literal("- Earn $" + reward + "\n")
                        .formatted(Formatting.GRAY))
                    .append(Text.literal("- Abandoning costs 75% of reward")
                        .formatted(Formatting.RED))
            )
        ));
        
        return text;
    }
    
    /**
     * Gets a formatted display text for this job with both adjusted quantity and reward.
     *
     * @param includeStatus Whether to include active/job status
     * @param adjustedQuantity The adjusted quantity for the job
     * @param showOriginalQuantity Whether to show the original quantity in parentheses
     * @param adjustedReward The adjusted reward amount (with bonuses)
     * @param showBaseReward Whether to show the base reward in parentheses
     * @return The formatted text
     */
    public MutableText getDisplayTextWithAdjustments(boolean includeStatus, int adjustedQuantity, boolean showOriginalQuantity, 
                                                  int adjustedReward, boolean showBaseReward) {
        MutableText text = Text.empty();
        
        // Add status if requested
        if (includeStatus) {
            if (isActive()) {
                text = text.append(Text.literal("【ACTIVE】 ").styled(style -> style.withColor(Formatting.GREEN).withBold(true)));
            } else {
                text = text.append(Text.literal("【JOB】 ").styled(style -> style.withColor(Formatting.AQUA)));
            }
        }
        
        // If this is a mega job, add a special prefix
        if (isMegaJob) {
            text = text.append(Text.literal("【MEGA】 ").styled(style -> style.withColor(Formatting.GOLD).withBold(true)));
        }
        
        // Format item name correctly with proper capitalization
        Item item = getItem();
        String itemName = item.getName().getString();
        String displayName = (adjustedQuantity == 1 ? itemName : itemName + "s");
        
        // Add job details with better formatting
        MutableText jobText = Text.literal("Collect ")
                .append(Text.literal(String.valueOf(adjustedQuantity)).styled(style -> style.withColor(Formatting.YELLOW).withBold(true)));
        
        // Show original quantity if requested and different
        if (showOriginalQuantity && adjustedQuantity != quantity) {
            jobText.append(Text.literal(" (-" + (quantity - adjustedQuantity) + ")").styled(style -> style.withColor(Formatting.DARK_AQUA)));
        }
        
        jobText.append(" ")
               .append(Text.literal(displayName).styled(style -> style.withColor(Formatting.AQUA).withBold(true)))
               .append(Text.literal(" • Reward: ").styled(style -> style.withColor(Formatting.GRAY)));
        
        // Add the reward
        if (showBaseReward && adjustedReward != reward) {
            jobText = jobText.append(Text.literal("$" + adjustedReward).styled(style -> style.withColor(Formatting.GREEN).withBold(true)))
                    .append(Text.literal(" (+$" + (adjustedReward - reward) + ")").styled(style -> style.withColor(Formatting.GRAY)));
        } else {
            jobText = jobText.append(Text.literal("$" + adjustedReward).styled(style -> style.withColor(Formatting.GREEN).withBold(true)));
        }
        
        return text.append(jobText);
    }
    
    /**
     * Creates clickable job text with adjusted quantity and reward.
     *
     * @param commandPrefix The command prefix
     * @param adjustedQuantity The adjusted quantity for the job
     * @param showOriginalQuantity Whether to show the original quantity
     * @param adjustedReward The adjusted reward amount (with bonuses)
     * @param showBaseReward Whether to show the base reward in parentheses
     * @return The clickable text
     */
    public Text createClickableTextWithAdjustments(String commandPrefix, int adjustedQuantity, boolean showOriginalQuantity,
                                                int adjustedReward, boolean showBaseReward) {
        MutableText text = getDisplayTextWithAdjustments(true, adjustedQuantity, showOriginalQuantity, adjustedReward, showBaseReward);
        
        // Format item name for hover text
        Item item = getItem();
        String itemName = item.getName().getString();
        String displayName = (adjustedQuantity == 1 ? itemName : itemName + "s");
        
        // Create hover text
        MutableText hoverText = Text.literal("Click to ")
                .append(Text.literal(isActive() ? "abandon" : "activate").formatted(isActive() ? Formatting.RED : Formatting.GREEN))
                .append(Text.literal(" job:\n").formatted(Formatting.YELLOW))
                .append(Text.literal(adjustedQuantity + " " + displayName + "\n")
                        .formatted(Formatting.WHITE))
                .append(Text.literal("Reward: $" + adjustedReward)
                        .formatted(Formatting.GOLD));
        
        // Add information about abandonment penalty
        if (isActive()) {
            if (isMegaJob) {
                // No penalty for mega jobs
                hoverText.append(Text.literal("\nMEGA jobs can be abandoned without penalty")
                        .formatted(Formatting.GREEN));
            } else {
                // Regular jobs have a penalty
                hoverText.append(Text.literal("\nPenalty: $" + (int)(adjustedReward * 0.75))
                        .formatted(Formatting.RED));
            }
        }
        
        // Add hover and click events
        return text.styled(style -> style
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
                .withClickEvent(new ClickEvent(
                        ClickEvent.Action.RUN_COMMAND,
                        String.format("%s%s", 
                            isActive() ? "/jobs abandon" : commandPrefix, 
                            isActive() ? "" : getId())
                ))
        );
    }
    
    /**
     * Gets the display text for this job with an adjusted reward.
     * 
     * @param includeStatus Whether to include the status
     * @param adjustedReward The adjusted reward amount (with level bonus)
     * @param showBaseReward Whether to show the base reward in parentheses
     * @return The display text
     */
    public Text getDisplayTextWithAdjustedReward(boolean includeStatus, int adjustedReward, boolean showBaseReward) {
        // Use the new combined method but with original quantity
        return getDisplayTextWithAdjustments(includeStatus, quantity, false, adjustedReward, showBaseReward);
    }
    
    /**
     * Creates clickable job text with an adjusted reward.
     *
     * @param commandPrefix The command prefix
     * @param adjustedReward The adjusted reward amount (with level bonus)
     * @param showBaseReward Whether to show the base reward in parentheses
     * @return The clickable job text
     */
    public Text createClickableTextWithAdjustedReward(String commandPrefix, int adjustedReward, boolean showBaseReward) {
        // Use the new combined method but with original quantity
        return createClickableTextWithAdjustments(commandPrefix, quantity, false, adjustedReward, showBaseReward);
    }
    
    /**
     * Gets a formatted display text for this job, optionally showing adjusted quantity.
     *
     * @param includeStatus Whether to include active/job status
     * @param adjustedQuantity The adjusted quantity for the job
     * @param showOriginalQuantity Whether to show the original quantity in parentheses
     * @return The formatted text
     */
    public MutableText getDisplayTextWithAdjustedQuantity(boolean includeStatus, int adjustedQuantity, boolean showOriginalQuantity) {
        // Use the new combined method but with original reward
        return getDisplayTextWithAdjustments(includeStatus, adjustedQuantity, showOriginalQuantity, reward, false);
    }
    
    /**
     * Creates clickable text with adjusted quantity that activates this job when clicked.
     *
     * @param commandPrefix The command prefix ("/jobs take " etc.)
     * @param adjustedQuantity The adjusted quantity for the job
     * @param showOriginalQuantity Whether to show the original quantity
     * @return The clickable text
     */
    public Text createClickableTextWithAdjustedQuantity(String commandPrefix, int adjustedQuantity, boolean showOriginalQuantity) {
        // Use the new combined method but with original reward
        return createClickableTextWithAdjustments(commandPrefix, adjustedQuantity, showOriginalQuantity, reward, false);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Job job = (Job) o;
        return quantity == job.quantity &&
               reward == job.reward &&
               active == job.active &&
               isMegaJob == job.isMegaJob &&
               Objects.equals(id, job.id) &&
               Objects.equals(itemId, job.itemId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, itemId, quantity, reward, active, isMegaJob);
    }
} 