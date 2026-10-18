package com.notdev.bloodline;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BloodlineAbility {

    private static final long DEFAULT_COOLDOWN_MS = 20_000L;

    private final BloodlineSMP plugin;
    private final BloodlineManager bloodlineManager;
    private final SilverineManager silverine;

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final NamespacedKey cooldownKey;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public BloodlineAbility(BloodlineSMP plugin, BloodlineManager bloodlineManager, SilverineManager silverine) {
        this.plugin = plugin;
        this.bloodlineManager = bloodlineManager;
        this.silverine = silverine;
        this.cooldownKey = new NamespacedKey(plugin, "ability_cooldown_until");
    }

    /** Entry point shared by the command and the reusable Bloodline Sigil. */
    public boolean useAbility(Player player) {
        return activate(player);
    }

    public boolean activate(Player player) {
        if (player == null || !player.isOnline()) {
            return false;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("Abilities cannot be used in Spectator mode.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return false;
        }

        if (!plugin.getConfig().getBoolean("abilities.enabled", true)) {
            sendConfiguredMessage(player, "messages.ability-disabled");
            return false;
        }

        Bloodline bloodline =
                bloodlineManager.getBloodline(player.getUniqueId());

        if (bloodline == null || !bloodline.hasAbility()) {
            sendConfiguredMessage(player, "messages.no-ability");
            return false;
        }

        long now = System.currentTimeMillis();
        UUID uuid = player.getUniqueId();

        Long persistedUntil = player.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
        Long memoryUntil = cooldowns.get(uuid);
        Long cooldownUntil;
        if (persistedUntil == null) {
            cooldownUntil = memoryUntil;
        } else {
            long memoryValue = memoryUntil == null ? 0L : memoryUntil;
            cooldownUntil = Math.max(memoryValue, persistedUntil);
        }

        if (cooldownUntil != null) {
            if (now < cooldownUntil) {
                sendConfiguredMessage(
                        player,
                        "messages.cooldown",
                        "{time}",
                        formatSeconds(cooldownUntil - now)
                );
                return false;
            }

            cooldowns.remove(uuid);
            player.getPersistentDataContainer().remove(cooldownKey);
        }

        boolean activated = switch (bloodline) {
            case VAMPIRE -> activateVampire(player);
            case FROSTBORN -> activateFrostborn(player);
            case SHADOW -> activateShadow(player);
            case DEMON -> activateDemon(player);
            case STORMBORN -> activateStormborn(player);
            case BERSERKER -> activateBerserker(player);
            case TIDEBORN -> activateTideborn(player);
            case TITAN -> activateTitan(player);
            case VOIDBORN -> activateVoidborn(player);
            case CELESTIAL -> activateCelestial(player);
            case PHOENIX -> activatePhoenix(player);
            case ARCANE -> activateArcane(player);
            case DRAGONBORN -> activateDragonborn(player);
            case MAYON -> silverine != null && silverine.startStorm(player);
            case NORMAL -> false;
        };

        if (!activated) {
            return false;
        }

        if (bloodline == Bloodline.MAYON) {
            sendActivated(player, bloodline);
            return true;
        }
        long cooldownMillis = getCooldownMillis(bloodline);
        long nextCooldownUntil = now > Long.MAX_VALUE - cooldownMillis
                ? Long.MAX_VALUE
                : now + cooldownMillis;
        cooldowns.put(uuid, nextCooldownUntil);
        player.getPersistentDataContainer().set(cooldownKey, PersistentDataType.LONG, nextCooldownUntil);

        sendActivated(player, bloodline);
        return true;
    }

    private static final String ABILITY_DAMAGE_METADATA = "bloodlinesmp_ability_damage";

    private boolean damageAsAbility(LivingEntity target, double damage, Player source) {
        if (target == null || target.isDead() || !target.isValid()) return false;
        double before = target.getHealth() + (target instanceof Player p ? p.getAbsorptionAmount() : 0.0D);
        target.setMetadata(ABILITY_DAMAGE_METADATA, new FixedMetadataValue(plugin, true));
        try {
            target.damage(damage, source);
        } finally {
            target.removeMetadata(ABILITY_DAMAGE_METADATA, plugin);
        }
        double after = target.isDead() ? 0.0D : target.getHealth()
                + (target instanceof Player p ? p.getAbsorptionAmount() : 0.0D);
        return after < before;
    }

    private boolean activateVampire(Player player) {
        grantEffect(player, PotionEffectType.STRENGTH, 120, 0); // Strength I
        grantEffect(player, PotionEffectType.SPEED, 120, 0);
        player.heal(8.0D, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.CUSTOM);
        player.setAbsorptionAmount(Math.min(12.0D, player.getAbsorptionAmount() + 6.0D));
        Location center = player.getLocation();
        int hits = 0;
        for (Entity entity : player.getNearbyEntities(4.5D, 3.0D, 4.5D)) {
            if (!(entity instanceof LivingEntity target) || !isValidAbilityTarget(player, target)) continue;
            if (!damageAsAbility(target, 4.0D, player)) continue;
            Vector pull = center.toVector().subtract(target.getLocation().toVector());
            if (pull.lengthSquared() > 0.001D) target.setVelocity(pull.normalize().multiply(0.55D).setY(0.18D));
            hits++;
        }
        if (plugin.getConfig().getBoolean("visuals.particles", true)) {
            player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, center.clone().add(0, 1, 0), 24, 1.4, 0.8, 1.4, 0.1);
            player.getWorld().spawnParticle(Particle.CRIT, center.clone().add(0, 1, 0), 18, 1.0, 0.6, 1.0, 0.2);
        }
        player.playSound(center, Sound.ENTITY_PLAYER_HURT, 1.0F, 0.65F);
        return true;
    }
    private boolean activateFrostborn(Player player) {
        grantEffect(player, PotionEffectType.RESISTANCE, 140, 0); // brief frostguard
        Location center = player.getLocation();
        int affected = 0;
        for (Entity entity : player.getNearbyEntities(5.0D, 3.0D, 5.0D)) {
            if (!(entity instanceof LivingEntity target) || !isValidAbilityTarget(player, target)) continue;
            if (!damageAsAbility(target, 5.0D, player)) continue;
            target.setFreezeTicks(Math.min(140, target.getFreezeTicks() + 100));
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(0.65D).setY(0.22D));
            affected++;
        }
        if (plugin.getConfig().getBoolean("visuals.particles", true)) {
            player.getWorld().spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 1, 0), 50, 2.5, 1.0, 2.5, 0.03);
            player.getWorld().spawnParticle(Particle.CLOUD, center.clone().add(0, 0.4, 0), 20, 1.8, 0.2, 1.8, 0.02);
        }
        player.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, 1.2F, 0.6F);
        return true;
    }
    private boolean activateShadow(Player player) {
        Location origin = player.getLocation().clone();
        // Shadow Step is horizontal; looking down or up must not send the search into the floor/ceiling.
        Vector direction = origin.getDirection().setY(0.0D);
        if (direction.lengthSquared() < 0.000001D) {
            double yaw = Math.toRadians(origin.getYaw());
            direction = new Vector(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        direction.normalize();
        Location destination = null;
        for (double d = 0.5D; d <= 7.0D; d += 0.5D) {
            Location candidate = origin.clone().add(direction.clone().multiply(d));
            candidate.setX(candidate.getBlockX() + 0.5D);
            candidate.setZ(candidate.getBlockZ() + 0.5D);
            candidate.setYaw(origin.getYaw());
            candidate.setPitch(origin.getPitch());
            Location safe = findSafeBlinkLocation(candidate, player, destination == null ? origin.getY() : destination.getY());
            if (safe == null) break;
            destination = safe;
        }
        if (destination == null || destination.distanceSquared(origin) < 1.0D) {
            sendMessage(player, "<dark_purple>Shadow Step found no safe landing space.");
            return false;
        }
        if (!player.teleport(destination)) {
            sendMessage(player, "<dark_purple>Shadow Step was blocked by a teleport or protection rule.");
            return false;
        }
        grantEffect(player, PotionEffectType.SPEED, 100, 1);
        grantEffect(player, PotionEffectType.INVISIBILITY, 50, 0);
        Location center = player.getLocation();
        for (Entity entity : player.getNearbyEntities(3.5D, 2.5D, 3.5D)) {
            if (!(entity instanceof LivingEntity target) || !isValidAbilityTarget(player, target)) continue;
            if (!damageAsAbility(target, 6.0D, player)) continue;
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(0.9D).setY(0.3D));
        }
        if (plugin.getConfig().getBoolean("visuals.particles", true)) player.getWorld().spawnParticle(Particle.PORTAL, center.clone().add(0, 1, 0), 45, 1.1, 0.8, 1.1, 0.2);
        player.getWorld().playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.7F);
        return true;
    }
    private boolean activateDemon(Player player) {
        grantEffect(player, PotionEffectType.STRENGTH, 200, 2); // Strength III
        grantEffect(player, PotionEffectType.RESISTANCE, 200, 1); // Resistance II
        grantEffect(player, PotionEffectType.FIRE_RESISTANCE, 300, 0);
        Location center = player.getLocation();
        int hits = 0;
        for (Entity entity : player.getNearbyEntities(5.0D, 3.0D, 5.0D)) {
            if (!(entity instanceof LivingEntity target) || !isValidAbilityTarget(player, target)) continue;
            if (!damageAsAbility(target, 7.0D, player)) continue;
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(0.8D).setY(0.32D));
            hits++;
        }
        if (plugin.getConfig().getBoolean("visuals.particles", true)) {
            player.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, 1, 0), 55, 2.2, 1.0, 2.2, 0.06);
            player.getWorld().spawnParticle(Particle.LAVA, center.clone().add(0, 0.5, 0), 12, 1.2, 0.3, 1.2, 0.0);
        }
        player.getWorld().playSound(center, Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.65F);
        return true;
    }
    private boolean activateStormborn(Player player) {
        grantEffect(player, PotionEffectType.SPEED, 120, 1);
        grantEffect(player, PotionEffectType.RESISTANCE, 100, 0);
        Location center = player.getLocation();
        World world = player.getWorld();
        int hits = 0;
        for (Entity entity : player.getNearbyEntities(6.0D, 4.0D, 6.0D)) {
            if (!(entity instanceof LivingEntity target) || !isValidAbilityTarget(player, target)) continue;
            if (!damageAsAbility(target, 6.0D, player)) continue;
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(1.05D).setY(0.55D));
            world.spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 14, 0.35, 0.45, 0.35, 0.08);
            hits++;
        }
        if (plugin.getConfig().getBoolean("visuals.particles", true)) world.spawnParticle(Particle.CLOUD, center.clone().add(0, 0.3, 0), 45, 2.0, 0.2, 2.0, 0.05);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7F, 1.5F);
        return true;
    }
    private boolean activateBerserker(Player player) {
        grantEffect(player, PotionEffectType.STRENGTH, 160, 1);
        grantEffect(player, PotionEffectType.RESISTANCE, 120, 0);
        Location center = player.getLocation();
        player.heal(6.0D, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.CUSTOM);
        Vector forward = center.getDirection().setY(0);
        if (forward.lengthSquared() < 0.001D) forward = new Vector(0, 0, 1);
        else forward.normalize();
        int hits = 0;
        for (Entity entity : player.getNearbyEntities(4.5D, 2.5D, 4.5D)) {
            if (!(entity instanceof LivingEntity target) || !isValidAbilityTarget(player, target)) continue;
            Vector to = target.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (to.lengthSquared() > 0.001D && forward.dot(to.normalize()) < 0.0D) continue;
            if (!damageAsAbility(target, 8.0D, player)) continue;
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(1.0D).setY(0.35D));
            hits++;
        }
        if (plugin.getConfig().getBoolean("visuals.particles", true)) player.getWorld().spawnParticle(Particle.CRIT, center.clone().add(0, 1, 0), 50, 1.6, 0.8, 1.6, 0.3);
        player.getWorld().playSound(center, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.2F, 0.55F);
        return true;
    }
    private boolean activateTideborn(Player player) {
        grantEffect(player, PotionEffectType.DOLPHINS_GRACE, 300, 1);
        grantEffect(player, PotionEffectType.WATER_BREATHING, 300, 0);
        grantEffect(player, PotionEffectType.SPEED, 160, 1);
        // Tidal Rush remains useful on land; water-specific effects are still granted
        // through Dolphin's Grace and Water Breathing, without making the ability unusable.
        Location center = player.getLocation();
        Vector forward = center.getDirection().normalize();
        player.setVelocity(forward.multiply(1.55D).setY(Math.max(0.2D, forward.getY() * 0.7D)));
        for (Entity entity : player.getNearbyEntities(4.5D, 3.0D, 4.5D)) {
            if (!(entity instanceof LivingEntity target) || !isValidAbilityTarget(player, target)) continue;
            if (!damageAsAbility(target, 5.0D, player)) continue;
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(1.2D).setY(0.4D));
        }
        if (plugin.getConfig().getBoolean("visuals.particles", true)) player.getWorld().spawnParticle(Particle.SPLASH, center.clone().add(0, 1, 0), 55, 1.2, 0.8, 1.2, 0.15);
        player.getWorld().playSound(center, Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0F, 1.2F);
        return true;
    }
    private boolean activateTitan(Player player) {
        grantEffect(player, PotionEffectType.RESISTANCE, 180, 1);
        grantEffect(player, PotionEffectType.STRENGTH, 180, 0);
        Location center = player.getLocation();
        player.setVelocity(new Vector(0.0D, Math.max(0.65D, player.getVelocity().getY()), 0.0D));
        player.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8F, 0.65F);
        if (plugin.getConfig().getBoolean("visuals.particles", true)) player.getWorld().spawnParticle(Particle.BLOCK, center.clone().add(0, 0.1, 0), 70, 2.5, 0.2, 2.5, 0.1, Material.STONE.createBlockData());
        for (Entity entity : player.getNearbyEntities(5.0D, 3.0D, 5.0D)) {
            if (!(entity instanceof LivingEntity target) || !isValidAbilityTarget(player, target)) continue;
            if (!damageAsAbility(target, 7.0D, player)) continue;
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(1.15D).setY(0.65D));
        }
        return true;
    }
    private boolean activateVoidborn(Player player) {
        Location origin = player.getLocation().clone();
        Vector direction = origin.getDirection().setY(0.0D);
        if (direction.lengthSquared() < 0.000001D) {
            double yaw = Math.toRadians(origin.getYaw());
            direction = new Vector(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        }
        direction.normalize();

        Location destination = null;
        for (double distance = 0.5D; distance <= 6.0D; distance += 0.5D) {
            Location candidate = origin.clone().add(direction.clone().multiply(distance));
            candidate.setX(candidate.getBlockX() + 0.5D);
            candidate.setZ(candidate.getBlockZ() + 0.5D);
            candidate.setYaw(origin.getYaw());
            candidate.setPitch(origin.getPitch());

            Location safe = findSafeBlinkLocation(candidate, player, destination == null ? origin.getY() : destination.getY());
            if (safe == null) break;
            destination = safe;
        }

        if (destination == null || destination.distanceSquared(origin) < 2.25D) {
            sendMessage(player, "<dark_purple>Void Blink found no safe space nearby.");
            return false;
        }

        if (plugin.getConfig().getBoolean("visuals.particles", true)) {
            origin.getWorld().spawnParticle(Particle.PORTAL, origin.clone().add(0.0D, 1.0D, 0.0D),
                    18, 0.25D, 0.45D, 0.25D, 0.2D);
        }

        if (!player.teleport(destination)) {
            sendMessage(player, "<dark_purple>Void Blink could not complete the teleport.");
            return false;
        }
        grantEffect(player, PotionEffectType.SPEED, 120, 1);
        grantEffect(player, PotionEffectType.RESISTANCE, 100, 0);

        Location arrival = player.getLocation();
        Vector burst = arrival.getDirection().setY(0.0D);
        if (burst.lengthSquared() > 0.001D) player.setVelocity(burst.normalize().multiply(0.45D).setY(0.18D));
        for (Entity entity : player.getNearbyEntities(3.0D, 2.5D, 3.0D)) {
            if (!(entity instanceof LivingEntity target) || !isValidAbilityTarget(player, target)) continue;
            if (!damageAsAbility(target, 5.0D, player)) continue;
            Vector away = target.getLocation().toVector().subtract(arrival.toVector());
            if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(0.8D).setY(0.25D));
        }
        if (plugin.getConfig().getBoolean("visuals.particles", true)) {
            player.getWorld().spawnParticle(Particle.PORTAL, arrival.clone().add(0.0D, 1.0D, 0.0D),
                    36, 0.45D, 0.7D, 0.45D, 0.25D);
        }
        player.getWorld().playSound(arrival, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.2F);
        return true;
    }

    private Location findSafeBlinkLocation(Location base, Player player, double previousY) {
        World world = base.getWorld();
        if (world == null || !world.getWorldBorder().isInside(base)) return null;
        double[] offsets = {0.0D, 1.0D, -1.0D};
        for (double offset : offsets) {
            Location candidate = base.clone();
            candidate.setY(Math.floor(previousY) + offset);
            if (!isSafeBlinkLocation(candidate, player)) continue;
            return candidate;
        }
        return null;
    }

    private boolean isSafeBlinkLocation(Location location, Player player) {
        World world = location.getWorld();
        if (world == null || !world.getWorldBorder().isInside(location)) return false;
        int feetY = location.getBlockY();
        if (feetY <= world.getMinHeight() || feetY + 2 >= world.getMaxHeight()) return false;
        for (int y = feetY - 1; y <= feetY + 2; y++) {
            Material type = world.getBlockAt(location.getBlockX(), y, location.getBlockZ()).getType();
            if (isBlinkHazard(type)) return false;
        }
        Material support = world.getBlockAt(location.getBlockX(), feetY - 1, location.getBlockZ()).getType();
        if (!support.isSolid() || isBlinkHazard(support)) return false;
        org.bukkit.util.BoundingBox box = player.getBoundingBox();
        double dx = location.getX() - player.getLocation().getX();
        double dy = location.getY() - player.getLocation().getY();
        double dz = location.getZ() - player.getLocation().getZ();
        org.bukkit.util.BoundingBox shifted = new org.bukkit.util.BoundingBox(
                box.getMinX() + dx, box.getMinY() + dy, box.getMinZ() + dz,
                box.getMaxX() + dx, box.getMaxY() + dy, box.getMaxZ() + dz);
        return !world.hasCollisionsIn(shifted);
    }

    private boolean isBlinkHazard(Material type) {
        return type == Material.LAVA || type == Material.FIRE || type == Material.SOUL_FIRE
                || type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE
                || type == Material.MAGMA_BLOCK || type == Material.CACTUS
                || type == Material.SWEET_BERRY_BUSH || type == Material.COBWEB
                || type == Material.POWDER_SNOW || type == Material.WATER;
    }

    private boolean activateCelestial(Player player) {
        grantEffect(player, PotionEffectType.REGENERATION, 160, 1);
        grantEffect(player, PotionEffectType.RESISTANCE, 100, 0);
        player.heal(10.0D, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.CUSTOM);
        player.setAbsorptionAmount(Math.min(10.0D, player.getAbsorptionAmount() + 5.0D));
        player.setFireTicks(0);
        Location center = player.getLocation();
        for (Entity entity : player.getNearbyEntities(4.5D, 3.0D, 4.5D)) {
            if (!(entity instanceof LivingEntity target) || !isValidAbilityTarget(player, target)) continue;
            if (!damageAsAbility(target, 4.0D, player)) continue;
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(0.75D).setY(0.45D));
        }
        if (plugin.getConfig().getBoolean("visuals.particles", true)) player.getWorld().spawnParticle(Particle.END_ROD, center.clone().add(0, 1, 0), 55, 1.6, 1.1, 1.6, 0.05);
        player.getWorld().playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.3F);
        return true;
    }
    private boolean activatePhoenix(Player player) {
        grantEffect(player, PotionEffectType.FIRE_RESISTANCE, 300, 0);
        grantEffect(player, PotionEffectType.STRENGTH, 160, 1);
        grantEffect(player, PotionEffectType.REGENERATION, 100, 0);
        Location center = player.getLocation();
        player.setFireTicks(0);
        player.heal(5.0D, org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.CUSTOM);
        for (Entity entity : player.getNearbyEntities(5.0D, 3.0D, 5.0D)) {
            if (!(entity instanceof LivingEntity target) || !isValidAbilityTarget(player, target)) continue;
            if (!damageAsAbility(target, 7.0D, player)) continue;
            target.setFireTicks(Math.max(target.getFireTicks(), 100));
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(0.8D).setY(0.45D));
        }
        if (plugin.getConfig().getBoolean("visuals.particles", true)) {
            player.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, 1, 0), 70, 1.8, 1.0, 1.8, 0.05);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center.clone().add(0, 1, 0), 20, 0.8, 0.8, 0.8, 0.2);
        }
        player.getWorld().playSound(center, Sound.ITEM_TOTEM_USE, 0.9F, 1.2F);
        return true;
    }
    private boolean activateArcane(Player player) {
        grantEffect(player, PotionEffectType.STRENGTH, 160, 1);
        grantEffect(player, PotionEffectType.RESISTANCE, 100, 0);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        org.bukkit.util.RayTraceResult trace = player.getWorld().rayTrace(
                eye, direction, 18.0D, org.bukkit.FluidCollisionMode.NEVER, true, 0.8D,
                entity -> entity instanceof LivingEntity target && isValidAbilityTarget(player, target));
        if (trace == null || !(trace.getHitEntity() instanceof LivingEntity hit)) {
            player.sendActionBar(net.kyori.adventure.text.Component.text("No target in range.",
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW));
            return false;
        }
        if (!damageAsAbility(hit, 12.0D, player)) return false;
        Vector knock = direction.clone().multiply(0.9D).setY(0.2D);
        hit.setVelocity(knock);
        if (plugin.getConfig().getBoolean("visuals.particles", true))
            hit.getWorld().spawnParticle(Particle.CRIT, hit.getLocation().add(0, 1, 0), 20, 0.35, 0.45, 0.35, 0.15);
        player.getWorld().playSound(eye, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0F, 1.3F);
        return true;
    }

    private boolean isValidAbilityTarget(Player caster, LivingEntity target) {
        if (caster == null || target == null || target == caster || target.isDead() || !target.isValid()) return false;
        if (target.hasMetadata("NPC")) return false;
        if (!(target instanceof Player other)) return target instanceof Enemy;
        if (other.getGameMode() == GameMode.SPECTATOR || other.getGameMode() == GameMode.CREATIVE
                || other.hasMetadata("vanished") || !caster.getWorld().getPVP()) return false;
        org.bukkit.scoreboard.Team casterTeam = caster.getScoreboard().getEntryTeam(caster.getName());
        org.bukkit.scoreboard.Team targetTeam = other.getScoreboard().getEntryTeam(other.getName());
        return casterTeam == null || targetTeam == null || !casterTeam.equals(targetTeam);
    }
    private boolean activateDragonborn(Player player) {
        grantEffect(player, PotionEffectType.STRENGTH, 200, 2); // Strength III
        grantEffect(player, PotionEffectType.RESISTANCE, 180, 1); // Resistance II
        grantEffect(player, PotionEffectType.FIRE_RESISTANCE, 300, 0);
        Location center = player.getLocation();
        Vector forward = center.getDirection().setY(0);
        if (forward.lengthSquared() < 0.001D) forward = new Vector(0, 0, 1);
        else forward.normalize();
        int affected = 0;
        for (Entity entity : player.getNearbyEntities(7.0D, 4.0D, 7.0D)) {
            if (!(entity instanceof LivingEntity target) || !isValidAbilityTarget(player, target)) continue;
            Vector to = target.getLocation().toVector().subtract(center.toVector()).setY(0);
            if (to.lengthSquared() > 0.001D && forward.dot(to.normalize()) < -0.25D) continue;
            if (!damageAsAbility(target, target instanceof Enemy ? 12.0D : 8.0D, player)) continue;
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
            Vector away = target.getLocation().toVector().subtract(center.toVector());
            if (away.lengthSquared() > 0.001D) target.setVelocity(away.normalize().multiply(1.35D).setY(0.65D));
            affected++;
        }
        if (plugin.getConfig().getBoolean("visuals.particles", true)) {
            player.getWorld().spawnParticle(Particle.DRAGON_BREATH, center.clone().add(0, 1, 0), 65, 2.8, 1.3, 2.8, 0.06);
            player.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, 1, 0), 45, 2.4, 1.0, 2.4, 0.04);
        }
        player.getWorld().playSound(center, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0F, 1.1F);
        return true;
    }
    private void grantEffect(Player player, PotionEffectType type, int durationTicks, int amplifier) {
        // Never replace a stronger or longer existing effect (including effects granted
        // by other plugins). Otherwise apply the bloodline's intended combat window.
        PotionEffect current = player.getPotionEffect(type);
        if (current != null && (current.getAmplifier() > amplifier
                || (current.getAmplifier() == amplifier && current.getDuration() >= durationTicks))) {
            return;
        }
        player.addPotionEffect(new PotionEffect(type, durationTicks, amplifier, false, true, true), true);
    }

    private void sendActivated(Player player, Bloodline bloodline) {
        if (plugin.getConfig().getBoolean(
                "visuals.action-bar",
                true
        )) {
            player.sendActionBar(
                    miniMessage.deserialize(
                            "<gold>"
                                    + bloodline.getDisplayName()
                                    + "</gold> <gray>ability activated!"
                    )
            );
        }

        if (plugin.getConfig().getBoolean(
                "visuals.sounds",
                true
        )) {
            playAbilitySound(player, bloodline);
        }

        if (plugin.getConfig().getBoolean(
                "visuals.particles",
                true
        )) {
            spawnAbilityParticles(player);
        }
    }

    private void playAbilitySound(
            Player player,
            Bloodline bloodline
    ) {
        Sound sound = switch (bloodline) {
            case VAMPIRE -> Sound.ENTITY_PLAYER_BURP;
            case FROSTBORN -> Sound.BLOCK_GLASS_BREAK;
            case SHADOW -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case DEMON -> Sound.ENTITY_BLAZE_SHOOT;
            case STORMBORN -> Sound.ENTITY_LIGHTNING_BOLT_THUNDER;
            case BERSERKER -> Sound.ENTITY_PLAYER_ATTACK_CRIT;
            case TIDEBORN -> Sound.ENTITY_DOLPHIN_PLAY;
            case TITAN -> Sound.ENTITY_IRON_GOLEM_HURT;
            case VOIDBORN -> Sound.ENTITY_ENDERMAN_STARE;
            case CELESTIAL -> Sound.BLOCK_BEACON_ACTIVATE;
            case PHOENIX -> Sound.ENTITY_BLAZE_AMBIENT;
            case ARCANE -> Sound.BLOCK_ENCHANTMENT_TABLE_USE;
            case DRAGONBORN -> Sound.ENTITY_ENDER_DRAGON_GROWL;
            case NORMAL, MAYON -> null;
        };

        if (sound != null) {
            player.playSound(
                    player.getLocation(),
                    sound,
                    0.7f,
                    1.0f
            );
        }
    }

    private void spawnAbilityParticles(Player player) {
        player.getWorld().spawnParticle(
                Particle.ENCHANT,
                player.getLocation().add(0.0, 1.0, 0.0),
                12,
                0.35,
                0.5,
                0.35,
                0.05
        );
    }

    private long getCooldownMillis(Bloodline bloodline) {
        // The original global 20s cooldown remains the ceiling. Rarer bloodlines
        // receive shorter cooldowns so rarity feels meaningful without nerfing anyone.
        long baseMillis = switch (bloodline) {
            case NORMAL -> DEFAULT_COOLDOWN_MS;
            case VAMPIRE -> 18_000L;
            case FROSTBORN -> 17_000L;
            case SHADOW -> 16_000L;
            case DEMON, STORMBORN, BERSERKER, TIDEBORN -> 15_000L;
            case TITAN -> 14_000L;
            case VOIDBORN -> 13_000L;
            case CELESTIAL, PHOENIX, ARCANE -> 12_000L;
            case DRAGONBORN -> 10_000L;
            case MAYON -> DEFAULT_COOLDOWN_MS;
        };
        double multiplier = plugin.getConfig().getDouble("abilities.cooldown-multiplier", 1.0);
        if (!Double.isFinite(multiplier) || multiplier <= 0.0) multiplier = 1.0;
        return Math.max(1_000L, (long) (baseMillis * multiplier));
    }

    public long getRemainingCooldown(UUID uuid) {
        if (uuid == null) return 0L;
        long cooldownUntil = cooldowns.getOrDefault(uuid, 0L);
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) {
            Long persisted = online.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
            if (persisted != null) cooldownUntil = Math.max(cooldownUntil, persisted);
        }
        long remaining = cooldownUntil - System.currentTimeMillis();
        if (remaining <= 0L) {
            cooldowns.remove(uuid);
            return 0L;
        }
        return remaining;
    }

    public void forgetInMemoryCooldown(UUID uuid) {
        if (uuid != null) cooldowns.remove(uuid);
    }

    public boolean isOnCooldown(UUID uuid) {
        return getRemainingCooldown(uuid) > 0L;
    }

    public void clearCooldown(UUID uuid) {
        cooldowns.remove(uuid);
        Player player = plugin.getServer().getPlayer(uuid);
        if (player != null) player.getPersistentDataContainer().remove(cooldownKey);
    }

    public void clearAllCooldowns() {
        cooldowns.clear();
    }

    private void sendConfiguredMessage(
            Player player,
            String path,
            String... replacements
    ) {
        String message =
                plugin.getConfig().getString(path);

        if (message == null || message.isBlank()) {
            return;
        }

        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(
                    replacements[i],
                    replacements[i + 1]
            );
        }

        String prefix =
                plugin.getConfig().getString(
                        "messages.prefix",
                        ""
                );

        sendMessage(
                player,
                prefix + message
        );
    }

    private void sendMessage(
            Player player,
            String message
    ) {
        player.sendActionBar(miniMessage.deserialize(message));
    }

    private String formatSeconds(long milliseconds) {
        double seconds =
                milliseconds / 1000.0;

        if (seconds >= 10.0) {
            return String.format(
                    "%.0f",
                    Math.ceil(seconds)
            );
        }

        return String.format(
                "%.1f",
                seconds
        );
    }
}