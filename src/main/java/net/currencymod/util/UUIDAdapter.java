package net.currencymod.util;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.UUID;

/**
 * A simple adapter for serializing and deserializing UUID objects with GSON
 */
public class UUIDAdapter implements JsonSerializer<UUID>, JsonDeserializer<UUID> {
    
    @Override
    public JsonElement serialize(UUID src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }
    
    @Override
    public UUID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        try {
            return UUID.fromString(json.getAsString());
        } catch (Exception e) {
            throw new JsonParseException("Could not parse UUID: " + json, e);
        }
    }
} 