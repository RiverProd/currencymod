package net.currencymod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.currencymod.CurrencyMod;
import net.currencymod.util.FileUtil;
import net.currencymod.util.UUIDAdapter;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists per-player GUI preference (on/off) to gui_preferences.json.
 * Default is false (text-based system) for all players.
 */
public class GuiPreferenceManager {
    private static final String PREFS_FILE = "currency_mod/gui_preferences.json";
    private static final Type PREFS_TYPE = new TypeToken<Map<UUID, Boolean>>() {}.getType();

    private static GuiPreferenceManager instance;

    private final Map<UUID, Boolean> preferences = new HashMap<>();
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(UUID.class, new UUIDAdapter())
            .create();

    private GuiPreferenceManager() {}

    public static GuiPreferenceManager getInstance() {
        if (instance == null) {
            instance = new GuiPreferenceManager();
        }
        return instance;
    }

    public boolean isGuiEnabled(UUID playerUuid) {
        return preferences.getOrDefault(playerUuid, false);
    }

    public void setGuiEnabled(UUID playerUuid, boolean enabled) {
        preferences.put(playerUuid, enabled);
        save();
    }

    public void load(MinecraftServer server) {
        File file = FileUtil.getServerFile(server, PREFS_FILE);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            Map<UUID, Boolean> loaded = gson.fromJson(reader, PREFS_TYPE);
            if (loaded != null) {
                preferences.clear();
                preferences.putAll(loaded);
            }
        } catch (IOException e) {
            CurrencyMod.LOGGER.error("Failed to load GUI preferences: {}", e.getMessage());
        }
    }

    public void save() {
        MinecraftServer server = CurrencyMod.getServer();
        if (server == null) return;

        File file = FileUtil.getServerFile(server, PREFS_FILE);
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(preferences, writer);
        } catch (IOException e) {
            CurrencyMod.LOGGER.error("Failed to save GUI preferences: {}", e.getMessage());
        }
    }
}
