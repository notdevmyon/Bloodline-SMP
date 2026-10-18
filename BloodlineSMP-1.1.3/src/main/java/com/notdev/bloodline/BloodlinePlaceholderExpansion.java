package com.notdev.bloodline;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Optional PlaceholderAPI integration. This class is only loaded when PAPI is installed. */
public final class BloodlinePlaceholderExpansion extends PlaceholderExpansion {
    private static final List<BloodlinePlaceholderExpansion> REGISTERED = new ArrayList<>();

    private final BloodlineManager manager;
    private final String version;
    private final String identifier;

    public BloodlinePlaceholderExpansion(BloodlineManager manager) {
        this(manager, "bloodline");
    }

    public BloodlinePlaceholderExpansion(BloodlineManager manager, String identifier) {
        this.manager = manager;
        this.identifier = identifier;
        this.version = org.bukkit.Bukkit.getPluginManager().getPlugin("BloodlineSMP") == null ? "unknown" : org.bukkit.Bukkit.getPluginManager().getPlugin("BloodlineSMP").getDescription().getVersion();
    }

    public static int registerBoth(BloodlineManager manager) {
        int registered = 0;
        for (String identifier : List.of("bloodline", "bloodlinesmp")) {
            BloodlinePlaceholderExpansion expansion = new BloodlinePlaceholderExpansion(manager, identifier);
            if (expansion.register()) {
                REGISTERED.add(expansion);
                registered++;
            }
        }
        return registered;
    }

    public static void unregisterAll() {
        for (BloodlinePlaceholderExpansion expansion : REGISTERED) {
            expansion.unregister();
        }
        REGISTERED.clear();
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getAuthor() {
        return "Notdev";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        // Internal expansions must persist when PlaceholderAPI itself is reloaded.
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (params == null) {
            return "";
        }

        String key = params.toLowerCase(Locale.ROOT);
        if (key.equals("player_count")) {
            return Integer.toString(manager.getPlayerCount());
        }
        if (player == null || player.getUniqueId() == null) {
            return "";
        }

        Bloodline bloodline = manager.getBloodline(player.getUniqueId());
        if (key.equals("has")) {
            return Boolean.toString(bloodline != null);
        }
        if (key.equals("awakening")) {
            return Boolean.toString(bloodline == null && manager.hasAwakening(player.getUniqueId()));
        }
        if (key.equals("awakening_seconds")) {
            BloodlineManager.AwakeningData awakening = manager.getAwakening(player.getUniqueId());
            if (awakening == null) {
                return "0";
            }
            long remaining = Math.max(0L, awakening.expiryMillis() - System.currentTimeMillis());
            long seconds = remaining / 1000L + (remaining % 1000L == 0L ? 0L : 1L);
            return Long.toString(seconds);
        }
        if (bloodline == null) {
            return switch (key) {
                case "name", "bloodline", "id", "rarity", "ability", "ability_name", "chance" -> "None";
                default -> null;
            };
        }

        return switch (key) {
            case "name", "bloodline" -> bloodline.getDisplayName();
            case "id" -> bloodline.name().toLowerCase(Locale.ROOT);
            case "rarity" -> bloodline.getRarity();
            case "ability", "ability_name" -> switch (bloodline) {
                case VAMPIRE -> "Blood Frenzy";
                case FROSTBORN -> "Frozen Core";
                case SHADOW -> "Shadow Step";
                case DEMON -> "Infernal Fury";
                case STORMBORN -> "Tempest";
                case BERSERKER -> "Blood Rage";
                case TIDEBORN -> "Tidal Rush";
                case TITAN -> "Colossus";
                case VOIDBORN -> "Void Blink";
                case CELESTIAL -> "Divine Grace";
                case PHOENIX -> "Phoenix Flame";
                case ARCANE -> "Arcane Resonance";
                case DRAGONBORN -> "Dragon's Roar";
                case MAYON -> "Silverine Particle Storm";
                case NORMAL -> "None";
            };
            case "chance" -> String.format(Locale.ROOT, "%.2f", bloodline.getChance());
            default -> null;
        };
    }
}
