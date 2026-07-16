package com.yagiz.skinpowers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerDataStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, PlayerPowerData> PLAYERS = new HashMap<>();
    private static ModConfig config = new ModConfig();
    private static Path dataDirectory;
    private static boolean dirty;

    private PlayerDataStore() {}

    public static synchronized void load(MinecraftServer server) {
        dataDirectory = server.getWorldPath(LevelResource.ROOT).resolve("skinpowers");
        PLAYERS.clear();
        config = new ModConfig();
        try {
            Files.createDirectories(dataDirectory);
            Path playersFile = dataDirectory.resolve("players.json");
            if (Files.isRegularFile(playersFile)) {
                try (Reader reader = Files.newBufferedReader(playersFile)) {
                    StoreFile store = GSON.fromJson(reader, StoreFile.class);
                    if (store != null && store.players != null) {
                        for (Map.Entry<String, PlayerPowerData> entry : store.players.entrySet()) {
                            try {
                                PLAYERS.put(UUID.fromString(entry.getKey()), entry.getValue());
                            } catch (IllegalArgumentException ignored) {
                                SkinPowersMod.LOGGER.warn("Geçersiz UUID kayıt satırı atlandı: {}", entry.getKey());
                            }
                        }
                    }
                }
            }
            Path configFile = dataDirectory.resolve("config.json");
            if (Files.isRegularFile(configFile)) {
                try (Reader reader = Files.newBufferedReader(configFile)) {
                    ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                    if (loaded != null) config = loaded;
                }
            }
            dirty = false;
            SkinPowersMod.LOGGER.info("Skin Powers: {} oyuncu kaydı yüklendi.", PLAYERS.size());
        } catch (IOException | JsonParseException exception) {
            SkinPowersMod.LOGGER.error("Skin Powers kayıtları okunamadı; boş kayıtla devam ediliyor.", exception);
        }
    }

    public static synchronized PlayerPowerData get(UUID uuid) {
        return PLAYERS.computeIfAbsent(uuid, ignored -> {
            dirty = true;
            return new PlayerPowerData();
        });
    }

    public static synchronized void reset(UUID uuid) {
        get(uuid).reset();
        dirty = true;
    }

    public static synchronized ModConfig config() {
        return config;
    }

    public static synchronized void markDirty() {
        dirty = true;
    }

    public static synchronized void save() {
        if (dataDirectory == null || !dirty) return;
        try {
            Files.createDirectories(dataDirectory);
            Map<String, PlayerPowerData> serialized = new HashMap<>();
            for (Map.Entry<UUID, PlayerPowerData> entry : PLAYERS.entrySet()) {
                serialized.put(entry.getKey().toString(), entry.getValue());
            }
            atomicWrite(dataDirectory.resolve("players.json"), new StoreFile(serialized));
            atomicWrite(dataDirectory.resolve("config.json"), config);
            dirty = false;
        } catch (IOException exception) {
            SkinPowersMod.LOGGER.error("Skin Powers kayıtları yazılamadı.", exception);
        }
    }

    private static void atomicWrite(Path target, Object value) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary)) {
            GSON.toJson(value, writer);
        }
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailure) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class StoreFile {
        private Map<String, PlayerPowerData> players;

        private StoreFile(Map<String, PlayerPowerData> players) {
            this.players = players;
        }
    }
}
