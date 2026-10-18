package com.notdev.bloodline;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Objects;
import java.util.UUID;

public final class BloodlineSMP extends JavaPlugin {
    private BloodlineManager bloodlineManager;
    private BloodlineAnimation bloodlineAnimation;
    private BloodlineAwakening bloodlineAwakening;
    private BloodlineListener bloodlineListener;
    private BloodlineItem bloodlineItem;
    private SilverineManager silverineManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        bloodlineManager = new BloodlineManager(this);
        bloodlineManager.load();
        bloodlineItem = new BloodlineItem(this);
        bloodlineItem.registerRecipes();
        silverineManager = new SilverineManager(this, bloodlineManager);
        BloodlineAbility bloodlineAbility = new BloodlineAbility(this, bloodlineManager, silverineManager);
        bloodlineAnimation = new BloodlineAnimation(this);
        bloodlineAwakening = new BloodlineAwakening(this, this::completeAwakening);
        bloodlineListener = new BloodlineListener(this, bloodlineManager, bloodlineItem, bloodlineAbility, bloodlineAnimation, bloodlineAwakening);
        BloodlineCommand bloodlineCommand = new BloodlineCommand(bloodlineManager, bloodlineListener, bloodlineItem, bloodlineAnimation, bloodlineAwakening);
        PluginCommand command = Objects.requireNonNull(getCommand("bloodline"), "The 'bloodline' command is missing from plugin.yml");
        command.setExecutor(bloodlineCommand);
        command.setTabCompleter(bloodlineCommand);
        getServer().getPluginManager().registerEvents(bloodlineListener, this);
        registerPlaceholderAPI();
        silverineManager.initializeOnline();
        bloodlineListener.initializeOnline();
        getLogger().info("BloodlineSMP v" + getDescription().getVersion() + " has been enabled.");
        getLogger().info("Loaded " + bloodlineManager.getPlayerCount() + " saved player(s).");
        getLogger().info("Loaded " + bloodlineManager.getAwakenings().size() + " pending awakening(s).");
    }


    private void completeAwakening(UUID uuid) {
        if (uuid == null || bloodlineManager.getAwakening(uuid) == null) return;
        if (bloodlineManager.getBloodline(uuid) != null) {
            bloodlineManager.clearAwakening(uuid);
            return;
        }
        Player player = Bukkit.getPlayer(uuid);
        // Keep the persisted awakening pending if it expires while the player is offline.
        // Their next join restores the timer and performs the reveal while they are present.
        if (player == null || !player.isOnline()) {
            return;
        }
        String playerName = player.getName();
        if (playerName == null || playerName.isBlank()) playerName = uuid.toString();
        Bloodline result = bloodlineManager.assignIfMissing(uuid, playerName);
        if (result == null) {
            bloodlineManager.clearAwakening(uuid);
            return;
        }
        if (player.isOnline()) {
            bloodlineListener.handleBloodlineChange(player, null, result);
            player.sendMessage(net.kyori.adventure.text.Component.text("Your Bloodline is now ")
                    .append(net.kyori.adventure.text.Component.text(result.getDisplayName(), result.getColor()))
                    .append(net.kyori.adventure.text.Component.text(".")));
            if (getConfig().getBoolean("settings.reveal-animation", true)) {
                bloodlineAnimation.start(player, result, null);
            }
        }
    }

    private void registerPlaceholderAPI() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().info("PlaceholderAPI is not enabled. Placeholder support disabled.");
            return;
        }
        int registered = BloodlinePlaceholderExpansion.registerBoth(bloodlineManager);
        if (registered < 2) {
            getLogger().warning("One or more PlaceholderAPI expansions could not be registered; check for conflicting identifiers.");
        }
        getLogger().info("PlaceholderAPI integration enabled.");
    }

    @Override
    public void onDisable() {
        if (silverineManager != null) silverineManager.shutdown();
        if (bloodlineAnimation != null) bloodlineAnimation.cancelAll();
        if (bloodlineAwakening != null) bloodlineAwakening.cancelAll();
        if (bloodlineListener != null) bloodlineListener.cancelTasks();
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            BloodlinePlaceholderExpansion.unregisterAll();
        }
        if (bloodlineItem != null) bloodlineItem.unregisterRecipes();
        if (bloodlineManager != null) bloodlineManager.save();
        getLogger().info("BloodlineSMP has been disabled.");
    }

    public BloodlineManager getBloodlineManager() {
        return bloodlineManager;
    }

    public SilverineManager getSilverineManager() { return silverineManager; }
}