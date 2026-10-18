package com.notdev.bloodline;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;
import java.util.Locale;

public enum Bloodline {
    NORMAL("Normal", "Common", 26.0, NamedTextColor.WHITE),
    VAMPIRE("Vampire", "Uncommon", 14.0, NamedTextColor.DARK_RED),
    FROSTBORN("Frostborn", "Uncommon", 12.0, NamedTextColor.AQUA),
    SHADOW("Shadow", "Uncommon", 11.0, NamedTextColor.DARK_PURPLE),
    DEMON("Demon", "Rare", 9.0, NamedTextColor.RED),
    STORMBORN("Stormborn", "Rare", 7.0, NamedTextColor.YELLOW),
    BERSERKER("Berserker", "Rare", 6.0, NamedTextColor.GOLD),
    TIDEBORN("Tideborn", "Rare", 4.0, NamedTextColor.BLUE),
    TITAN("Titan", "Epic", 3.5, NamedTextColor.GRAY),
    VOIDBORN("Voidborn", "Epic", 2.5, NamedTextColor.DARK_GRAY),
    CELESTIAL("Celestial", "Legendary", 2.0, NamedTextColor.LIGHT_PURPLE),
    PHOENIX("Phoenix", "Legendary", 1.5, NamedTextColor.GOLD),
    ARCANE("Arcane", "Legendary", 1.0, NamedTextColor.LIGHT_PURPLE),
    DRAGONBORN("Dragonborn", "Mythic", 0.5, NamedTextColor.DARK_RED),
    MAYON("Mayon", "Special", 0.0, NamedTextColor.GOLD);

    private final String displayName;
    private final String rarity;
    private final double chance;
    private final NamedTextColor color;

    Bloodline(String displayName, String rarity, double chance, NamedTextColor color) {
        this.displayName = displayName;
        this.rarity = rarity;
        this.chance = chance;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRarity() {
        return rarity;
    }

    public double getChance() {
        return chance;
    }

    public NamedTextColor getColor() {
        return color;
    }

    public boolean hasAbility() {
        return this != NORMAL;
    }

    public boolean isObtainable() {
        return this != MAYON && chance > 0.0;
    }

    public static Bloodline fromString(String input) {
        if (input == null || input.isBlank()) return null;
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        return Arrays.stream(values())
                .filter(bloodline -> bloodline.name().equals(normalized)
                        || bloodline.displayName.equalsIgnoreCase(input.trim()))
                .findFirst()
                .orElse(null);
    }

    public static double getTotalChance() {
        return Arrays.stream(values())
                .filter(Bloodline::isObtainable)
                .mapToDouble(Bloodline::getChance)
                .sum();
    }
}
