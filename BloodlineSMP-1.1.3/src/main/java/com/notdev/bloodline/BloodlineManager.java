package com.notdev.bloodline;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** Owns Bloodline assignments and persistent awakening data. */
public final class BloodlineManager {
    private static final double CHANCE_TOLERANCE = 0.000001D;

    private final BloodlineSMP plugin;
    private final File dataFile;
    private final Map<UUID, Bloodline> bloodlines = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();
    private final Map<UUID, AwakeningData> awakenings = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> silverineParticlesVisible = new ConcurrentHashMap<>();
    private YamlConfiguration data;
    private boolean loadedSuccessfully;
    private boolean preserveBackup;

    public BloodlineManager(BloodlineSMP plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().severe("Could not create the plugin data folder; player data may not persist.");
        }

        data = new YamlConfiguration();
        if (dataFile.exists()) {
            try {
                data.load(dataFile);
            } catch (IOException | InvalidConfigurationException primaryFailure) {
                File backupFile = new File(dataFile.getParentFile(), dataFile.getName() + ".bak");
                if (backupFile.isFile()) {
                    try {
                        data = new YamlConfiguration();
                        data.load(backupFile);
                        preserveBackup = true;
                        plugin.getLogger().warning("data.yml could not be read; loaded the last backup from data.yml.bak. The known-good backup will be preserved during the first recovery save.");
                    } catch (IOException | InvalidConfigurationException backupFailure) {
                        plugin.getLogger().severe("Both data.yml and data.yml.bak are unreadable. No player data will be overwritten automatically.");
                        throw new IllegalStateException("Bloodline data files are unreadable", backupFailure);
                    }
                } else {
                    plugin.getLogger().severe("data.yml is unreadable and no backup exists. Refusing to continue with empty data to prevent overwriting player assignments.");
                    throw new IllegalStateException("Bloodline data file is unreadable", primaryFailure);
                }
            }
        }
        bloodlines.clear();
        playerNames.clear();
        awakenings.clear();
        silverineParticlesVisible.clear();

        ConfigurationSection players = data.getConfigurationSection("players");
        if (players != null) {
            for (String key : players.getKeys(false)) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(key);
                } catch (IllegalArgumentException exception) {
                    plugin.getLogger().warning("Ignoring invalid player UUID in data.yml: " + key);
                    continue;
                }

                String name = players.getString(key + ".name");
                if (name != null && !name.isBlank()) {
                    playerNames.put(uuid, name);
                }

