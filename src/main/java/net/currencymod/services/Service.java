package net.currencymod.services;

import java.util.UUID;

/**
 * Represents a service that players can subscribe to.
 */
public class Service {
    private final String tag;
    private final UUID providerUuid;
    private final int dailyPrice;
    private final int contractDays; // Minimum days before subscriber can break contract (0 = no contract)
    private double totalRevenue;
    
    /**
     * Creates a new service.
     *
     * @param tag The service tag (max 3 letters)
     * @param providerUuid The UUID of the service provider
     * @param dailyPrice The daily price charged to subscribers
     * @param contractDays The minimum contract days (0 = no contract)
     */
    public Service(String tag, UUID providerUuid, int dailyPrice, int contractDays) {
        this.tag = tag.toUpperCase();
        this.providerUuid = providerUuid;
        this.dailyPrice = dailyPrice;
        this.contractDays = contractDays;
        this.totalRevenue = 0.0;
    }
    
    /**
     * Creates a service from existing data (used when loading from storage).
     *
     * @param tag The service tag
     * @param providerUuid The UUID of the service provider
     * @param dailyPrice The daily price
     * @param contractDays The minimum contract days
     * @param totalRevenue The total revenue earned
     */
    public Service(String tag, UUID providerUuid, int dailyPrice, int contractDays, double totalRevenue) {
        this.tag = tag.toUpperCase();
        this.providerUuid = providerUuid;
        this.dailyPrice = dailyPrice;
        this.contractDays = contractDays;
        this.totalRevenue = totalRevenue;
    }
    
    /**
     * Gets the service tag.
     *
     * @return The tag
     */
    public String getTag() {
        return tag;
    }
    
    /**
     * Gets the provider's UUID.
     *
     * @return The provider UUID
     */
    public UUID getProviderUuid() {
        return providerUuid;
    }
    
    /**
     * Gets the daily price.
     *
     * @return The daily price
     */
    public int getDailyPrice() {
        return dailyPrice;
    }
    
    /**
     * Gets the minimum contract days.
     *
     * @return The contract days (0 = no contract)
     */
    public int getContractDays() {
        return contractDays;
    }
    
    /**
     * Gets the total revenue earned.
     *
     * @return The total revenue
     */
    public double getTotalRevenue() {
        return totalRevenue;
    }
    
    /**
     * Adds revenue to the service.
     *
     * @param amount The amount to add
     */
    public void addRevenue(double amount) {
        this.totalRevenue += amount;
    }
    
    /**
     * Sets the daily price (only affects new customers).
     *
     * @param newPrice The new daily price
     */
    public void setDailyPrice(int newPrice) {
        // Note: This will be handled by ServiceManager to ensure it only affects new customers
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Service service = (Service) o;
        return tag.equals(service.tag);
    }
    
    @Override
    public int hashCode() {
        return tag.hashCode();
    }
}

