package net.currencymod.plots;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A chunk registered by an admin as a buyable plot.
 *
 * This is the world-side record — it stores the chunk location, which plot
 * types the admin has enabled for purchase, and the current owner binding
 * (ownerUuid + plotId linking to a Plot in PlotManager.playerPlots).
 *
 * When unowned: ownerUuid == null, plotId == null.
 * When owned:   ownerUuid and plotId are both set.
 */
public class WorldPlot {

    private final ChunkKey location;
    private Set<PlotType> enabledTypes;
    private UUID ownerUuid;
    private UUID plotId;

    /**
     * Constructs an unowned WorldPlot.
     */
    public WorldPlot(ChunkKey location, Set<PlotType> enabledTypes) {
        this.location = location;
        this.enabledTypes = EnumSet.copyOf(enabledTypes.isEmpty()
                ? EnumSet.noneOf(PlotType.class) : enabledTypes);
        this.ownerUuid = null;
        this.plotId = null;
    }

    /**
     * Full constructor used when loading from storage.
     */
    public WorldPlot(ChunkKey location, Set<PlotType> enabledTypes, UUID ownerUuid, UUID plotId) {
        this.location = location;
        this.enabledTypes = enabledTypes.isEmpty()
                ? EnumSet.noneOf(PlotType.class) : EnumSet.copyOf(enabledTypes);
        this.ownerUuid = ownerUuid;
        this.plotId = plotId;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public ChunkKey getLocation() { return location; }

    public Set<PlotType> getEnabledTypes() { return enabledTypes; }

    public UUID getOwnerUuid() { return ownerUuid; }

    public UUID getPlotId() { return plotId; }

    // -------------------------------------------------------------------------
    // Mutators (admin config + ownership changes)
    // -------------------------------------------------------------------------

    public void setEnabledTypes(Set<PlotType> types) {
        this.enabledTypes = types.isEmpty()
                ? EnumSet.noneOf(PlotType.class) : EnumSet.copyOf(types);
    }

    public void setOwnerUuid(UUID ownerUuid) { this.ownerUuid = ownerUuid; }

    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    // -------------------------------------------------------------------------
    // Convenience queries
    // -------------------------------------------------------------------------

    /** True if a player currently owns this plot. */
    public boolean isOwned() { return ownerUuid != null; }

    /** True if the plot is registered, unowned, and has at least one enabled type. */
    public boolean isAvailable() { return !isOwned() && !enabledTypes.isEmpty(); }
}
