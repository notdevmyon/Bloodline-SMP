package com.notdev.bloodline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class BloodlineAwakening {

    private static final int BAR_SEGMENTS = 10;
    private static final long UPDATE_PERIOD_TICKS = 20L;

    private final BloodlineSMP plugin;
    private final Consumer<UUID> completionHandler;

    private final Map<UUID, AwakeningState> awakenings =
            new HashMap<>();

    private BukkitTask updateTask;

    public BloodlineAwakening(
            BloodlineSMP plugin,
            Consumer<UUID> completionHandler
    ) {
        this.plugin = plugin;
        this.completionHandler = completionHandler;
    }

    public void start(Player player, long durationMillis) {
        if (player == null || durationMillis <= 0L) {
            return;
        }

        start(
                player.getUniqueId(),
                System.currentTimeMillis(),
                durationMillis
        );
    }

    public void start(
            UUID uuid,
            long startMillis,
            long durationMillis
    ) {
        if (uuid == null || durationMillis <= 0L) {
            return;
        }

        if (startMillis > Long.MAX_VALUE - durationMillis) {
            return;
        }

        if (awakenings.containsKey(uuid)) {
            return;
        }

        long expiryMillis =
                startMillis + durationMillis;

        awakenings.put(
                uuid,
                new AwakeningState(
                        startMillis,
                        expiryMillis
                )
        );

        startTaskIfNeeded();

        Player player =
                plugin.getServer().getPlayer(uuid);

        if (player != null && player.isOnline()) {
            updateBar(
                    player,
                    awakenings.get(uuid),
                    System.currentTimeMillis()
            );
        }
    }

    public void restore(
            UUID uuid,
            long startMillis,
            long expiryMillis
    ) {
        if (uuid == null
                || startMillis <= 0L
                || expiryMillis <= startMillis) {
            return;
        }

        if (awakenings.containsKey(uuid)) {
            return;
        }

        awakenings.put(
                uuid,
                new AwakeningState(
                        startMillis,
                        expiryMillis
                )
        );

        startTaskIfNeeded();

        Player player =
                plugin.getServer().getPlayer(uuid);

        if (player != null && player.isOnline()) {
            long now = System.currentTimeMillis();

            if (expiryMillis > now) {
                updateBar(
                        player,
                        awakenings.get(uuid),
                        now
                );
            }
        }
    }

    public boolean isAwakening(UUID uuid) {
        return uuid != null
                && awakenings.containsKey(uuid);
    }

    public long getStartTime(UUID uuid) {
        AwakeningState state =
                awakenings.get(uuid);

        return state == null
                ? 0L
                : state.startMillis();
    }

    public long getExpiry(UUID uuid) {
        AwakeningState state =
                awakenings.get(uuid);

        return state == null
                ? 0L
                : state.expiryMillis();
    }

    public long getDuration(UUID uuid) {
        AwakeningState state =
                awakenings.get(uuid);

        if (state == null) {
            return 0L;
        }

        return state.expiryMillis()
                - state.startMillis();
    }

    public void cancel(UUID uuid) {
        if (uuid == null) {
            return;
        }

        awakenings.remove(uuid);

        stopTaskIfEmpty();
    }

    public void cancelAll() {
        awakenings.clear();

        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    public int getPendingCount() {
        return awakenings.size();
    }

    private void startTaskIfNeeded() {
        if (updateTask != null) {
            return;
        }

        updateTask =
                plugin.getServer()
                        .getScheduler()
                        .runTaskTimer(
                                plugin,
                                this::updatePlayers,
                                0L,
                                UPDATE_PERIOD_TICKS
                        );
    }

    private void updatePlayers() {
        if (awakenings.isEmpty()) {
            stopTaskIfEmpty();
            return;
        }

        long now = System.currentTimeMillis();

        List<UUID> completed =
                new ArrayList<>();

        for (Map.Entry<UUID, AwakeningState> entry
                : awakenings.entrySet()) {

            UUID uuid = entry.getKey();
            AwakeningState state = entry.getValue();

            if (state.expiryMillis() <= now) {
                completed.add(uuid);
                continue;
            }

            Player player =
                    plugin.getServer().getPlayer(uuid);

            if (player == null || !player.isOnline()) {
                continue;
            }

            updateBar(
                    player,
                    state,
                    now
            );
        }

        if (completed.isEmpty()) {
            return;
        }

        for (UUID uuid : completed) {
            awakenings.remove(uuid);
        }

        stopTaskIfEmpty();

        if (completionHandler != null) {
            for (UUID uuid : completed) {
                completionHandler.accept(uuid);
            }
        }
    }

    private void updateBar(
            Player player,
            AwakeningState state,
            long now
    ) {
        if (!plugin.getConfig().getBoolean("settings.awakening-bar", true)) {
            return;
        }

        long totalDuration =
                state.expiryMillis()
                        - state.startMillis();

        long elapsed =
                Math.max(
                        0L,
                        now - state.startMillis()
                );

        double progress =
                Math.min(
                        1.0D,
                        (double) elapsed
                                / (double) totalDuration
                );

        int filledSegments = Math.min(
                BAR_SEGMENTS,
                (int) Math.floor(progress * BAR_SEGMENTS)
        );

        NamedTextColor color = getProgressColor(progress);
        StringBuilder bar = new StringBuilder(BAR_SEGMENTS);

        for (int i = 0; i < filledSegments; i++) {
            bar.append("■");
        }
        for (int i = filledSegments; i < BAR_SEGMENTS; i++) {
            bar.append("□");
        }

        player.sendActionBar(
                Component.text(
                        "✦ " + bar + " ✦",
                        color
                )
        );
    }

    private NamedTextColor getProgressColor(
            double progress
    ) {
        if (progress >= 0.90D) {
            return NamedTextColor.RED;
        }

        if (progress >= 0.60D) {
            return NamedTextColor.GOLD;
        }

        if (progress >= 0.30D) {
            return NamedTextColor.YELLOW;
        }

        return NamedTextColor.GREEN;
    }

    private void stopTaskIfEmpty() {
        if (!awakenings.isEmpty()) {
            return;
        }

        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private record AwakeningState(
            long startMillis,
            long expiryMillis
    ) {
    }
}