                if (players.contains(key + ".silverine-particles")) {
                    silverineParticlesVisible.put(uuid, players.getBoolean(key + ".silverine-particles", true));
                }
                String bloodlineName = players.getString(key + ".bloodline");
                Bloodline bloodline = Bloodline.fromString(bloodlineName);
                if (bloodline != null) {
                    bloodlines.put(uuid, bloodline);
                } else if (bloodlineName != null && !bloodlineName.isBlank()) {
                    plugin.getLogger().warning("Ignoring unknown Bloodline '" + bloodlineName + "' for " + key + ".");
                }
            }
        }

        loadAwakenings();
        loadedSuccessfully = true;
    }

    private void loadAwakenings() {
        ConfigurationSection pending = data.getConfigurationSection("awakenings");
        if (pending == null) {
            return;
        }

        for (String key : pending.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(key);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid awakening UUID in data.yml: " + key);
                continue;
            }

            if (bloodlines.containsKey(uuid)) {
                continue;
            }

            long start;
            long expiry;
            ConfigurationSection entry = pending.getConfigurationSection(key);
            if (entry != null) {
                start = entry.getLong("start", 0L);
                expiry = entry.getLong("expiry", 0L);
            } else {
                // Backward compatibility with v1.0.3 data.yml, where the UUID
                // value stored only the completion timestamp.
                expiry = pending.getLong(key, 0L);
                long delaySeconds = Math.max(1L, plugin.getConfig().getLong("settings.assignment-delay", 60L));
                long duration = delaySeconds > Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : delaySeconds * 1000L;
                start = expiry > duration ? expiry - duration : 1L;
            }

            if (start > 0L && expiry > start) {
                awakenings.put(uuid, new AwakeningData(start, expiry));
            } else if (expiry > 0L) {
                plugin.getLogger().warning("Ignoring invalid awakening timestamps for " + key + ".");
            }
        }
    }

    private org.bukkit.scheduler.BukkitTask pendingSaveTask;
    private boolean dirty;

    public synchronized void save() {
        String serialized = buildSerializedData();
        if (serialized != null) writeSerializedData(serialized);
        dirty = false;
        if (pendingSaveTask != null) {
            pendingSaveTask.cancel();
            pendingSaveTask = null;
        }
    }

    private synchronized String buildSerializedData() {
        if (!loadedSuccessfully) {
            plugin.getLogger().severe("Skipped data.yml save because loading did not complete successfully; existing data and backups were preserved.");
            return null;
        }
        if (data == null) data = new YamlConfiguration();
        data.set("players", null);
        data.set("awakenings", null);
        for (Map.Entry<UUID, Bloodline> entry : bloodlines.entrySet()) {
            UUID uuid = entry.getKey();
            String path = "players." + uuid;
            data.set(path + ".bloodline", entry.getValue().name());
            String name = playerNames.get(uuid);
            if (name != null && !name.isBlank()) data.set(path + ".name", name);
        }
        for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
            if (!bloodlines.containsKey(entry.getKey()) && entry.getValue() != null && !entry.getValue().isBlank())
                data.set("players." + entry.getKey() + ".name", entry.getValue());
        }
        for (Map.Entry<UUID, Boolean> entry : silverineParticlesVisible.entrySet())
            data.set("players." + entry.getKey() + ".silverine-particles", entry.getValue());
        for (Map.Entry<UUID, AwakeningData> entry : awakenings.entrySet()) {
            String path = "awakenings." + entry.getKey();
            data.set(path + ".start", entry.getValue().startMillis());
            data.set(path + ".expiry", entry.getValue().expiryMillis());
        }
        return data.saveToString();
    }

    private void writeSerializedData(String serialized) {
        try {
            File parent = dataFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                plugin.getLogger().severe("Could not create the plugin data folder; data.yml was not saved.");
                return;
            }
            Path destination = dataFile.toPath();
            Path temporary = destination.resolveSibling(dataFile.getName() + ".tmp");
            Path backup = destination.resolveSibling(dataFile.getName() + ".bak");
            Files.writeString(temporary, serialized, java.nio.charset.StandardCharsets.UTF_8);
            if (Files.exists(destination) && !preserveBackup)
                Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            preserveBackup = false;
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not safely save data.yml (existing data was left in place where possible): " + exception.getMessage());
        }
    }

    private void saveIfEnabled() {
        if (!plugin.getConfig().getBoolean("settings.auto-save", true)) return;
        dirty = true;
        if (pendingSaveTask != null) pendingSaveTask.cancel();
        pendingSaveTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            String serialized = buildSerializedData();
            pendingSaveTask = null;
            if (serialized == null) return;
            dirty = false;
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeSerializedData(serialized));
        }, 100L);
    }

    public boolean isSilverineParticlesVisible(UUID uuid) {
        return uuid == null || silverineParticlesVisible.getOrDefault(uuid, true);
    }

    public void setSilverineParticlesVisible(UUID uuid, boolean visible) {
        if (uuid == null) return;
        silverineParticlesVisible.put(uuid, visible);
        saveIfEnabled();
    }

    public Bloodline getBloodline(UUID uuid) {
        return uuid == null ? null : bloodlines.get(uuid);
    }

    public String getPlayerName(UUID uuid) {
        return uuid == null ? null : playerNames.get(uuid);
    }

    public BloodlineSMP getPlugin() {
        return plugin;
    }

    public int getPlayerCount() {
        return bloodlines.size();
    }

    public Map<UUID, Bloodline> getBloodlines() {
        return Collections.unmodifiableMap(new HashMap<>(bloodlines));
    }

    public Map<UUID, AwakeningData> getAwakenings() {
        return Collections.unmodifiableMap(new HashMap<>(awakenings));
    }

    public AwakeningData getAwakening(UUID uuid) {
        return uuid == null ? null : awakenings.get(uuid);
    }

    public boolean hasAwakening(UUID uuid) {
        return uuid != null && awakenings.containsKey(uuid);
    }

    public void updatePlayerName(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank() || name.equals(playerNames.get(uuid))) {
            return;
        }
        playerNames.put(uuid, name);
        saveIfEnabled();
    }

    public void setBloodline(UUID uuid, String playerName, Bloodline bloodline) {
        if (uuid == null || bloodline == null) {
            return;
        }
        bloodlines.put(uuid, bloodline);
        awakenings.remove(uuid);
        if (playerName != null && !playerName.isBlank()) {
            playerNames.put(uuid, playerName);
        }
        saveIfEnabled();
    }

    public boolean resetBloodline(UUID uuid) {
        if (uuid == null) {
            return false;
        }

        boolean removedBloodline = bloodlines.remove(uuid) != null;
        boolean removedAwakening = awakenings.remove(uuid) != null;
        if (removedBloodline || removedAwakening) {
            saveIfEnabled();
        }
        return removedBloodline;
    }

    public Bloodline assignIfMissing(UUID uuid, String playerName) {
        if (uuid == null) {
            return null;
        }

        Bloodline existing = bloodlines.get(uuid);
        if (existing != null) {
            if (playerName != null && !playerName.isBlank()) {
                updatePlayerName(uuid, playerName);
            }
            return existing;
        }

        Bloodline selected = rollBloodline();
        setBloodline(uuid, playerName, selected);
        return selected;
    }

    public Bloodline rollBloodline() {
        double total = Bloodline.getTotalChance();
        if (!Double.isFinite(total) || total <= 0.0D || Math.abs(total - 100.0D) > CHANCE_TOLERANCE) {
            throw new IllegalStateException("Obtainable Bloodline chances must total 100%. Current total: " + total);
        }

        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cumulative = 0.0D;
        for (Bloodline bloodline : Bloodline.values()) {
            if (!bloodline.isObtainable()) {
                continue;
            }
            cumulative += bloodline.getChance();
            if (roll < cumulative) {
                return bloodline;
            }
        }

        // Protect against floating-point rounding at the upper bound.
        for (int i = Bloodline.values().length - 1; i >= 0; i--) {
            Bloodline candidate = Bloodline.values()[i];
            if (candidate.isObtainable()) {
                return candidate;
            }
        }
        throw new IllegalStateException("No obtainable Bloodlines are configured.");
    }

    public Bloodline rerollBloodline(UUID uuid, String playerName) {
        if (uuid == null) {
            return null;
        }
        Bloodline previous = bloodlines.get(uuid);
        if (previous == null) {
            return assignIfMissing(uuid, playerName);
        }
        if (playerName != null && !playerName.isBlank()) {
            playerNames.put(uuid, playerName);
        }

        Bloodline selected = rollDifferentBloodline(previous);
        if (selected == null) {
            return previous;
        }
        bloodlines.put(uuid, selected);
        awakenings.remove(uuid);
        saveIfEnabled();
        return selected;
    }

    public Bloodline rollDifferentBloodline(Bloodline previous) {
        double totalDifferentWeight = 0.0D;
        for (Bloodline candidate : Bloodline.values()) {
            if (candidate.isObtainable() && candidate != previous) {
                totalDifferentWeight += candidate.getChance();
            }
        }
        if (totalDifferentWeight <= 0.0D) {
            return null;
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalDifferentWeight);
        double cumulative = 0.0D;
        for (Bloodline candidate : Bloodline.values()) {
            if (!candidate.isObtainable() || candidate == previous) {
                continue;
            }
            cumulative += candidate.getChance();
            if (roll < cumulative) {
                return candidate;
            }
        }

        // Floating-point rounding fallback: still guarantee a different obtainable line.
        for (Bloodline candidate : Bloodline.values()) {
            if (candidate.isObtainable() && candidate != previous) {
                return candidate;
            }
        }
        return null;
    }

    public boolean startAwakening(UUID uuid, String playerName, long delaySeconds) {
        if (delaySeconds < 0L || delaySeconds > Long.MAX_VALUE / 1000L) {
            return false;
        }
        long now = System.currentTimeMillis();
        long duration = delaySeconds * 1000L;
        if (now > Long.MAX_VALUE - duration) {
            return false;
        }
        return startAwakening(uuid, playerName, now, now + duration);
    }

    public boolean startAwakening(UUID uuid, String playerName, long startMillis, long expiryMillis) {
        if (uuid == null || startMillis <= 0L || expiryMillis <= startMillis
                || bloodlines.containsKey(uuid) || awakenings.containsKey(uuid)) {
            return false;
        }
        if (playerName != null && !playerName.isBlank()) {
            playerNames.put(uuid, playerName);
        }
        awakenings.put(uuid, new AwakeningData(startMillis, expiryMillis));
        saveIfEnabled();
        return true;
    }

    public boolean clearAwakening(UUID uuid) {
        if (uuid == null || awakenings.remove(uuid) == null) {
            return false;
        }
        saveIfEnabled();
        return true;
    }

    public record AwakeningData(long startMillis, long expiryMillis) {
    }
}
