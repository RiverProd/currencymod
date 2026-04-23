package net.currencymod.util;

import com.google.gson.*;
import net.currencymod.jobs.Job;

import java.lang.reflect.Type;
import java.util.UUID;

/**
 * A simple adapter for serializing and deserializing Job objects with GSON
 */
public class JobAdapter implements JsonSerializer<Job>, JsonDeserializer<Job> {
    
    @Override
    public JsonElement serialize(Job job, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject jsonObject = new JsonObject();
        
        jsonObject.addProperty("id", job.getId().toString());
        jsonObject.addProperty("itemId", job.getItemId());
        jsonObject.addProperty("quantity", job.getQuantity());
        jsonObject.addProperty("reward", job.getReward());
        jsonObject.addProperty("active", job.isActive());
        jsonObject.addProperty("isMegaJob", job.isMegaJob());
        
        return jsonObject;
    }
    
    @Override
    public Job deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            JsonObject obj = json.getAsJsonObject();
            
            UUID id = UUID.fromString(obj.get("id").getAsString());
            String itemId = obj.get("itemId").getAsString();
            int quantity = obj.get("quantity").getAsInt();
            int reward = obj.get("reward").getAsInt();
            
            // Check if isMegaJob field exists, default to false if not
            boolean isMegaJob = false;
            if (obj.has("isMegaJob")) {
                isMegaJob = obj.get("isMegaJob").getAsBoolean();
            }
            
            Job job = new Job(id, itemId, quantity, reward, isMegaJob);
            
            if (obj.has("active")) {
                boolean active = obj.get("active").getAsBoolean();
                job.setActive(active);
            }
            
            return job;
        } catch (Exception e) {
            throw new JsonParseException("Error deserializing Job", e);
        }
    }
} 