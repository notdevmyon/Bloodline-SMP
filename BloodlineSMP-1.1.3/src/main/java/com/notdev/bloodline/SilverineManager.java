package com.notdev.bloodline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public final class SilverineManager implements Listener {
    private final BloodlineSMP plugin;
    private final BloodlineManager bloodlines;
    private final NamespacedKey speedKey, jumpKey, healthKey, damageKey, auraSpeedKey, auraJumpKey, stormCooldownKey;
    private final Set<UUID> particleEnabled = new HashSet<>();
    private final Set<UUID> auraOwners = new HashSet<>();
    private final Map<UUID, Set<UUID>> auraAffected = new HashMap<>();
    private final Map<UUID, AffectedState> affected = new HashMap<>();
    private final Map<UUID, BukkitTask> storms = new HashMap<>();
    private final Map<UUID, Long> stormCooldowns = new HashMap<>();
    private BukkitTask particleTask, auraTask;
    private int particlePulse = 0;
    private long giveAllCooldownUntil;

    public SilverineManager(BloodlineSMP plugin, BloodlineManager bloodlines) {
        this.plugin = plugin;
        this.bloodlines = bloodlines;
        speedKey = new NamespacedKey(plugin, "mayon_speed");
        jumpKey = new NamespacedKey(plugin, "mayon_jump");
        healthKey = new NamespacedKey(plugin, "mayon_health");
        damageKey = new NamespacedKey(plugin, "mayon_damage");
        auraSpeedKey = new NamespacedKey(plugin, "silvering_aura_speed");
        auraJumpKey = new NamespacedKey(plugin, "silvering_aura_jump");
        stormCooldownKey = new NamespacedKey(plugin, "silverine_storm_cooldown_until");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void initializeOnline() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sweepAuraModifiers(player);
            if (isMayon(player)) applyMayonBuffs(player);
        }
        plugin.getLogger().info("Silvering Aura is session-only; all auras start OFF after restart.");
    }

    public boolean isMayon(Player player) {
        return player != null && bloodlines.getBloodline(player.getUniqueId()) == Bloodline.MAYON;
    }

    public void refreshMayon(Player player) {
        if (player == null) return;
        removeMayonBuffs(player);
        if (isMayon(player)) {
            applyMayonBuffs(player);
            if (plugin.getConfig().getBoolean("silverine.permanent-particles.enabled", true)
                    && particleEnabled(player.getUniqueId())) ensureParticleTask();
        } else {
            stopStorm(player.getUniqueId());
            stopAura(player.getUniqueId(), "bloodline changed");
        }
    }

    private void applyMayonBuffs(Player p) {
        addModifier(p, Attribute.MOVEMENT_SPEED, speedKey, configDouble("mayon.buffs.movement-speed", 0.15, 0, 0.5));
        addModifier(p, Attribute.JUMP_STRENGTH, jumpKey, configDouble("mayon.buffs.jump-strength", 0.15, 0, 0.5));
        addModifier(p, Attribute.MAX_HEALTH, healthKey, configDouble("mayon.buffs.max-health-bonus", 8, 0, 20));
        addModifier(p, Attribute.ATTACK_DAMAGE, damageKey, configDouble("mayon.buffs.attack-damage-bonus", 4, 0, 8));
        AttributeInstance health = p.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) p.setHealth(Math.min(p.getHealth(), Math.min(40.0, health.getValue())));
    }

    private void addModifier(Player p, Attribute attr, NamespacedKey key, double amount) {
        AttributeInstance instance = p.getAttribute(attr);
        if (instance == null) return;
        removeModifier(instance, key);
        String preset = String.valueOf(plugin.getConfig().getString("mayon.power-level", "strong"));
        double scale = switch (preset.toLowerCase(Locale.ROOT)) {
            case "balanced" -> 0.75;
            case "ultra" -> 1.5;
            case "absurd" -> 2.0;
            default -> 1.0;
        };
        double value = Math.max(-0.9, Math.min(20, amount * scale));
        if (!Double.isFinite(value)) value = 0;
        instance.addTransientModifier(new AttributeModifier(key, value,
                attr == Attribute.MOVEMENT_SPEED || attr == Attribute.JUMP_STRENGTH
                        ? AttributeModifier.Operation.MULTIPLY_SCALAR_1 : AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.ANY));
    }

    private void removeMayonBuffs(Player p) {
        for (Attribute a : List.of(Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH, Attribute.MAX_HEALTH, Attribute.ATTACK_DAMAGE)) {
            AttributeInstance i = p.getAttribute(a);
            if (i != null) for (NamespacedKey key : List.of(speedKey, jumpKey, healthKey, damageKey)) removeModifier(i, key);
        }
        AttributeInstance health = p.getAttribute(Attribute.MAX_HEALTH);
        if (health != null && p.getHealth() > health.getValue()) p.setHealth(Math.max(1.0, health.getValue()));
    }

    private void removeModifier(AttributeInstance i, NamespacedKey key) {
        for (AttributeModifier m : new ArrayList<>(i.getModifiers())) if (m.getKey().equals(key)) i.removeModifier(m);
    }

    public void toggleParticles(Player player, boolean enabled) {
        bloodlines.setSilverineParticlesVisible(player.getUniqueId(), enabled);
        player.sendMessage(Component.text("Mayon particles for you: " + (enabled ? "shown" : "hidden"), NamedTextColor.GRAY));
    }

    public boolean particleEnabled(UUID id) { return bloodlines.isSilverineParticlesVisible(id); }

    public void setHolderParticles(Player target, boolean enabled) {
        if (!isMayon(target)) return;
        if (enabled) {
            target.getPersistentDataContainer().set(new NamespacedKey(plugin, "silverine_particles"), PersistentDataType.BYTE, (byte) 1);
        } else target.getPersistentDataContainer().set(new NamespacedKey(plugin, "silverine_particles"), PersistentDataType.BYTE, (byte) 0);
        if (enabled) ensureParticleTask();
        target.sendMessage(Component.text("Silverine particles " + (enabled ? "enabled." : "disabled."), NamedTextColor.GRAY));
    }

    private boolean holderParticles(Player p) {
        Byte enabled = p.getPersistentDataContainer().get(new NamespacedKey(plugin, "silverine_particles"), PersistentDataType.BYTE);
        return enabled == null || enabled == (byte) 1;
    }

    private void ensureParticleTask() {
        if (particleTask != null || !plugin.getConfig().getBoolean("silverine.permanent-particles.enabled", true)) return;
        int period = Math.max(2, Math.min(20, plugin.getConfig().getInt("silverine.permanent-particles.period-ticks", 5)));
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::particlePulse, 1L, period);
    }

    private void particlePulse() {
        int maxPer = clamp(plugin.getConfig().getInt("silverine.permanent-particles.max-per-holder-per-pulse", 10), 1, 80);
        int maxTotal = clamp(plugin.getConfig().getInt("silverine.permanent-particles.max-total-per-pulse", 100), 1, 500);
        double view = clamp(plugin.getConfig().getDouble("silverine.permanent-particles.view-distance", 48), 8, 128);
        int total = 0;
        boolean any = false;
        for (Player holder : Bukkit.getOnlinePlayers()) {
            if (!isMayon(holder) || !holderParticles(holder) || holder.getGameMode() == GameMode.SPECTATOR
                    || holder.isDead() || !holder.isValid() || !holder.getWorld().isChunkLoaded(holder.getLocation().getBlockX() >> 4, holder.getLocation().getBlockZ() >> 4)) continue;
            any = true;
            Location base = holder.getLocation().clone();
            int count = Math.min(maxPer, Math.max(1, switch (String.valueOf(plugin.getConfig().getString("silverine.permanent-particles.intensity", "medium")).toLowerCase(Locale.ROOT)) {
                case "low" -> 3; case "high" -> 12; case "ultra" -> 18; default -> 7;
            }));
            for (Player viewer : holder.getWorld().getPlayers()) {
                if (total >= maxTotal) break;
                if (!particleEnabled(viewer.getUniqueId()) || viewer.getLocation().distanceSquared(base) > view * view) continue;
                for (int i = 0; i < count && total + 2 <= maxTotal; i++, total += 2) {
                    double angle = (particlePulse * 0.22) + (Math.PI * 2 * i / count);
                    double angle2 = angle * 1.55 + Math.PI;
                    // Two offset orbital ribbons create a layered silver halo instead
                    // of a single flat ring. All particles are client-side visuals.
                    Location upper = base.clone().add(Math.cos(angle) * 0.78,
                            1.15 + Math.sin(angle * 1.7) * 0.32, Math.sin(angle) * 0.78);
                    Location lower = base.clone().add(Math.cos(angle2) * 0.48,
                            0.48 + Math.sin(angle2 * 1.3) * 0.22, Math.sin(angle2) * 0.48);
                    Color silver = (i % 3 == 0) ? Color.fromRGB(225, 235, 255)
                            : (i % 3 == 1) ? Color.fromRGB(190, 205, 235)
                            : Color.fromRGB(245, 245, 255);
                    viewer.spawnParticle(Particle.DUST, upper, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(silver, 1.15f));
                    viewer.spawnParticle(Particle.END_ROD, lower, 1, 0, 0, 0, 0);
                    if (particlePulse % 4 == 0 && i % 3 == 0 && total < maxTotal) {
                        viewer.spawnParticle(Particle.ELECTRIC_SPARK, upper, 1, 0.025, 0.025, 0.025, 0.01);
                        total++;
                    }
                }
            }
        }
        particlePulse++;
        if (!any || total == 0 && Bukkit.getOnlinePlayers().isEmpty()) {
            particleTask.cancel(); particleTask = null;
        }
    }

    public boolean startStorm(Player holder) {
        if (!isMayon(holder) || holder.isDead() || !holder.isOnline()) return false;
        long now = System.currentTimeMillis();
        long ready = Math.max(stormCooldowns.getOrDefault(holder.getUniqueId(), 0L),
                holder.getPersistentDataContainer().getOrDefault(stormCooldownKey, PersistentDataType.LONG, 0L));
        if (ready > now) {
            holder.sendMessage(Component.text("Silverine Storm cooldown: " + Math.max(1, (ready - now + 999) / 1000) + "s", NamedTextColor.RED));
            return false;
        }
        stopStorm(holder.getUniqueId());
        int duration = clamp(plugin.getConfig().getInt("silverine.storm.duration-seconds", 5), 1, 15);
        int radius = clamp(plugin.getConfig().getInt("silverine.storm.radius", 6), 2, 12);
        int maxParticles = clamp(plugin.getConfig().getInt("silverine.storm.max-particles-per-pulse", 40), 2, 100);
        long end = now + duration * 1000L;
        holder.getWorld().playSound(holder.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.7f);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int tick;
            @Override public void run() {
                if (!holder.isOnline() || holder.isDead() || !isMayon(holder) || !holder.isValid()
                        || !holder.getWorld().isChunkLoaded(holder.getLocation().getBlockX() >> 4, holder.getLocation().getBlockZ() >> 4)
                        || System.currentTimeMillis() >= end) {
                    stopStorm(holder.getUniqueId()); return;
                }
                Location center = holder.getLocation().clone();
                int n = Math.max(1, Math.min(50, maxParticles / 2));
                for (int i = 0; i < n; i++) {
                    double a = tick * 0.38 + (Math.PI * 2 * i / n);
                    double y = (tick % 60) / 10.0;
                    Location l = center.clone().add(Math.cos(a) * (0.5 + y * 0.15), y, Math.sin(a) * (0.5 + y * 0.15));
                    holder.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
                }
                if (tick % 4 == 0) {
                    for (int i = 0; i < n; i++) {
                        double a = Math.PI * 2 * i / n;
                        Location l = center.clone().add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius);
                        holder.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(220, 220, 235), 1.0f));
                    }
                }
                if (plugin.getConfig().getBoolean("silverine.storm.storm-damage", false) && tick % 4 == 0) {
                    int targets = 0;
                    double damage = Math.max(0, Math.min(4, plugin.getConfig().getDouble("silverine.storm.damage", 1.0)));
                    for (Entity e : holder.getNearbyEntities(radius, radius, radius)) {
                        if (targets >= clamp(plugin.getConfig().getInt("silverine.storm.max-targets", 6), 1, 12)) break;
                        if (!(e instanceof LivingEntity target) || target == holder || target.isDead() || !target.isValid()) continue;
                        if (target instanceof Player p && isMayon(p)) continue;
                        target.setMetadata("bloodlinesmp_ability_damage", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                        try { target.damage(damage, holder); } finally { target.removeMetadata("bloodlinesmp_ability_damage", plugin); }
                        Vector kb = target.getLocation().toVector().subtract(center.toVector());
                        if (kb.lengthSquared() > 0.01) target.setVelocity(kb.normalize().multiply(0.35).setY(0.18));
                        targets++;
                    }
                }
                tick++;
            }
        }, 1L, 2L);
        storms.put(holder.getUniqueId(), task);
        long cooldownUntil = now + Math.max(5, plugin.getConfig().getInt("silverine.storm.cooldown-seconds", 30)) * 1000L;
        stormCooldowns.put(holder.getUniqueId(), cooldownUntil);
        holder.getPersistentDataContainer().set(stormCooldownKey, PersistentDataType.LONG, cooldownUntil);
        return true;
    }

    private void stopStorm(UUID id) {
        BukkitTask task = storms.remove(id);
        if (task == null) return;
        task.cancel();
        Player p = Bukkit.getPlayer(id);
        if (p != null && p.isOnline()) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.8f);
    }

    public boolean startAura(Player owner) {
        if (!isMayon(owner) || !owner.isOnline() || owner.isDead()) return false;
        UUID id = owner.getUniqueId();
        if (!auraOwners.add(id)) return false;
        auraAffected.put(id, new HashSet<>());
        plugin.getLogger().info("Silvering Aura START owner=" + owner.getName() + " radius=" + auraRadius() + " affected=0");
        ensureAuraTask();
        return true;
    }

    public void stopAura(UUID ownerId, String reason) {
        if (!auraOwners.remove(ownerId)) return;
        Set<UUID> ids = auraAffected.remove(ownerId);
        if (ids != null) for (UUID id : new HashSet<>(ids)) release(id, true);
        Player owner = Bukkit.getPlayer(ownerId);
        plugin.getLogger().info("Silvering Aura STOP owner=" + (owner == null ? ownerId : owner.getName()) + " reason=" + reason + " released=" + (ids == null ? 0 : ids.size()));
        if (auraOwners.isEmpty() && auraTask != null) { auraTask.cancel(); auraTask = null; }
    }

    public void stopAllAuras() {
        for (UUID id : new HashSet<>(auraOwners)) stopAura(id, "admin/off all");
    }

    private double auraRadius() { return clamp(plugin.getConfig().getDouble("silverine.aura.radius", 10), 1, 32); }
    private void ensureAuraTask() {
        if (auraTask != null) return;
        int period = clamp(plugin.getConfig().getInt("silverine.aura.period-ticks", 6), 5, 10);
        auraTask = Bukkit.getScheduler().runTaskTimer(plugin, this::auraPulse, 1L, period);
    }

    private void auraPulse() {
        if (auraOwners.isEmpty()) { if (auraTask != null) auraTask.cancel(); auraTask = null; return; }
        Set<UUID> seen = new HashSet<>();
        for (UUID ownerId : new HashSet<>(auraOwners)) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || !owner.isOnline() || owner.isDead() || !isMayon(owner)
                    || !owner.getWorld().isChunkLoaded(owner.getLocation().getBlockX() >> 4, owner.getLocation().getBlockZ() >> 4)) {
                stopAura(ownerId, "owner unavailable"); continue;
            }
            Set<UUID> current = auraAffected.computeIfAbsent(ownerId, k -> new HashSet<>());
            double r = auraRadius(), r2 = r * r;
            for (Player victim : owner.getWorld().getPlayers()) {
                if (victim == owner || exempt(victim) || victim.getLocation().distanceSquared(owner.getLocation()) > r2) continue;
                seen.add(victim.getUniqueId());
                if (affected.containsKey(victim.getUniqueId())) continue;
                capture(owner, victim);
                current.add(victim.getUniqueId());
            }
            current.removeIf(id -> {
                Player victim = Bukkit.getPlayer(id);
                if (victim == null || !victim.isOnline() || victim.getWorld() != owner.getWorld()
                        || victim.getLocation().distanceSquared(owner.getLocation()) > r2 || exempt(victim)) {
                    release(id, true); return true;
                }
                return false;
            });
            for (UUID victimId : new HashSet<>(current)) {
                Player victim = Bukkit.getPlayer(victimId);
                AffectedState state = affected.get(victimId);
                if (victim == null || state == null || !victim.isOnline()) continue;
                double maxLift = clamp(plugin.getConfig().getDouble("silverine.aura.max-lift", 5.0), 1.0, 8.0);
                double ceiling = Math.min(victim.getWorld().getMaxHeight() - 1.0, state.capture.getY() + maxLift);
                Location vl = victim.getLocation();
                boolean ceilingBlocked = victim.getWorld().getBlockAt(vl.getBlockX(), vl.getBlockY() + 1, vl.getBlockZ()).getType().isSolid()
                        || victim.getWorld().getBlockAt(vl.getBlockX(), vl.getBlockY() + 2, vl.getBlockZ()).getType().isSolid();
                if (victim.getLocation().getY() >= ceiling || ceilingBlocked) {
                    victim.setVelocity(new Vector(0, 0, 0));
                } else {
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 12, 0, false, false, true), false);
                    victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 12, 10, false, false, true), false);
                    victim.setVelocity(new Vector(0, Math.min(0.08, Math.max(0, victim.getVelocity().getY())), 0));
                }
            }
            int cap = clamp(plugin.getConfig().getInt("silverine.aura.max-border-particles", 24), 0, 48);
            if (cap > 0 && particleEnabled(ownerId)) {
                Location c = owner.getLocation();
                for (int i = 0; i < cap; i++) {
                    double a = 2 * Math.PI * i / cap;
                    owner.spawnParticle(Particle.END_ROD, c.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 1, 0, 0, 0, 0);
                }
            }
        }
        // If a victim was captured by an owner that was removed this pulse, release it.
        for (UUID id : new HashSet<>(affected.keySet())) {
            if (!seen.contains(id)) {
                AffectedState st = affected.get(id);
                if (st != null && (Bukkit.getPlayer(st.owner) == null || !auraOwners.contains(st.owner))) release(id, true);
            }
        }
    }

    private boolean exempt(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR || p.hasPermission("bloodline.admin") || isMayon(p)) return true;
        if (plugin.getConfig().getBoolean("silverine.aura.exempt-creative", true) && p.getGameMode() == GameMode.CREATIVE) return true;
        for (String name : plugin.getConfig().getStringList("silverine.aura.exempt-players")) if (name.equalsIgnoreCase(p.getName())) return true;
        return false;
    }

    private void capture(Player owner, Player victim) {
        UUID id = victim.getUniqueId();
        AffectedState state = new AffectedState(owner.getUniqueId(), victim.getAllowFlight(), victim.isFlying(), victim.getLocation().clone());
        affected.put(id, state);
        if (victim.isInsideVehicle()) victim.leaveVehicle();
        victim.setFlying(false); victim.setAllowFlight(false);
        addModifier(victim, Attribute.MOVEMENT_SPEED, auraSpeedKey, -0.99);
        addModifier(victim, Attribute.JUMP_STRENGTH, auraJumpKey, -0.99);
        state.oldLevitation = victim.getPotionEffect(PotionEffectType.LEVITATION);
        state.oldSlowness = victim.getPotionEffect(PotionEffectType.SLOWNESS);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 12, 0, false, false, true), false);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 12, 10, false, false, true), false);
        victim.setVelocity(new Vector(0, 0.08, 0));
        victim.sendActionBar(Component.text("You are caught in the Silvering Aura", NamedTextColor.GRAY));
        victim.showTitle(net.kyori.adventure.title.Title.title(Component.text("SILVERING AURA", NamedTextColor.WHITE),
                Component.text("You are caught in the Silvering Aura", NamedTextColor.GRAY)));
    }

    private void release(UUID id, boolean safeFall) {
        release(id, Bukkit.getPlayer(id), safeFall);
    }

    private void release(UUID id, Player p, boolean safeFall) {
        AffectedState state = affected.remove(id);
        if (state == null) return;
        for (Set<UUID> set : auraAffected.values()) set.remove(id);
        if (p == null) return;
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED); if (speed != null) removeModifier(speed, auraSpeedKey);
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH); if (jump != null) removeModifier(jump, auraJumpKey);
        PotionEffect levitation = p.getPotionEffect(PotionEffectType.LEVITATION);
        if (levitation != null && levitation.getAmplifier() == 0 && levitation.getDuration() <= 12) p.removePotionEffect(PotionEffectType.LEVITATION);
        PotionEffect slowness = p.getPotionEffect(PotionEffectType.SLOWNESS);
        if (slowness != null && slowness.getAmplifier() == 10 && slowness.getDuration() <= 12) p.removePotionEffect(PotionEffectType.SLOWNESS);
        if (state.oldLevitation != null && (p.getPotionEffect(PotionEffectType.LEVITATION) == null || state.oldLevitation.getAmplifier() > p.getPotionEffect(PotionEffectType.LEVITATION).getAmplifier())) p.addPotionEffect(state.oldLevitation, true);
        if (state.oldSlowness != null && (p.getPotionEffect(PotionEffectType.SLOWNESS) == null || state.oldSlowness.getAmplifier() > p.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier())) p.addPotionEffect(state.oldSlowness, true);
        if (safeFall) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, false, true));
        p.setAllowFlight(state.allowFlight);
        if (state.allowFlight && state.flying && p.getGameMode() != GameMode.SPECTATOR) {
            try { p.setFlying(true); } catch (IllegalArgumentException ignored) { }
        }
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (!affected.containsKey(e.getPlayer().getUniqueId()) || e.getTo() == null) return;
        Location from = e.getFrom(), to = e.getTo();
        if (from.getX() != to.getX() || from.getZ() != to.getZ() || from.getY() > to.getY()) {
            e.setTo(new Location(from.getWorld(), from.getX(), to.getY(), from.getZ(), to.getYaw(), to.getPitch()));
        }
        Player p = e.getPlayer();
        if (p.getVelocity().getY() > 0.12) p.setVelocity(new Vector(0, 0.08, 0));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void onTeleport(PlayerTeleportEvent e) {
        if (!affected.containsKey(e.getPlayer().getUniqueId())) return;
        if (plugin.getConfig().getBoolean("silverine.aura.cancel-victim-teleports", true)) e.setCancelled(true);
        else release(e.getPlayer().getUniqueId(), true);
    }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e) { release(e.getPlayer().getUniqueId(), true); }
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        if (auraOwners.contains(id)) stopAura(id, "owner died");
        release(id, false);
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        if (auraOwners.contains(id)) stopAura(id, "owner quit");
        release(id, e.getPlayer(), false);
        stopStorm(id);
        particleEnabled.remove(id);
        removeMayonBuffs(e.getPlayer());
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) { sweepAuraModifiers(e.getPlayer()); refreshMayon(e.getPlayer()); }
    @EventHandler public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && affected.containsKey(p.getUniqueId())
                && plugin.getConfig().getBoolean("silverine.aura.victims-invulnerable", false)) e.setCancelled(true);
        if (e.getEntity() instanceof Player p && p.getUniqueId().equals(e.getEntity().getUniqueId())
                && e.getCause() == EntityDamageEvent.DamageCause.FALL && affected.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    private void sweepAuraModifiers(Player p) {
        for (Attribute a : List.of(Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH)) {
            AttributeInstance i = p.getAttribute(a);
            if (i != null) for (AttributeModifier m : new ArrayList<>(i.getModifiers()))
                if (m.getKey().equals(auraSpeedKey) || m.getKey().equals(auraJumpKey)) i.removeModifier(m);
        }
    }

    public void reloadCleanup() {
        for (UUID id : new HashSet<>(auraOwners)) stopAura(id, "reload");
        for (UUID id : new HashSet<>(storms.keySet())) stopStorm(id);
        for (UUID id : new HashSet<>(affected.keySet())) release(id, true);
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
        if (auraTask != null) { auraTask.cancel(); auraTask = null; }
        for (Player p : Bukkit.getOnlinePlayers()) refreshMayon(p);
    }

    public void shutdown() {
        reloadCleanup();
        for (Player p : Bukkit.getOnlinePlayers()) { removeMayonBuffs(p); sweepAuraModifiers(p); }
    }

    public void clearPlayer(UUID id) {
        stopStorm(id);
        stopAura(id, "cleanup");
        release(id, false);
    }

    public boolean giveCoinAll(CommandSender sender, int amount, BloodlineItem item) {
        int max = clamp(plugin.getConfig().getInt("givecoinall.max-amount", 16), 1, 64);
        if (amount < 1 || amount > max) { sender.sendMessage(Component.text("Amount must be between 1 and " + max + ".", NamedTextColor.RED)); return false; }
        long now = System.currentTimeMillis();
        if (now < giveAllCooldownUntil) { sender.sendMessage(Component.text("Wait before using /bl givecoinall again.", NamedTextColor.RED)); return false; }
        giveAllCooldownUntil = now + 10_000L;
        List<Player> snapshot = new ArrayList<>(Bukkit.getOnlinePlayers());
        boolean spectators = plugin.getConfig().getBoolean("givecoinall.include-spectators", false);
        int received = 0, total = 0;
        for (Player p : snapshot) {
            if (p == null || !p.isOnline() || (!spectators && p.getGameMode() == GameMode.SPECTATOR)) continue;
            int left = amount;
            while (left > 0) {
                int stack = Math.min(left, item.createCoin().getMaxStackSize());
                Map<Integer, org.bukkit.inventory.ItemStack> leftovers = p.getInventory().addItem(item.createCoinAmount(stack));
                for (org.bukkit.inventory.ItemStack drop : leftovers.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
                left -= stack;
            }
            received++; total += amount;
        }
        plugin.getLogger().info("givecoinall by " + sender.getName() + ": gave " + total + " Blood Coins to " + received + " player(s).");
        sender.sendMessage(Component.text("Gave " + amount + " Blood Coin(s) to " + received + " player(s).", NamedTextColor.GREEN));
        return true;
    }

    private double configDouble(String path, double def, double min, double max) {
        double v = plugin.getConfig().getDouble(path, def);
        return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : def;
    }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static double clamp(double v, double min, double max) { return Double.isFinite(v) ? Math.max(min, Math.min(max, v)) : min; }

    private static final class AffectedState {
        final UUID owner; final boolean allowFlight, flying; final Location capture;
        PotionEffect oldLevitation, oldSlowness;
        AffectedState(UUID owner, boolean allowFlight, boolean flying, Location capture) {
            this.owner=owner; this.allowFlight=allowFlight; this.flying=flying; this.capture=capture;
        }
    }
}
