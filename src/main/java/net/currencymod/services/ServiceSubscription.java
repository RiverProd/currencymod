package net.currencymod.services;

import java.util.UUID;

/**
 * Represents a player's subscription to a service.
 */
public class ServiceSubscription {
    private final UUID subscriberUuid;
    private final String subscriberName; // Username of the subscriber (for display when offline)
    private final String serviceTag;
    private final long subscriptionDate; // Minecraft day when subscription started
    private final int contractDays; // Contract days at time of subscription (0 = no contract)
    private int daysPaid; // Number of days already paid for
    private int dailyPrice; // Daily price at time of subscription (locked in)
    private long lastChargedDay; // Last Minecraft day when subscriber was charged
    
    /**
     * Creates a new subscription.
     *
     * @param subscriberUuid The UUID of the subscriber
     * @param subscriberName The username of the subscriber
     * @param serviceTag The service tag
     * @param subscriptionDate The Minecraft day when subscription started
     * @param contractDays The contract days at time of subscription
     * @param dailyPrice The daily price at time of subscription
     */
    public ServiceSubscription(UUID subscriberUuid, String subscriberName, String serviceTag, long subscriptionDate, 
                              int contractDays, int dailyPrice) {
        this.subscriberUuid = subscriberUuid;
        this.subscriberName = subscriberName;
        this.serviceTag = serviceTag.toUpperCase();
        this.subscriptionDate = subscriptionDate;
        this.contractDays = contractDays;
        this.dailyPrice = dailyPrice;
        this.daysPaid = 0;
        this.lastChargedDay = -1;
    }
    
    /**
     * Creates a subscription from existing data (used when loading from storage).
     *
     * @param subscriberUuid The UUID of the subscriber
     * @param subscriberName The username of the subscriber
     * @param serviceTag The service tag
     * @param subscriptionDate The Minecraft day when subscription started
     * @param contractDays The contract days
     * @param dailyPrice The daily price
     * @param daysPaid The number of days already paid for
     * @param lastChargedDay The last Minecraft day when subscriber was charged
     */
    public ServiceSubscription(UUID subscriberUuid, String subscriberName, String serviceTag, long subscriptionDate,
                              int contractDays, int dailyPrice, int daysPaid, long lastChargedDay) {
        this.subscriberUuid = subscriberUuid;
        this.subscriberName = subscriberName != null ? subscriberName : "Unknown Player";
        this.serviceTag = serviceTag.toUpperCase();
        this.subscriptionDate = subscriptionDate;
        this.contractDays = contractDays;
        this.dailyPrice = dailyPrice;
        this.daysPaid = daysPaid;
        this.lastChargedDay = lastChargedDay;
    }
    
    /**
     * Gets the subscriber's UUID.
     *
     * @return The subscriber UUID
     */
    public UUID getSubscriberUuid() {
        return subscriberUuid;
    }
    
    /**
     * Gets the subscriber's username.
     *
     * @return The subscriber username
     */
    public String getSubscriberName() {
        return subscriberName;
    }
    
    /**
     * Gets the service tag.
     *
     * @return The service tag
     */
    public String getServiceTag() {
        return serviceTag;
    }
    
    /**
     * Gets the subscription date (Minecraft day).
     *
     * @return The subscription date
     */
    public long getSubscriptionDate() {
        return subscriptionDate;
    }
    
    /**
     * Gets the contract days.
     *
     * @return The contract days (0 = no contract)
     */
    public int getContractDays() {
        return contractDays;
    }
    
    /**
     * Gets the number of days already paid for.
     *
     * @return The days paid
     */
    public int getDaysPaid() {
        return daysPaid;
    }
    
    /**
     * Gets the daily price (locked in at subscription time).
     *
     * @return The daily price
     */
    public int getDailyPrice() {
        return dailyPrice;
    }
    
    /**
     * Updates the daily price for this subscription.
     * Used when a service provider changes the service price and it should
     * affect existing subscribers.
     *
     * @param dailyPrice The new daily price
     */
    public void setDailyPrice(int dailyPrice) {
        this.dailyPrice = dailyPrice;
    }
    
    /**
     * Gets the last Minecraft day when subscriber was charged.
     *
     * @return The last charged day (-1 if never charged)
     */
    public long getLastChargedDay() {
        return lastChargedDay;
    }
    
    /**
     * Increments the days paid.
     */
    public void incrementDaysPaid() {
        this.daysPaid++;
    }
    
    /**
     * Decrements the days paid (for using prepaid days).
     */
    public void decrementDaysPaid() {
        if (this.daysPaid > 0) {
            this.daysPaid--;
        }
    }
    
    /**
     * Sets the last charged day.
     *
     * @param day The Minecraft day
     */
    public void setLastChargedDay(long day) {
        this.lastChargedDay = day;
    }
    
    /**
     * Checks if the subscription has fulfilled its contract.
     *
     * @return True if contract is fulfilled or no contract exists
     */
    public boolean isContractFulfilled() {
        return contractDays == 0 || daysPaid >= contractDays;
    }
    
    /**
     * Gets the remaining contract days.
     *
     * @return The remaining contract days (0 if fulfilled or no contract)
     */
    public int getRemainingContractDays() {
        if (contractDays == 0) {
            return 0;
        }
        return Math.max(0, contractDays - daysPaid);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceSubscription that = (ServiceSubscription) o;
        return subscriberUuid.equals(that.subscriberUuid) && serviceTag.equals(that.serviceTag);
    }
    
    @Override
    public int hashCode() {
        return subscriberUuid.hashCode() * 31 + serviceTag.hashCode();
    }
}

