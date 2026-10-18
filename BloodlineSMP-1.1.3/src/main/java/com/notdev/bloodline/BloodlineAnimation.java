package com.notdev.bloodline;

import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Short, self-cancelling title roulette. Assignment is completed before this visual begins. */
public final class BloodlineAnimation {
    private static final int ROLL_FRAMES = 18;
    private final BloodlineSMP plugin;
    private final Map<UUID, BukkitTask> active = new HashMap<>();

    public BloodlineAnimation(BloodlineSMP plugin) {
        this.plugin = plugin;
    }

    public void start(Player player, Bloodline bloodline, Bloodline previous) {
        if (player == null || !player.isOnline() || bloodline == null
                || !plugin.getConfig().getBoolean("settings.reveal-animation", true)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        cancel(uuid);
        List<Bloodline> pool = new ArrayList<>();
        for (Bloodline candidate : Bloodline.values()) {
            if (candidate.isObtainable() || candidate == Bloodline.MAYON && candidate == bloodline) {
                pool.add(candidate);
            }
        }
        if (!pool.contains(bloodline)) pool.add(bloodline);

        BukkitRunnable animation = new BukkitRunnable() {
            private int frame;

            @Override
            public void run() {
                Player current = plugin.getServer().getPlayer(uuid);
                if (current == null || !current.isOnline()) {
                    active.remove(uuid);
                    cancel();
                    return;
                }

                boolean finished = frame >= ROLL_FRAMES;
                Bloodline shown = finished ? bloodline : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                Component title = finished
                        ? Component.text("✦ BLOODLINE AWAKENED ✦", NamedTextColor.GOLD)
                        : Component.text("✧ BLOODLINE ROLLING ✧", NamedTextColor.DARK_PURPLE);
                Component subtitle = Component.text(shown.getDisplayName(), shown.getColor());
                current.showTitle(Title.title(title, subtitle,
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(finished ? 1800 : 500), Duration.ofMillis(100))));

                if (plugin.getConfig().getBoolean("visuals.particles", true)) {
                    Particle particle = switch (shown) {
                        case FROSTBORN, TIDEBORN -> Particle.SNOWFLAKE;
                        case DEMON, PHOENIX, DRAGONBORN -> Particle.FLAME;
                        case VOIDBORN, SHADOW -> Particle.PORTAL;
                        case CELESTIAL, ARCANE -> Particle.ENCHANT;
                        case STORMBORN -> Particle.ELECTRIC_SPARK;
                        default -> Particle.END_ROD;
                    };
                    current.getWorld().spawnParticle(particle,
                            current.getLocation().add(0.0D, 1.0D, 0.0D),
                            finished ? 28 : 8, 0.35D, 0.55D, 0.35D, 0.02D);
                }

                if (plugin.getConfig().getBoolean("visuals.sounds", true)) {
                    current.playSound(current.getLocation(), finished ? Sound.UI_TOAST_CHALLENGE_COMPLETE
                                    : Sound.BLOCK_NOTE_BLOCK_CHIME,
                            finished ? 0.9F : 0.45F,
                            finished ? 1.0F : 0.65F + (frame % 8) * 0.12F);
                }

                if (finished) {
                    active.remove(uuid);
                    cancel();
                    return;
                }
                frame++;
            }
        };

        BukkitTask task = animation.runTaskTimer(plugin, 0L, 2L);
        active.put(uuid, task);
    }

    public boolean isAnimating(UUID uuid) {
        BukkitTask task = uuid == null ? null : active.get(uuid);
        return task != null && !task.isCancelled();
    }

    public void cancel(UUID uuid) {
        if (uuid == null) return;
        BukkitTask task = active.remove(uuid);
        if (task != null) task.cancel();
    }

    public void cancelAll() {
        for (BukkitTask task : active.values()) task.cancel();
        active.clear();
    }
}
