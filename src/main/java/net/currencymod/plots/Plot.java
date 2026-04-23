package net.currencymod.plots;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a plot owned by a player.
 */
public class Plot {
    private final UUID id;
    private final PlotType type;
    private final long purchaseTimestamp;

    /**
     * Create a new plot with the given type
     *
     * @param type The type of plot
     */
    public Plot(PlotType type) {
        this.id = UUID.randomUUID();
        this.type = type;
        this.purchaseTimestamp = Instant.now().getEpochSecond();
    }

    /**
     * Create a plot from existing data (used when loading from storage)
     *
     * @param id The unique ID of the plot
     * @param type The type of plot
     * @param purchaseTimestamp The timestamp when the plot was purchased
     */
    public Plot(UUID id, PlotType type, long purchaseTimestamp) {
        this.id = id;
        this.type = type;
        this.purchaseTimestamp = purchaseTimestamp;
    }

    /**
     * Get the unique ID of this plot
     *
     * @return The plot ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Get the type of this plot
     *
     * @return The plot type
     */
    public PlotType getType() {
        return type;
    }

    /**
     * Get the timestamp when this plot was purchased
     *
     * @return The purchase timestamp
     */
    public long getPurchaseTimestamp() {
        return purchaseTimestamp;
    }

    /**
     * Get the daily tax for this plot
     *
     * @return The daily tax amount
     */
    public int getDailyTax() {
        return type.getDailyTax();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Plot plot = (Plot) o;
        return id.equals(plot.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
