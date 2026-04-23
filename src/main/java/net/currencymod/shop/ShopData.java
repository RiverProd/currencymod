package net.currencymod.shop;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;

import java.util.Optional;
import java.util.UUID;

public class ShopData {
    private final UUID owner;
    private final double price;
    private ItemStack item;
    private final boolean isBuyShop;
    private final boolean isAdminShop;
    
    // Additional fields for web sync (stored in JSON)
    private String itemName;
    private int quantity;
    private String serviceTag;
    
    /**
     * Constructor for shop data
     * @param owner The UUID of the shop owner
     * @param price The price of items in the shop
     * @param item The item being sold (can be null for buy shops)
     * @param isBuyShop Whether this is a buy shop (true) or sell shop (false)
     */
    public ShopData(UUID owner, double price, ItemStack item, boolean isBuyShop) {
        this(owner, price, item, isBuyShop, false);
    }
    
    /**
     * Constructor for shop data with admin shop flag
     * @param owner The UUID of the shop owner
     * @param price The price of items in the shop
     * @param item The item being sold (can be null for buy shops)
     * @param isBuyShop Whether this is a buy shop (true) or sell shop (false)
     * @param isAdminShop Whether this is an admin shop (true) or regular shop (false)
     */
    public ShopData(UUID owner, double price, ItemStack item, boolean isBuyShop, boolean isAdminShop) {
        this(owner, price, item, isBuyShop, isAdminShop, null, 0, null);
    }
    
    /**
     * Constructor for shop data with all web sync data
     * @param owner The UUID of the shop owner
     * @param price The price of items in the shop
     * @param item The item being sold (can be null for buy shops)
     * @param isBuyShop Whether this is a buy shop (true) or sell shop (false)
     * @param isAdminShop Whether this is an admin shop (true) or regular shop (false)
     * @param itemName The name of the item (for web sync)
     * @param quantity The quantity of items (for web sync)
     * @param serviceTag The service tag if required (for web sync, can be null)
     */
    public ShopData(UUID owner, double price, ItemStack item, boolean isBuyShop, boolean isAdminShop, 
                   String itemName, int quantity, String serviceTag) {
        this.owner = owner;
        this.price = price;
        this.item = item;
        this.isBuyShop = isBuyShop;
        this.isAdminShop = isAdminShop;
        this.itemName = itemName;
        this.quantity = quantity;
        this.serviceTag = serviceTag;
    }
    
    /**
     * Get the shop owner's UUID
     * @return The owner's UUID
     */
    public UUID getOwner() {
        return owner;
    }
    
    /**
     * Get the price of items in the shop
     * @return The price
     */
    public double getPrice() {
        return price;
    }
    
    /**
     * Get the item being sold
     * @return The item
     */
    public ItemStack getItem() {
        return item;
    }
    
    /**
     * Set the item being sold
     * @param item The new item
     */
    public void setItem(ItemStack item) {
        this.item = item;
    }
    
    /**
     * Check if this is a buy shop
     * @return True if this is a buy shop, false if it's a sell shop
     */
    public boolean isBuyShop() {
        return isBuyShop;
    }
    
    /**
     * Check if this is an admin shop
     * @return True if this is an admin shop, false if it's a regular shop
     */
    public boolean isAdminShop() {
        return isAdminShop;
    }
    
    /**
     * Get the item name (for web sync)
     * @return The item name, or null if not set
     */
    public String getItemName() {
        return itemName;
    }
    
    /**
     * Set the item name (for web sync)
     * @param itemName The item name
     */
    public void setItemName(String itemName) {
        this.itemName = itemName;
    }
    
    /**
     * Get the quantity (for web sync)
     * @return The quantity, or 0 if not set
     */
    public int getQuantity() {
        return quantity;
    }
    
    /**
     * Set the quantity (for web sync)
     * @param quantity The quantity
     */
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
    
    /**
     * Get the service tag if this shop requires a service subscription
     * @return The service tag, or null if no service required
     */
    public String getServiceTag() {
        return serviceTag;
    }
    
    /**
     * Set the service tag (for web sync)
     * @param serviceTag The service tag, or null if no service required
     */
    public void setServiceTag(String serviceTag) {
        this.serviceTag = serviceTag;
    }
    
    /**
     * Check if this shop has all required web sync data
     * @return True if itemName and quantity are set
     */
    public boolean hasWebSyncData() {
        return itemName != null && !itemName.isEmpty() && quantity > 0;
    }
    
    /**
     * Convert the shop data to an NBT compound
     * @return The NBT compound
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        
        nbt.putUuid("Owner", owner);
        nbt.putDouble("Price", price);
        nbt.putBoolean("IsBuyShop", isBuyShop);
        nbt.putBoolean("IsAdminShop", isAdminShop);
        
        // For now, simply storing the item information is disabled since the API has changed
        // This would need to be reimplemented for a real production mod
        
        return nbt;
    }
    
    /**
     * Create shop data from an NBT compound
     * @param nbt The NBT compound
     * @param wrapperLookup Registry lookup for item deserialization
     * @return The shop data
     */
    public static ShopData fromNbt(NbtCompound nbt, WrapperLookup wrapperLookup) {
        UUID owner = nbt.getUuid("Owner");
        double price = nbt.getDouble("Price");
        boolean isBuyShop = nbt.getBoolean("IsBuyShop");
        boolean isAdminShop = nbt.contains("IsAdminShop") ? nbt.getBoolean("IsAdminShop") : false;
        
        // For now, item loading is disabled since the API has changed
        // This would need to be reimplemented for a real production mod
        ItemStack item = ItemStack.EMPTY;
        
        return new ShopData(owner, price, item, isBuyShop, isAdminShop);
    }
    
    /**
     * Get a string representation of this shop data
     */
    @Override
    public String toString() {
        String formatPrice = price == Math.floor(price) 
            ? String.format("%.0f", price) 
            : String.format("%.2f", price);
        
        String shopType = isBuyShop ? "Buy" : "Sell";
        if (isAdminShop) {
            shopType = "Admin" + shopType;
        }
        
        return "[Shop: " + shopType + " $" + formatPrice + ", Owner: " + owner + "]";
    }
} 