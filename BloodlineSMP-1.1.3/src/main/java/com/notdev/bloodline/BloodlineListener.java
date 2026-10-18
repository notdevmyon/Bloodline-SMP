package com.notdev.bloodline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public final class BloodlineListener implements Listener {
    private static final int FROST_MAX_STACKS = 10;
    private static final long FROST_DECAY_MS = 8_000L;
    private static final int BERSERKER_MAX_STACKS = 5;
    private static final long BERSERKER_TIMEOUT_MS = 10 * 60 * 1000L;
    private static final int BERSERKER_EFFECT_TICKS = 20 * 60 * 10;
    private static final int PHOENIX_MAX_CHARGES = 2;
    private static final long PHOENIX_PROTECTION_MS = 1_000L;
    private static final long STORM_LIGHTNING_COOLDOWN_MS = 5_000L;
    private static final double STORM_PROC_CHANCE = 0.25D;
    private static final double STORM_DAMAGE_MULTIPLIER = 1.15D;
    private static final double VAMPIRE_PROC_CHANCE = 0.45D;
    private static final double VAMPIRE_HEAL = 2.0D;
    private static final double CELESTIAL_RECOVERY_CHANCE = 0.20D;
    private static final double ARCANE_WARD_CHANCE = 0.25D;
    private static final double ARCANE_DAMAGE_REDUCTION = 0.30D;
    private static final double TIDEBORN_WATER_DAMAGE_MULTIPLIER = 2.25D;
    private static final double DRAGONBORN_BOSS_MULTIPLIER = 2.75D;

    private final BloodlineSMP plugin;
    private final BloodlineManager bloodlineManager;
    private final BloodlineItem bloodlineItem;
    private final BloodlineAbility bloodlineAbility;
    private final Map<UUID, Long> sigilClickGuard = new HashMap<>();
    private final BloodlineAnimation bloodlineAnimation;
    private final BloodlineAwakening bloodlineAwakening;
    private final NamespacedKey phoenixChargesKey;
    private final NamespacedKey berserkerMarkerKey;
    private final NamespacedKey berserkerStreakKey;
    private final NamespacedKey berserkerLastKillKey;
    private final NamespacedKey berserkerOwnedEffectsKey;
    private final NamespacedKey berserkerStrengthAmplifierKey;
    private final NamespacedKey berserkerSpeedAmplifierKey;
    private final NamespacedKey berserkerResistanceAmplifierKey;
    private final NamespacedKey berserkerLastVictimKillTimeKey;
    private final NamespacedKey stormLightningCooldownKey;

    private final Map<FrostKey, FrostState> frostStates = new HashMap<>();
    private final Map<UUID, BerserkerState> berserkerStates = new HashMap<>();
    private final Map<UUID, Integer> phoenixCharges = new HashMap<>();
    private final Map<UUID, Long> phoenixProtectionUntil = new HashMap<>();
    private final Map<UUID, Long> stormLightningCooldowns = new HashMap<>();
    private final Map<UUID, Map<PotionEffectType, EffectGrant>> ownedLongEffects = new HashMap<>();
    private BukkitTask passiveRefreshTask;
    private int passiveTicks;

    public BloodlineListener(BloodlineSMP plugin, BloodlineManager bloodlineManager, BloodlineItem bloodlineItem, BloodlineAbility bloodlineAbility, BloodlineAnimation bloodlineAnimation, BloodlineAwakening bloodlineAwakening) {
        this.plugin = plugin;
        this.bloodlineManager = bloodlineManager;
        this.bloodlineItem = bloodlineItem;
        this.bloodlineAbility = bloodlineAbility;
        this.bloodlineAnimation = bloodlineAnimation;
        this.bloodlineAwakening = bloodlineAwakening;
        this.phoenixChargesKey = new NamespacedKey(plugin, "phoenix_charges");
        this.berserkerMarkerKey = new NamespacedKey(plugin, "berserker_effect");
        this.berserkerStreakKey = new NamespacedKey(plugin, "berserker_streak");
        this.berserkerLastKillKey = new NamespacedKey(plugin, "berserker_last_kill");
        this.berserkerOwnedEffectsKey = new NamespacedKey(plugin, "berserker_owned_effects");
        this.berserkerStrengthAmplifierKey = new NamespacedKey(plugin, "berserker_strength_amp");
        this.berserkerSpeedAmplifierKey = new NamespacedKey(plugin, "berserker_speed_amp");
        this.berserkerResistanceAmplifierKey = new NamespacedKey(plugin, "berserker_resistance_amp");
        this.berserkerLastVictimKillTimeKey = new NamespacedKey(plugin, "berserker_last_victim_kill_time");
        this.stormLightningCooldownKey = new NamespacedKey(plugin, "storm_lightning_cooldown");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Bloodline bloodline = bloodlineManager.getBloodline(uuid);
        boolean autoAssign = plugin.getConfig().getBoolean("settings.auto-assign", true);

        if (bloodline == null && autoAssign) {
            String assignmentMode = plugin.getConfig().getString("settings.assignment-mode", "timer");

            if ("direct".equalsIgnoreCase(assignmentMode)) {
                bloodlineAwakening.cancel(uuid);
                bloodlineManager.clearAwakening(uuid);
                bloodline = bloodlineManager.assignIfMissing(uuid, player.getName());
                startRevealIfEnabled(player, bloodline);
            } else {
                BloodlineManager.AwakeningData awakening = bloodlineManager.getAwakening(uuid);

                if (awakening == null) {
                    long delaySeconds = Math.max(0L, plugin.getConfig().getLong("settings.assignment-delay", 60L));

                    if (delaySeconds <= 0L) {
                        bloodline = bloodlineManager.assignIfMissing(uuid, player.getName());
                        startRevealIfEnabled(player, bloodline);
                    } else if (delaySeconds <= Long.MAX_VALUE / 1000L) {
                        long startMillis = System.currentTimeMillis();
                        long durationMillis = delaySeconds * 1000L;

                        if (startMillis <= Long.MAX_VALUE - durationMillis) {
                            long expiryMillis = startMillis + durationMillis;

                            if (bloodlineManager.startAwakening(uuid, player.getName(), startMillis, expiryMillis)) {
                                bloodlineAwakening.start(uuid, startMillis, durationMillis);
                            }
                        }
                    }
                } else {
                    bloodlineAwakening.restore(uuid, awakening.startMillis(), awakening.expiryMillis());
                }
            }
        } else {
            bloodlineManager.updatePlayerName(uuid, player.getName());
        }

        ensurePassiveRefreshTask();

        if (bloodline == null) {
            return;
        }

        if (bloodline == Bloodline.STORMBORN) {
            Long cooldown = player.getPersistentDataContainer().get(stormLightningCooldownKey, PersistentDataType.LONG);

            if (cooldown != null && cooldown > System.currentTimeMillis()) {
                stormLightningCooldowns.put(uuid, cooldown);
            } else {
                player.getPersistentDataContainer().remove(stormLightningCooldownKey);
            }
        } else {
            stormLightningCooldowns.remove(uuid);
            player.getPersistentDataContainer().remove(stormLightningCooldownKey);
        }

        if (bloodline == Bloodline.PHOENIX) {
            initializePhoenix(player);
        } else {
            phoenixCharges.remove(uuid);
            player.getPersistentDataContainer().remove(phoenixChargesKey);
        }

        if (bloodline == Bloodline.BERSERKER) {
            restoreBerserker(player);
        } else if (hasBerserkerMarker(player)) {
            clearBerserkerData(player);
        }

        if (bloodline != null) {
            updateMovementPassives(player);
        }
    }

    public boolean activateAbility(Player player) { return bloodlineAbility.activate(player); }
    public void clearAbilityCooldown(Player player) { if (player != null) bloodlineAbility.clearCooldown(player.getUniqueId()); }

    public void initializeOnline() {
        ensurePassiveRefreshTask();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Bloodline bloodline = bloodlineManager.getBloodline(player.getUniqueId());
            if (bloodline == Bloodline.PHOENIX) initializePhoenix(player);
            if (bloodline == Bloodline.BERSERKER) restoreBerserker(player);
            if (bloodline != null && hasMovementPassive(bloodline)) updateMovementPassives(player);
        }
    }

    private void startRevealIfEnabled(Player player, Bloodline bloodline) {
        if (player == null || bloodline == null) {
            return;
        }
        player.sendMessage(Component.text("Your Bloodline is now ")
                .append(Component.text(bloodline.getDisplayName(), bloodline.getColor()))
                .append(Component.text(".")));
        if (plugin.getConfig().getBoolean("settings.reveal-animation", true)) {
            bloodlineAnimation.start(player, bloodline, null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        bloodlineAbility.forgetInMemoryCooldown(uuid);
        cleanupOwnedLongEffects(event.getPlayer());
        // Ability cooldowns are persisted on the player and must survive disconnects.
        bloodlineAnimation.cancel(uuid);
        frostStates.keySet().removeIf(key -> key.attacker().equals(uuid) || key.target().equals(uuid));
        berserkerStates.remove(uuid);
        phoenixCharges.remove(uuid);
        phoenixProtectionUntil.remove(uuid);
        stormLightningCooldowns.remove(uuid);
        ensurePassiveRefreshTask();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onBloodlineItemUse(PlayerInteractEvent event) {
        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        if (item == null || event.getHand() == null) return;
        boolean coin = bloodlineItem.isCoin(item);
        boolean sigil = bloodlineItem.isSigil(item);
        if (!coin && !sigil) return;
        if (action == Action.RIGHT_CLICK_BLOCK && event.useItemInHand() == Event.Result.DENY) {
            if (plugin.getConfig().getBoolean("debug", false)) plugin.getLogger().info("Bloodline item use denied by another plugin for " + player.getName());
            return;
        }
        if (coin && event.getHand() != EquipmentSlot.HAND) return;
        if (sigil && event.getHand() == EquipmentSlot.OFF_HAND && bloodlineItem.isSigil(player.getInventory().getItemInMainHand())) return;

        if (!player.hasPermission("bloodline.use") && !player.hasPermission("bloodline.admin")) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("You do not have permission to use Bloodline items.", NamedTextColor.RED));
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            event.setCancelled(true);
            player.sendActionBar(Component.text("Bloodline items cannot be used in Spectator mode.", NamedTextColor.RED));
            return;
        }

        if (coin) {
            // Blood Coins activate with a normal main-hand right-click; no sneak input
            // or reroll cooldown is required.
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            rerollBloodline(player, item);
            return;
        }

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.setCancelled(true);

        long clickNow = System.currentTimeMillis();
        Long lastClick = sigilClickGuard.get(player.getUniqueId());
        if (lastClick != null && clickNow - lastClick < 150L) return;
        sigilClickGuard.put(player.getUniqueId(), clickNow);
        try {
            bloodlineAbility.activate(player);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Bloodline ability failed for " + player.getName(), exception);
        }
    }

    private void rerollBloodline(Player player, ItemStack coin) {
        UUID uuid = player.getUniqueId();
        Bloodline current = bloodlineManager.getBloodline(uuid);

        if (current == Bloodline.MAYON) {
            player.sendActionBar(Component.text("Mayon cannot be rerolled.", NamedTextColor.RED));
            return;
        }
        if (current == null) {
            player.sendActionBar(Component.text("You do not have a Bloodline to reroll.", NamedTextColor.RED));
            return;
        }

        if (bloodlineAnimation.isAnimating(uuid)) {
            player.sendActionBar(Component.text("Wait for your current Bloodline animation to finish.", NamedTextColor.RED));
            return;
        }

        Bloodline result = rollDifferentBloodline(current);

        if (result == null) {
            player.sendActionBar(Component.text("No different Bloodline is available right now.", NamedTextColor.RED));
            return;
        }

        bloodlineManager.setBloodline(uuid, player.getName(), result);

        int amount = coin.getAmount();

        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            coin.setAmount(amount - 1);
        }

        handleBloodlineChange(player, current, result, false);

        if (plugin.getConfig().getBoolean("settings.reveal-animation", true)) {
            bloodlineAnimation.start(player, result, null);
        }

        player.sendActionBar(Component.text("Bloodline rerolled: " + result.getDisplayName(), NamedTextColor.GOLD));
    }

    private Bloodline rollDifferentBloodline(Bloodline current) {
        return bloodlineManager.rollDifferentBloodline(current);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Bloodline bloodline = bloodlineManager.getBloodline(player.getUniqueId());

        if (bloodline == null) {
            return;
        }

        // Phoenix's fire immunity must run before the lethal-hit rebirth check,
        // otherwise an eligible fire tick can consume a charge unnecessarily.
        if (bloodline == Bloodline.PHOENIX && isFireDamage(event.getCause())) {
            applyPhoenixFirePassive(player, event);
            if (event.isCancelled()) return;
        }
        if (bloodline == Bloodline.PHOENIX && tryPhoenixSave(player, event)) {
            return;
        }

        switch (bloodline) {
            case SHADOW -> applyShadowDefense(player, event);
            case DEMON -> applyDemonDefense(player, event);
            case FROSTBORN -> applyFrostbornDefense(player, event);
            case TITAN -> applyTitanDefense(player, event);
            case VOIDBORN -> applyVoidbornDefense(player, event);
            case CELESTIAL -> applyCelestialDefense(player, event);
            case DRAGONBORN -> applyDragonbornDefense(player, event);
            case TIDEBORN -> applyTidebornDefense(player, event);
            case ARCANE -> applyArcaneDefense(player, event);
            case VAMPIRE -> applyVampireDefense(player, event);
            case PHOENIX -> { /* handled before rebirth check */ }
            case BERSERKER -> applyBerserkerDefense(player, event);
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAttackDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity().hasMetadata("bloodlinesmp_ability_damage")) return;
        if (event.getFinalDamage() <= 0.0D) {
            return;
        }

        Player attacker = getPlayerAttacker(event);

        if (attacker == null || !isValidAttackCause(event)) {
            return;
        }

        Bloodline bloodline = bloodlineManager.getBloodline(attacker.getUniqueId());

        if (bloodline == null) {
            return;
        }

        switch (bloodline) {
            case VOIDBORN -> applyVoidbornAttackDamage(attacker, event);
            case DRAGONBORN -> handleDragonbornAttack(attacker, event);
            case TIDEBORN -> applyTidebornAttackDamage(attacker, event);
            case STORMBORN -> handleStormbornDamage(attacker, event);
            case FROSTBORN -> applyFrostbiteStrike(attacker, event);
            case SHADOW -> applyShadowStrike(attacker, event);
            case DEMON -> applyDemonStrike(attacker, event);
            case BERSERKER -> applyBerserkerStrike(attacker, event);
            case ARCANE -> applyArcaneStrike(attacker, event);
            default -> {
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttackEffects(EntityDamageByEntityEvent event) {
        if (event.getEntity().hasMetadata("bloodlinesmp_ability_damage")) return;
        if (event.getFinalDamage() <= 0.0D) {
            return;
        }

        Player attacker = getPlayerAttacker(event);

        if (attacker == null || !isValidAttackEffectCause(event)) {
            return;
        }

        Bloodline bloodline = bloodlineManager.getBloodline(attacker.getUniqueId());

        if (bloodline == null) {
            return;
        }

        switch (bloodline) {
            case VAMPIRE -> handleVampireAttack(attacker, event);
            case FROSTBORN -> handleFrostbornAttack(attacker, event);
            case STORMBORN -> handleStormbornAttack(attacker, event);
            case TIDEBORN -> handleTidebornPush(attacker, event);
            default -> {
            }
        }
    }

    /** Frostborn's added passive: frozen targets take extra shatter damage. */
    private void applyFrostbiteStrike(Player attacker, EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof LivingEntity target
                && target.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            event.setDamage(event.getDamage() * 1.75D);
        }
    }

    /** Shadow's added passive: ambush damage is stronger at night. */
    private void applyShadowStrike(Player attacker, EntityDamageByEntityEvent event) {
        if (isNight(attacker.getWorld())) event.setDamage(event.getDamage() * 1.75D);
    }

    /** Demon's added passive: the Nether empowers outgoing attacks. */
    private void applyDemonStrike(Player attacker, EntityDamageByEntityEvent event) {
        if (attacker.getWorld().getEnvironment() == World.Environment.NETHER) {
            event.setDamage(event.getDamage() * 1.75D);
        }
    }

    /** Berserker's added passive: low health increases damage, while the streak still handles its own effects. */
    private void applyBerserkerStrike(Player attacker, EntityDamageByEntityEvent event) {
        if (attacker.getHealth() <= getMaxHealth(attacker) * 0.50D) {
            event.setDamage(event.getDamage() * 1.75D);
        }
    }

    /** Arcane's added passive: occasional magical attacks get a stronger damage burst. */
    private void applyArcaneStrike(Player attacker, EntityDamageByEntityEvent event) {
        if (ThreadLocalRandom.current().nextDouble() < 0.50D) {
            event.setDamage(event.getDamage() * 1.75D);
            attacker.getWorld().spawnParticle(Particle.ENCHANT,
                    event.getEntity().getLocation().add(0, 1.0, 0), 10, 0.3, 0.4, 0.3, 0.04);
        }
    }

    /** Phoenix's added passive: fire damage is cancelled and the player is extinguished; it does not grant per-tick healing. */
    private void applyPhoenixFirePassive(Player player, EntityDamageEvent event) {
        if (!isFireDamage(event.getCause()) || event.getFinalDamage() <= 0.0D) return;
        event.setCancelled(true);
        player.setFireTicks(0);
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1.0, 0),
                12, 0.3, 0.4, 0.3, 0.02);
    }

    /** Berserker's added passive: a little resistance when nearly defeated. */
    private void applyBerserkerDefense(Player player, EntityDamageEvent event) {
        if (player.getHealth() <= getMaxHealth(player) * 0.25D) {
            event.setDamage(event.getDamage() * 0.50D);
        }
    }

    private boolean isValidAttackEffectCause(EntityDamageByEntityEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        return cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.PROJECTILE;
    }

    private boolean isValidAttackCause(EntityDamageByEntityEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();
        return cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || cause == EntityDamageEvent.DamageCause.PROJECTILE;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        UUID dead = event.getEntity().getUniqueId();
        frostStates.keySet().removeIf(key -> key.target().equals(dead) || key.attacker().equals(dead));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimUuid = victim.getUniqueId();
        frostStates.keySet().removeIf(key -> key.target().equals(victimUuid) || key.attacker().equals(victimUuid));
        cleanupOwnedLongEffects(victim);
        Bloodline victimBloodline = bloodlineManager.getBloodline(victimUuid);

        if (victimBloodline == Bloodline.PHOENIX) {
            phoenixCharges.put(victimUuid, PHOENIX_MAX_CHARGES);
            victim.getPersistentDataContainer().set(phoenixChargesKey, PersistentDataType.INTEGER, PHOENIX_MAX_CHARGES);
            phoenixProtectionUntil.remove(victimUuid);
        }

        if (victimBloodline == Bloodline.BERSERKER) {
            berserkerStates.remove(victimUuid);
            clearBerserkerData(victim);
        }

        Player killer = victim.getKiller();

        if (killer == null || killer.equals(victim)) {
            return;
        }

        if (bloodlineManager.getBloodline(killer.getUniqueId()) != Bloodline.BERSERKER) {
            return;
        }

        long now = System.currentTimeMillis();
        Long lastKillTime = victim.getPersistentDataContainer().get(berserkerLastVictimKillTimeKey, PersistentDataType.LONG);

        if (lastKillTime != null && now - lastKillTime < BERSERKER_TIMEOUT_MS) {
            return;
        }

        victim.getPersistentDataContainer().set(berserkerLastVictimKillTimeKey, PersistentDataType.LONG, now);
        handleBerserkerKill(killer);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                updateMovementPassives(player);
            }
        });
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) updateMovementPassives(player);
        });
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) updateMovementPassives(player);
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (bloodlineManager.getBloodline(event.getPlayer().getUniqueId()) != Bloodline.TITAN) {
            return;
        }

        String name = event.getItem().getType().name();
        boolean armor = name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");

        if (armor && ThreadLocalRandom.current().nextDouble() < 0.70D) {
            event.setCancelled(true);
        }
    }

    private void handleVampireAttack(Player player, EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();

        if (!(target instanceof LivingEntity) || target instanceof ArmorStand) {
            return;
        }

        if (target instanceof Player targetPlayer && targetPlayer.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        double chance = VAMPIRE_PROC_CHANCE;

        if (isDaytimeOverworld(player.getWorld())) {
            chance *= 0.90D;
        }

        if (ThreadLocalRandom.current().nextDouble() >= chance) {
            return;
        }

        double before = player.getHealth();
        double healAmount = Math.min(VAMPIRE_HEAL, getMaxHealth(player) - before);

        if (healAmount <= 0.0D) {
            return;
        }

        player.heal(healAmount, EntityRegainHealthEvent.RegainReason.CUSTOM);

        if (player.getHealth() <= before) {
            return;
        }

        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.0, 0), 3, 0.15, 0.15, 0.15, 0.0);
    }

    private void applyVampireDefense(Player player, EntityDamageEvent event) {
        if (isDaytimeOverworld(player.getWorld()) && isFireDamage(event.getCause())) {
            event.setDamage(event.getDamage() * 1.15D);
        }
    }

    private double getMaxHealth(Player player) {
        Attribute attribute = Attribute.MAX_HEALTH;
        if (player.getAttribute(attribute) == null) {
            return 20.0D;
        }
        return player.getAttribute(attribute).getValue();
    }

    private void handleFrostbornAttack(Player attacker, EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target) || target instanceof ArmorStand) {
            return;
        }

        if (target instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        UUID attackerUuid = attacker.getUniqueId();
        UUID targetUuid = target.getUniqueId();
        long now = System.currentTimeMillis();

        // Stale entries are pruned by the periodic maintenance task, not on every hit.
        FrostKey key = new FrostKey(attackerUuid, targetUuid);
        FrostState state = frostStates.get(key);
        if (state == null) {
            state = new FrostState();
        }

        state.stacks = Math.min(FROST_MAX_STACKS, state.stacks + 1);
        state.lastHitTime = now;
        frostStates.put(key, state);

        if (state.stacks >= FROST_MAX_STACKS) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 4, false, true, true));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 1, false, true, true));
            target.getWorld().spawnParticle(Particle.SNOWFLAKE, target.getLocation().add(0, 1.0, 0), 30, 0.45, 0.7, 0.45, 0.02);
            target.getWorld().playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8F, 1.5F);
            state.stacks = 0;
            return;
        }

        int amplifier = Math.min(2, (state.stacks - 1) / 3);
        // 3.4–7 seconds across the stack range; do not scale durations by the old x5 factor.
        int duration = 60 + (state.stacks * 8);
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier, false, true, true));
    }

    private void applyFrostbornDefense(Player player, EntityDamageEvent event) {
        if (isFireDamage(event.getCause())) {
            event.setDamage(event.getDamage() * 1.75D);
        }
    }

    private void updateShadowMovement(Player player) {
        if (isNight(player.getWorld())) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, false, false, false));
            ownedLongEffects.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                    .put(PotionEffectType.NIGHT_VISION, new EffectGrant(0, System.currentTimeMillis() + 20_000L));
            return;
        }

        removeOwnedShortEffect(player, PotionEffectType.SPEED, 1);
        cleanupOwnedEffect(player, PotionEffectType.NIGHT_VISION);
    }

    private void applyShadowDefense(Player player, EntityDamageEvent event) {
        if (isNight(player.getWorld())) {
            event.setDamage(event.getDamage() * 0.35D);
        }
    }

    private boolean isNight(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }

        long time = world.getTime();
        return time >= 13_000L && time < 23_000L;
    }

    private void applyDemonDefense(Player player, EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause == EntityDamageEvent.DamageCause.LAVA) {
            event.setDamage(event.getDamage() * 0.20D);
            return;
        }

        if (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setDamage(event.getDamage() * 0.20D);
            return;
        }

        if (cause == EntityDamageEvent.DamageCause.DROWNING) {
            event.setDamage(event.getDamage() * 1.75D);
            return;
        }

        if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            if (event instanceof EntityDamageByEntityEvent byEntity && (byEntity.getDamager() instanceof Blaze || byEntity.getDamager().getType() == EntityType.MAGMA_CUBE)) {
                event.setDamage(event.getDamage() * 0.15D);
                return;
            }
        }

        if (cause == EntityDamageEvent.DamageCause.PROJECTILE
                && event instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Projectile projectile
                && (projectile.getShooter() instanceof Blaze || projectile.getShooter() instanceof MagmaCube)) {
            event.setDamage(event.getDamage() * 0.15D);
        }
    }

    private void updateStormbornMovement(Player player) {
        if (!player.getWorld().hasStorm()) {
            removeOwnedShortEffect(player, PotionEffectType.SPEED, 1);
            removeOwnedShortEffect(player, PotionEffectType.RESISTANCE, 0);
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 50, 0, false, false, false));
    }

    private void handleStormbornDamage(Player attacker, EntityDamageByEntityEvent event) {
        if (!isStorming(attacker.getWorld())) {
            return;
        }

        Entity target = event.getEntity();

        if (!(target instanceof LivingEntity) || target instanceof ArmorStand || target instanceof Player) {
            return;
        }

        event.setDamage(event.getDamage() * STORM_DAMAGE_MULTIPLIER);
    }

    private void handleStormbornAttack(Player attacker, EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();

        if (target instanceof ArmorStand || !isStorming(attacker.getWorld())) {
            return;
        }

        if (target instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        UUID uuid = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        Long cooldown = stormLightningCooldowns.get(uuid);

        if (cooldown != null) {
            if (now < cooldown) {
                return;
            }
            stormLightningCooldowns.remove(uuid);
        }

        if (ThreadLocalRandom.current().nextDouble() >= STORM_PROC_CHANCE) {
            return;
        }

        long stormCooldownUntil = now + STORM_LIGHTNING_COOLDOWN_MS;

        if (stormCooldownUntil < now) {
            stormCooldownUntil = Long.MAX_VALUE;
        }

        stormLightningCooldowns.put(uuid, stormCooldownUntil);
        attacker.getPersistentDataContainer().set(stormLightningCooldownKey, PersistentDataType.LONG, stormCooldownUntil);
        attacker.getWorld().strikeLightningEffect(target.getLocation());
    }

    private boolean isStorming(World world) {
        return world.hasStorm() && world.isThundering();
    }

    private void updateTidebornMovement(Player player) {
        if (!player.isInWater()) {
            removeOwnedShortEffect(player, PotionEffectType.DOLPHINS_GRACE, 2);
            removeOwnedShortEffect(player, PotionEffectType.WATER_BREATHING, 0);
            return;
        }

        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 30, 2, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 30, 0, false, false, false));
    }

    private void applyTidebornDefense(Player player, EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING && player.isInWater()) {
            event.setCancelled(true);
            return;
        }

        if (player.isInWater() && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setDamage(event.getDamage() * 0.15D);
        }
    }

    private boolean isTidebornTarget(Entity target) {
        return target instanceof LivingEntity && !(target instanceof ArmorStand) && !(target instanceof Player);
    }

    private void applyTidebornAttackDamage(Player attacker, EntityDamageByEntityEvent event) {
        if (!attacker.isInWater() || !isTidebornTarget(event.getEntity())) {
            return;
        }

        event.setDamage(event.getDamage() * TIDEBORN_WATER_DAMAGE_MULTIPLIER);
    }

    private void handleTidebornPush(Player attacker, EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();

        if (!attacker.isInWater() || !isTidebornTarget(target)) {
            return;
        }

        Location center = target.getLocation();

        for (Entity nearby : center.getWorld().getNearbyEntities(center, 3.0D, 1.5D, 3.0D)) {
            if (!(nearby instanceof Monster monster) || monster.isDead() || nearby.equals(target)) {
                continue;
            }

            Location entityLocation = monster.getLocation();
            double x = entityLocation.getX() - center.getX();
            double z = entityLocation.getZ() - center.getZ();
            double length = Math.sqrt((x * x) + (z * z));

            if (length < 0.0001D) {
                x = center.getX() - attacker.getLocation().getX();
                z = center.getZ() - attacker.getLocation().getZ();
                length = Math.sqrt((x * x) + (z * z));

                if (length < 0.0001D) {
                    x = 1.0D;
                    z = 0.0D;
                    length = 1.0D;
                }
            }

            monster.setVelocity(new Vector((x / length) * 0.75D, 0.25D, (z / length) * 0.75D));
        }
    }

    private void applyTitanDefense(Player player, EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent byEntity && isMobDamageSource(byEntity.getDamager())) {
            event.setDamage(event.getDamage() * 0.45D);
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setDamage(event.getDamage() * 0.15D);
        }
    }

    private boolean isMobDamageSource(Entity source) {
        if (source instanceof Enemy) {
            return true;
        }

        return source instanceof Projectile projectile && projectile.getShooter() instanceof Enemy;
    }

    private void applyVoidbornDefense(Player player, EntityDamageEvent event) {
        World.Environment environment = player.getWorld().getEnvironment();

        if (environment == World.Environment.THE_END) {
            event.setDamage(event.getDamage() * 0.65D);
        } else if (environment == World.Environment.NETHER) {
            event.setDamage(event.getDamage() * 0.85D);
        }
    }

    private void applyVoidbornAttackDamage(Player attacker, EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();

        if (!(target instanceof LivingEntity) || target instanceof Player) {
            return;
        }

        World.Environment environment = attacker.getWorld().getEnvironment();

        if (environment == World.Environment.THE_END) {
            event.setDamage(event.getDamage() * (target.getType() == EntityType.ENDERMAN ? 4.0D : 2.25D));
        } else if (environment == World.Environment.NETHER) {
            event.setDamage(event.getDamage() * 1.75D);
        }
    }

    private void applyCelestialDefense(Player player, EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause == EntityDamageEvent.DamageCause.FALL
                || cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setDamage(event.getDamage() * 0.45D);
        }

        if (ThreadLocalRandom.current().nextDouble() < CELESTIAL_RECOVERY_CHANCE
                && player.getHealth() < getMaxHealth(player) * 0.50D
                && event.getFinalDamage() > 0.0D) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 70, 0, false, true, true));
        }
    }

    private void applyArcaneDefense(Player player, EntityDamageEvent event) {
        if (ThreadLocalRandom.current().nextDouble() >= ARCANE_WARD_CHANCE) {
            return;
        }

        event.setDamage(event.getDamage() * (1.0D - ARCANE_DAMAGE_REDUCTION));

        Location location = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(Particle.ENCHANT, location, 12, 0.35D, 0.45D, 0.35D, 0.05D);
        player.getWorld().playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.35F, 1.6F);
    }

    private void applyDragonbornDefense(Player player, EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setDamage(event.getDamage() * 0.20D);
        }
    }

    private void handleDragonbornAttack(Player attacker, EntityDamageByEntityEvent event) {
        Entity target = event.getEntity();

        if (!(target instanceof LivingEntity) || target instanceof Player || target instanceof ArmorStand) {
            return;
        }

        double multiplier = 1.75D;

        if (target.getType() == EntityType.ENDER_DRAGON
                || target.getType() == EntityType.WITHER
                || target.getType() == EntityType.WARDEN) {
            multiplier = DRAGONBORN_BOSS_MULTIPLIER;
        }

        event.setDamage(event.getDamage() * multiplier);
    }

    private boolean tryPhoenixSave(Player player, EntityDamageEvent event) {
        EntityDamageEvent.DamageCause cause = event.getCause();

        if (cause == EntityDamageEvent.DamageCause.KILL
                || cause == EntityDamageEvent.DamageCause.SUICIDE
                || cause == EntityDamageEvent.DamageCause.VOID
                || cause == EntityDamageEvent.DamageCause.WORLD_BORDER) {
            return false;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long protectionUntil = phoenixProtectionUntil.get(uuid);

        if (protectionUntil != null) {
            if (now < protectionUntil) {
                event.setCancelled(true);
                return true;
            }

            phoenixProtectionUntil.remove(uuid);
        }

        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }

        if (event.getFinalDamage() < player.getHealth()) {
            return false;
        }

        int charges = Math.max(0, Math.min(PHOENIX_MAX_CHARGES, phoenixCharges.getOrDefault(uuid, getStoredPhoenixCharges(player))));

        if (charges <= 0) {
            return false;
        }

        int remainingCharges = charges - 1;
        phoenixCharges.put(uuid, remainingCharges);
        player.getPersistentDataContainer().set(phoenixChargesKey, PersistentDataType.INTEGER, remainingCharges);
        event.setCancelled(true);

        double targetHealth = Math.min(5.0D, getMaxHealth(player));
        double currentHealth = player.getHealth();

        if (currentHealth < targetHealth) {
            player.heal(targetHealth - currentHealth, EntityRegainHealthEvent.RegainReason.CUSTOM);
        } else if (currentHealth > targetHealth) {
            player.setHealth(targetHealth);
        }

        player.setNoDamageTicks(20);
        player.setRemainingAir(player.getMaximumAir());
        player.setFireTicks(0);
        phoenixProtectionUntil.put(uuid, now + PHOENIX_PROTECTION_MS);

        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0, false, true, true));

        Location location = player.getLocation().add(0, 1.0, 0);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, location, 40, 0.5, 0.8, 0.5, 0.1);
        player.getWorld().playSound(location, Sound.ITEM_TOTEM_USE, 1.0F, 1.0F);

        player.sendActionBar(Component.text("✦ Phoenix Rebirth • " + remainingCharges + " save" + (remainingCharges == 1 ? "" : "s") + " remaining", NamedTextColor.GOLD));

        return true;
    }

    private void initializePhoenix(Player player) {
        phoenixCharges.put(player.getUniqueId(), getStoredPhoenixCharges(player));
    }

    private int getStoredPhoenixCharges(Player player) {
        Integer stored = player.getPersistentDataContainer().get(phoenixChargesKey, PersistentDataType.INTEGER);

        if (stored == null) {
            return PHOENIX_MAX_CHARGES;
        }

        return Math.max(0, Math.min(PHOENIX_MAX_CHARGES, stored));
    }

    private void handleBerserkerKill(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        BerserkerState state = berserkerStates.get(uuid);

        if (state == null) {
            state = loadBerserkerState(player);
        }

        if (state == null || now - state.lastKillTime >= BERSERKER_TIMEOUT_MS) {
            if (hasBerserkerMarker(player)) {
                clearBerserkerData(player);
            }

            state = new BerserkerState();
            state.streak = 1;
        } else {
            state.streak = Math.min(BERSERKER_MAX_STACKS, state.streak + 1);
        }

        state.lastKillTime = now;
        berserkerStates.put(uuid, state);

        if (hasBerserkerMarker(player)) {
            removeOwnedBerserkerEffects(player);
        }

        applyBerserkerEffects(player, state.streak, BERSERKER_EFFECT_TICKS);

        player.getPersistentDataContainer().set(berserkerMarkerKey, PersistentDataType.BYTE, (byte) 1);
        player.getPersistentDataContainer().set(berserkerStreakKey, PersistentDataType.INTEGER, state.streak);
        player.getPersistentDataContainer().set(berserkerLastKillKey, PersistentDataType.LONG, state.lastKillTime);

        player.sendActionBar(Component.text("⚔ Bloodlust Streak: " + state.streak + "/" + BERSERKER_MAX_STACKS, NamedTextColor.RED));
        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 1.0, 0), 10, 0.3, 0.4, 0.3, 0.1);
    }

    private void applyBerserkerEffects(Player player, int streak, int ticks) {
        int normalizedStreak = Math.min(BERSERKER_MAX_STACKS, Math.max(1, streak));
        int strengthAmplifier = normalizedStreak >= 2 ? 1 : 0;
        int speedAmplifier = normalizedStreak >= 4 ? 1 : 0;

        applyBerserkerEffect(player, PotionEffectType.STRENGTH, strengthAmplifier, ticks, (byte) 1, berserkerStrengthAmplifierKey);
        applyBerserkerEffect(player, PotionEffectType.SPEED, speedAmplifier, ticks, (byte) 2, berserkerSpeedAmplifierKey);

        if (normalizedStreak >= BERSERKER_MAX_STACKS) {
            applyBerserkerEffect(player, PotionEffectType.RESISTANCE, 0, ticks, (byte) 4, berserkerResistanceAmplifierKey);
        } else {
            removeOwnedBerserkerEffect(player, PotionEffectType.RESISTANCE, (byte) 4, berserkerResistanceAmplifierKey);
        }
    }

    private void restoreBerserker(Player player) {
        UUID uuid = player.getUniqueId();
        BerserkerState state = loadBerserkerState(player);

        if (state == null) {
            return;
        }

        long elapsed = System.currentTimeMillis() - state.lastKillTime;
        long remainingMs = BERSERKER_TIMEOUT_MS - elapsed;

        if (remainingMs <= 0L) {
            clearBerserkerData(player);
            berserkerStates.remove(uuid);
            return;
        }

        int remainingTicks = (int) Math.min(BERSERKER_EFFECT_TICKS, Math.max(1L, remainingMs / 50L));

        if (hasBerserkerMarker(player)) {
            removeOwnedBerserkerEffects(player);
        }

        applyBerserkerEffects(player, state.streak, remainingTicks);
        player.getPersistentDataContainer().set(berserkerMarkerKey, PersistentDataType.BYTE, (byte) 1);
        berserkerStates.put(uuid, state);
    }

    private BerserkerState loadBerserkerState(Player player) {
        Integer streak = player.getPersistentDataContainer().get(berserkerStreakKey, PersistentDataType.INTEGER);
        Long lastKill = player.getPersistentDataContainer().get(berserkerLastKillKey, PersistentDataType.LONG);

        if (streak == null || lastKill == null || streak <= 0) {
            return null;
        }

        long elapsed = System.currentTimeMillis() - lastKill;

        if (elapsed >= BERSERKER_TIMEOUT_MS) {
            clearBerserkerData(player);
            return null;
        }

        BerserkerState state = new BerserkerState();
        state.streak = Math.min(BERSERKER_MAX_STACKS, streak);
        state.lastKillTime = lastKill;
        berserkerStates.put(player.getUniqueId(), state);
        return state;
    }

    private void clearBerserkerData(Player player) {
        if (hasBerserkerMarker(player)) {
            removeOwnedBerserkerEffects(player);
        }

        player.getPersistentDataContainer().remove(berserkerMarkerKey);
        player.getPersistentDataContainer().remove(berserkerStreakKey);
        player.getPersistentDataContainer().remove(berserkerLastKillKey);
        berserkerStates.remove(player.getUniqueId());
    }

    private void applyBerserkerEffect(Player player, PotionEffectType type, int amplifier, int ticks, byte bit, NamespacedKey amplifierKey) {
        int mask = getBerserkerOwnedMask(player);
        boolean owned = (mask & bit) != 0;
        PotionEffect current = player.getPotionEffect(type);

        if (owned) {
            Integer previousAmplifier = player.getPersistentDataContainer().get(amplifierKey, PersistentDataType.INTEGER);

            if (current != null && previousAmplifier != null && current.getAmplifier() == previousAmplifier && current.getDuration() <= BERSERKER_EFFECT_TICKS) {
                player.removePotionEffect(type);
            } else if (current != null) {
                clearBerserkerOwnedEffect(player, bit, amplifierKey);
                return;
            }
        } else if (current != null) {
            return;
        }

        player.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
        setBerserkerOwnedEffect(player, bit, amplifierKey, amplifier);
    }

    private void removeOwnedBerserkerEffects(Player player) {
        removeOwnedBerserkerEffect(player, PotionEffectType.STRENGTH, (byte) 1, berserkerStrengthAmplifierKey);
        removeOwnedBerserkerEffect(player, PotionEffectType.SPEED, (byte) 2, berserkerSpeedAmplifierKey);
        removeOwnedBerserkerEffect(player, PotionEffectType.RESISTANCE, (byte) 4, berserkerResistanceAmplifierKey);
    }

    private void removeOwnedBerserkerEffect(Player player, PotionEffectType type, byte bit, NamespacedKey amplifierKey) {
        int mask = getBerserkerOwnedMask(player);

        if ((mask & bit) == 0) {
            return;
        }

        PotionEffect current = player.getPotionEffect(type);
        Integer amplifier = player.getPersistentDataContainer().get(amplifierKey, PersistentDataType.INTEGER);

        if (current != null && amplifier != null && current.getAmplifier() == amplifier && current.getDuration() <= BERSERKER_EFFECT_TICKS) {
            player.removePotionEffect(type);
        }

        clearBerserkerOwnedEffect(player, bit, amplifierKey);
    }

    private int getBerserkerOwnedMask(Player player) {
        Integer mask = player.getPersistentDataContainer().get(berserkerOwnedEffectsKey, PersistentDataType.INTEGER);
        return mask == null ? 0 : mask;
    }

    private void setBerserkerOwnedEffect(Player player, byte bit, NamespacedKey amplifierKey, int amplifier) {
        int mask = getBerserkerOwnedMask(player) | bit;
        player.getPersistentDataContainer().set(berserkerOwnedEffectsKey, PersistentDataType.INTEGER, mask);
        player.getPersistentDataContainer().set(amplifierKey, PersistentDataType.INTEGER, amplifier);
    }

    private void clearBerserkerOwnedEffect(Player player, byte bit, NamespacedKey amplifierKey) {
        int mask = getBerserkerOwnedMask(player) & ~bit;

        if (mask == 0) {
            player.getPersistentDataContainer().remove(berserkerOwnedEffectsKey);
        } else {
            player.getPersistentDataContainer().set(berserkerOwnedEffectsKey, PersistentDataType.INTEGER, mask);
        }

        player.getPersistentDataContainer().remove(amplifierKey);
    }

    private boolean hasBerserkerMarker(Player player) {
        return player.getPersistentDataContainer().has(berserkerMarkerKey, PersistentDataType.BYTE);
    }

    public void handleBloodlineChange(Player player, Bloodline previous, Bloodline current) {
        handleBloodlineChange(player, previous, current, true);
    }

    private void handleBloodlineChange(Player player, Bloodline previous, Bloodline current, boolean clearAbilityCooldown) {
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();
        cleanupOwnedLongEffects(player);
        bloodlineAwakening.cancel(uuid);
        bloodlineManager.clearAwakening(uuid);
        frostStates.keySet().removeIf(key -> key.attacker().equals(uuid) || key.target().equals(uuid));
        phoenixProtectionUntil.remove(uuid);
        stormLightningCooldowns.remove(uuid);

        if (previous == Bloodline.STORMBORN) {
            player.getPersistentDataContainer().remove(stormLightningCooldownKey);
            removeOwnedShortEffect(player, PotionEffectType.SPEED, 1);
            removeOwnedShortEffect(player, PotionEffectType.RESISTANCE, 0);
        }

        if (previous == Bloodline.SHADOW) {
            removeOwnedShortEffect(player, PotionEffectType.SPEED, 1);
            cleanupOwnedEffect(player, PotionEffectType.NIGHT_VISION);
        }

        if (previous == Bloodline.VOIDBORN) {
            removeOwnedShortEffect(player, PotionEffectType.SPEED, 1);
            removeOwnedShortEffect(player, PotionEffectType.NIGHT_VISION, 0);
        }
        if (previous == Bloodline.DEMON) {
            removeOwnedShortEffect(player, PotionEffectType.FIRE_RESISTANCE, 0);
            removeOwnedShortEffect(player, PotionEffectType.STRENGTH, 1);
        }
        if (previous == Bloodline.CELESTIAL) {
            removeOwnedShortEffect(player, PotionEffectType.RESISTANCE, 1);
            removeOwnedShortEffect(player, PotionEffectType.SLOW_FALLING, 0);
        }

        if (previous == Bloodline.TIDEBORN) {
            removeOwnedShortEffect(player, PotionEffectType.DOLPHINS_GRACE, 2);
            removeOwnedShortEffect(player, PotionEffectType.WATER_BREATHING, 0);
        }

        if (previous == Bloodline.BERSERKER) {
            clearBerserkerData(player);
        } else {
            berserkerStates.remove(uuid);
        }

        if (previous == Bloodline.PHOENIX) {
            phoenixCharges.remove(uuid);
            player.getPersistentDataContainer().remove(phoenixChargesKey);
        }

        if (current == Bloodline.PHOENIX) {
            initializePhoenix(player);
        }

        if (current == Bloodline.BERSERKER) {
            restoreBerserker(player);
        }

        if (clearAbilityCooldown) {
            bloodlineAbility.clearCooldown(uuid);
        }
        if (current != null) {
            updateMovementPassives(player);
        }
        if (plugin.getSilverineManager() != null) plugin.getSilverineManager().refreshMayon(player);
        ensurePassiveRefreshTask();
    }

    private void updateMovementPassives(Player player) {
        Bloodline bloodline = bloodlineManager.getBloodline(player.getUniqueId());
        if (bloodline == null) return;
        switch (bloodline) {
            case SHADOW -> updateShadowMovement(player);
            case TIDEBORN -> updateTidebornMovement(player);
            case STORMBORN -> updateStormbornMovement(player);
            case VOIDBORN -> updateVoidbornMovement(player);
            case DEMON -> updateDemonMovement(player);
            case CELESTIAL -> updateCelestialMovement(player);
            default -> { }
        }
    }

    private void updateVoidbornMovement(Player player) {
        if (player.getWorld().getEnvironment() == World.Environment.THE_END) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 100, 0, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 30, 1, false, false, false));
        } else {
            removeOwnedShortEffect(player, PotionEffectType.SPEED, 1);
        }
    }

    private void updateDemonMovement(Player player) {
        if (player.getWorld().getEnvironment() == World.Environment.NETHER) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 30, 0, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 30, 1, false, false, false));
        } else {
            removeOwnedShortEffect(player, PotionEffectType.STRENGTH, 1);
        }
    }

    private void updateCelestialMovement(Player player) {
        if (player.getHealth() <= getMaxHealth(player) * 0.35D) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 1, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 30, 0, false, false, false));
        } else {
            removeOwnedShortEffect(player, PotionEffectType.RESISTANCE, 1);
            removeOwnedShortEffect(player, PotionEffectType.SLOW_FALLING, 0);
        }
    }

    private void ensurePassiveRefreshTask() {
        if (passiveRefreshTask != null) return;
        passiveRefreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (hasMovementPassive(bloodlineManager.getBloodline(online.getUniqueId()))) {
                    updateMovementPassives(online);
                }
            }
            passiveTicks += 10;
            if (passiveTicks >= 100) {
                passiveTicks = 0;
                long now = System.currentTimeMillis();
                frostStates.entrySet().removeIf(entry -> now - entry.getValue().lastHitTime > FROST_DECAY_MS);
            }
            reconcileBerserkerPassives();
        }, 1L, 10L);
    }

    private boolean hasMovementPassive(Bloodline bloodline) {
        return bloodline == Bloodline.SHADOW || bloodline == Bloodline.TIDEBORN
                || bloodline == Bloodline.STORMBORN || bloodline == Bloodline.VOIDBORN
                || bloodline == Bloodline.DEMON || bloodline == Bloodline.CELESTIAL;
    }

    public void cancelTasks() {
        if (passiveRefreshTask != null) {
            passiveRefreshTask.cancel();
            passiveRefreshTask = null;
        }
    }

    private boolean isDaytimeOverworld(World world) {
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return false;
        }

        return world.getTime() < 13_000L;
    }

    private boolean isFireDamage(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.FIRE
                || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                || cause == EntityDamageEvent.DamageCause.LAVA
                || cause == EntityDamageEvent.DamageCause.HOT_FLOOR;
    }

    private void cleanupOwnedLongEffects(Player player) {
        if (player == null) return;
        Map<PotionEffectType, EffectGrant> grants = ownedLongEffects.remove(player.getUniqueId());
        if (grants == null) return;
        for (Map.Entry<PotionEffectType, EffectGrant> entry : grants.entrySet()) {
            PotionEffect live = player.getPotionEffect(entry.getKey());
            EffectGrant grant = entry.getValue();
            long grantRemaining = Math.max(0L, grant.expiresAtMs() - System.currentTimeMillis());
            int grantTicks = (int) Math.ceil(grantRemaining / 50.0D);
            if (live != null && live.getAmplifier() == grant.amplifier()
                    && live.getDuration() <= grantTicks + 2) {
                player.removePotionEffect(entry.getKey());
            }
        }
    }

    private void cleanupOwnedEffect(Player player, PotionEffectType type) {
        Map<PotionEffectType, EffectGrant> grants = ownedLongEffects.get(player.getUniqueId());
        if (grants == null) return;
        EffectGrant grant = grants.get(type);
        if (grant == null) return;
        PotionEffect live = player.getPotionEffect(type);
        int grantTicks = (int) Math.ceil(Math.max(0L, grant.expiresAtMs() - System.currentTimeMillis()) / 50.0D);
        if (live != null && live.getAmplifier() == grant.amplifier() && live.getDuration() <= grantTicks + 2) {
            player.removePotionEffect(type);
        }
        grants.remove(type);
        if (grants.isEmpty()) ownedLongEffects.remove(player.getUniqueId());
    }

    private void reconcileBerserkerPassives() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (bloodlineManager.getBloodline(player.getUniqueId()) != Bloodline.BERSERKER) continue;
            BerserkerState state = berserkerStates.get(player.getUniqueId());
            if (state == null || now - state.lastKillTime >= BERSERKER_TIMEOUT_MS) continue;
            int remainingTicks = (int) Math.min(BERSERKER_EFFECT_TICKS,
                    Math.max(1L, (BERSERKER_TIMEOUT_MS - (now - state.lastKillTime)) / 50L));
            reconcileBerserkerEffect(player, PotionEffectType.STRENGTH,
                    state.streak >= 2 ? 1 : 0, remainingTicks, (byte) 1, berserkerStrengthAmplifierKey);
            reconcileBerserkerEffect(player, PotionEffectType.SPEED,
                    state.streak >= 4 ? 1 : 0, remainingTicks, (byte) 2, berserkerSpeedAmplifierKey);
            if (state.streak >= BERSERKER_MAX_STACKS)
                reconcileBerserkerEffect(player, PotionEffectType.RESISTANCE, 0, remainingTicks, (byte) 4, berserkerResistanceAmplifierKey);
        }
    }

    private void reconcileBerserkerEffect(Player player, PotionEffectType type, int amplifier, int ticks, byte bit, NamespacedKey amplifierKey) {
        PotionEffect live = player.getPotionEffect(type);
        int mask = getBerserkerOwnedMask(player);
        if (live != null && live.getAmplifier() > amplifier) return;
        if (live == null || (mask & bit) == 0) {
            applyBerserkerEffect(player, type, amplifier, ticks, bit, amplifierKey);
        }
    }

    private void removeOwnedShortEffect(Player player, PotionEffectType type, int expectedAmplifier) {
        // Refreshed passive effects expire naturally; never remove unrelated or active-ability effects.
    }

    private Player getPlayerAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }

        return null;
    }

    private record EffectGrant(int amplifier, long expiresAtMs) {
    }

    private record FrostKey(UUID attacker, UUID target) {
    }

    private static final class FrostState {
        private int stacks;
        private long lastHitTime;
    }

    private static final class BerserkerState {
        private int streak;
        private long lastKillTime;
    }
}