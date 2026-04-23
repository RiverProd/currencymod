package net.currencymod.util;

import com.google.gson.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.lang.reflect.Type;

/**
 * A simplified adapter for serializing and deserializing ItemStack objects with GSON.
 * Only stores basic item information to avoid MC 1.21.1 API compatibility issues.
 */
public class ItemStackAdapter implements JsonSerializer<ItemStack>, JsonDeserializer<ItemStack> {
    
    @Override
    public JsonElement serialize(ItemStack itemStack, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        
        // Store only basic item data to avoid API compatibility issues
        // Get the registry ID of the item
        String itemId = Registries.ITEM.getId(itemStack.getItem()).toString();
        jsonObject.addProperty("id", itemId);
        jsonObject.addProperty("count", itemStack.getCount());
        
        // Store the display name as basic text
        jsonObject.addProperty("displayName", itemStack.getName().getString());
        
        return jsonObject;
    }
    
    @Override
    public ItemStack deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            JsonObject obj = json.getAsJsonObject();
            
            // Get the ID of the item
            String itemId = obj.get("id").getAsString();
            
            // Parse the ID using Identifier.of which is the correct API for 1.21.1
            Item item = Registries.ITEM.get(Identifier.of(itemId));
            
            // Get the count
            int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
            
            // Create the ItemStack
            return new ItemStack(item, count);
        } catch (Exception e) {
            // Log error and return a fallback item
            System.err.println("Error deserializing ItemStack: " + e.getMessage());
            return new ItemStack(Registries.ITEM.get(Identifier.of("minecraft:stone")));
        }
    }
} 