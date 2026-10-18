package com.notdev.bloodline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class BloodlineCommand implements CommandExecutor, TabCompleter {
    private final BloodlineManager manager;
    private final BloodlineListener listener;
    private final BloodlineItem item;
    private final BloodlineAnimation animation;
    private final BloodlineAwakening awakening;

    public BloodlineCommand(BloodlineManager manager, BloodlineListener listener, BloodlineItem item, BloodlineAnimation animation, BloodlineAwakening awakening) {
        this.manager = manager;
        this.listener = listener;
        this.item = item;
        this.animation = animation;
        this.awakening = awakening;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player && !sender.hasPermission("bloodline.use") && !sender.hasPermission("bloodline.admin")) {
            sender.sendMessage(Component.text("You do not have permission to use Bloodline commands.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "info" -> showInfo(sender, args);
            case "ability" -> abilityCommand(sender, args);
            case "list" -> showList(sender);
            case "help" -> sendHelp(sender);
            case "set" -> setBloodline(sender, args);
            case "reset" -> resetBloodline(sender, args);
            case "reroll" -> rerollBloodline(sender, args);
            case "give" -> giveItem(sender, args);
            case "givesigil" -> giveSigil(sender, args);
            case "mayon" -> mayonCommand(sender, args);
            case "particles" -> particlesCommand(sender, args);
            case "givecoinall" -> giveCoinAll(sender, args);
            case "reload" -> reloadPlugin(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void abilityCommand(CommandSender sender, String[] args) {
        if (!manager.getPlugin().getConfig().getBoolean("abilities.command.enabled", true)) {
            sender.sendMessage(Component.text("The /bl ability command is disabled.", NamedTextColor.RED));
            return;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage(Component.text("This command is for players.", NamedTextColor.RED)); return; }
        if (args.length == 1) {
            if (!manager.getPlugin().getConfig().getBoolean("abilities.enabled", true)) {
                player.sendActionBar(Component.text("Bloodline abilities are disabled.", NamedTextColor.RED));
                return;
            }
            if (manager.getPlugin().getConfig().getBoolean("abilities.command.require-sigil", false)
                    && !item.hasSigilInInventory(player)) {
                player.sendActionBar(Component.text("You must carry a Bloodline Sigil to use this command.", NamedTextColor.RED));
                return;
            }
            listener.activateAbility(player);
            return;
        }
        if (!requireAdmin(sender)) return;
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(Component.text("That player is not online.", NamedTextColor.RED)); return; }
        if (args.length > 2 && args[2].equalsIgnoreCase("force")) listener.clearAbilityCooldown(target);
        listener.activateAbility(target);
    }

    private void mayonCommand(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        SilverineManager silverine = manager.getPlugin().getSilverineManager();
        if (silverine == null || args.length < 2) {
            sender.sendMessage(Component.text("Usage: /bl mayon <storm|particles|aura> ...", NamedTextColor.RED));
            return;
        }
        String sub = args[1].toLowerCase(Locale.ROOT);
        if (sub.equals("aura") && args.length >= 4 && args[2].equalsIgnoreCase("off") && args[3].equalsIgnoreCase("all")) {
            silverine.stopAllAuras();
            sender.sendMessage(Component.text("All Silvering Auras stopped.", NamedTextColor.GREEN));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /bl mayon storm <player> | particles <player> <on|off> | aura <player> <on|off>", NamedTextColor.RED));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[2]);
        if (target == null) {
            sender.sendMessage(Component.text("Target must be online.", NamedTextColor.RED));
            return;
        }
        if (!silverine.isMayon(target)) {
            sender.sendMessage(Component.text("That player must hold MAYON.", NamedTextColor.RED));
            return;
        }
        switch (sub) {
            case "storm" -> {
                if (silverine.startStorm(target)) sender.sendMessage(Component.text("Started Silverine Storm for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "particles" -> {
                if (args.length < 4) { sender.sendMessage(Component.text("Usage: /bl mayon particles <player> <on|off>", NamedTextColor.RED)); return; }
                boolean on = args[3].equalsIgnoreCase("on");
                if (!on && !args[3].equalsIgnoreCase("off")) { sender.sendMessage(Component.text("Use on or off.", NamedTextColor.RED)); return; }
                silverine.setHolderParticles(target, on);
                sender.sendMessage(Component.text("Updated Silverine particles for " + target.getName() + ".", NamedTextColor.GREEN));
            }
            case "aura" -> {
                if (args.length < 4) { sender.sendMessage(Component.text("Usage: /bl mayon aura <player> <on|off>", NamedTextColor.RED)); return; }
                if (args[3].equalsIgnoreCase("on")) {
                    if (silverine.startAura(target)) sender.sendMessage(Component.text("Silvering Aura started for " + target.getName() + ".", NamedTextColor.GREEN));
                    else sender.sendMessage(Component.text("That player's aura is already active.", NamedTextColor.YELLOW));
                } else if (args[3].equalsIgnoreCase("off")) {
                    silverine.stopAura(target.getUniqueId(), "admin command");
                    sender.sendMessage(Component.text("Silvering Aura stopped for " + target.getName() + ".", NamedTextColor.GREEN));
                } else sender.sendMessage(Component.text("Use on or off.", NamedTextColor.RED));
            }
            default -> sender.sendMessage(Component.text("Unknown Mayon subcommand.", NamedTextColor.RED));
        }
    }

    private void particlesCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command is for players.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            sender.sendMessage(Component.text("Usage: /bl particles <on|off>", NamedTextColor.RED));
            return;
        }
        manager.getPlugin().getSilverineManager().toggleParticles(player, args[1].equalsIgnoreCase("on"));
    }

    private void giveCoinAll(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        int amount = 1;
        if (args.length >= 2) {
            try { amount = Integer.parseInt(args[1]); }
            catch (NumberFormatException ex) { sender.sendMessage(Component.text("Amount must be a whole number.", NamedTextColor.RED)); return; }
        }
        manager.getPlugin().getSilverineManager().giveCoinAll(sender, amount, item);
    }

    private void reloadPlugin(CommandSender sender) {
        if (!requireAdmin(sender)) return;
        manager.getPlugin().getSilverineManager().reloadCleanup();
        manager.getPlugin().reloadConfig();
        item.registerRecipes();
        manager.getPlugin().getSilverineManager().reloadCleanup();
        sender.sendMessage(Component.text("BloodlineSMP configuration reloaded; active Silverine effects were cleaned up.", NamedTextColor.GREEN));
    }

    private void showInfo(CommandSender sender, String[] args) {
        Player target;

        if (args.length >= 2) {
            if (!requireAdmin(sender)) {
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
                return;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Component.text("Usage: /bloodline info <player>", NamedTextColor.RED));
            return;
        }

        Bloodline bloodline = manager.getBloodline(target.getUniqueId());
        if (bloodline == null) {
            sender.sendMessage(Component.text(target.getName() + " has not awakened a Bloodline yet.", NamedTextColor.YELLOW));
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text(target.getName() + "'s Bloodline", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("Bloodline: ", NamedTextColor.GRAY)
                .append(Component.text(bloodline.getDisplayName(), bloodline.getColor())));
        sender.sendMessage(Component.text("Rarity: ", NamedTextColor.GRAY)
                .append(Component.text(bloodline.getRarity(), NamedTextColor.WHITE)));

        sender.sendMessage(Component.text("Passive Powers", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        for (String passive : getPassiveDescriptions(bloodline)) {
            sender.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(passive, NamedTextColor.GRAY)));
        }

        sender.sendMessage(Component.text("Active Ability", NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text(" • ", NamedTextColor.DARK_GRAY)
                .append(Component.text(getAbilityDescription(bloodline), NamedTextColor.GRAY)));
        if (bloodline.hasAbility()) {
            sender.sendMessage(Component.text(" • Use a Bloodline Sigil or /bl ability", NamedTextColor.DARK_GRAY));
        }
        sender.sendMessage(Component.empty());
    }


    private List<String> getPassiveDescriptions(Bloodline bloodline) {
        return switch (bloodline) {
            case NORMAL -> List.of("No special passive; the baseline Bloodline.");
            case VAMPIRE -> List.of(
                    "Blood Leech: attacks can restore health; weaker during Overworld daytime.",
                    "Blood Hunger: slightly increased fire damage during daytime."
            );
            case FROSTBORN -> List.of(
                    "Frost Chain: repeated hits build up to 10 Frost stacks; stacks decay after a pause.",
                    "At maximum stacks, the target is heavily slowed and briefly weakened.",
                    "Frost weakness: takes increased fire-related damage."
            );
            case SHADOW -> List.of(
                    "Nightwalker: gains enhanced movement and Night Vision at Overworld night.",
                    "Shadow Veil: takes reduced damage during Overworld night.",
                    "Shadow Strike: attacks can deliver a shadow-themed combat burst."
            );
            case DEMON -> List.of(
                    "Netherborn: greatly reduces fire and lava damage, including damage from Blazes and Magma Cubes.",
                    "Abyssal Weakness: takes increased drowning damage.",
                    "Demon Strike: attacks can unleash a fire-themed strike."
            );
            case STORMBORN -> List.of(
                    "Storm Charge: gains movement and defensive benefits during rain or thunderstorms.",
                    "Storm Strike: attacks during thunderstorms can trigger a visual storm proc with a cooldown."
            );
            case BERSERKER -> List.of(
                    "Bloodlust: player kills build a streak up to 5, improving combat effects.",
                    "The streak expires after 10 minutes without a kill, resets on death, and persists across relogging.",
                    "Berserker Strike: attacks gain a combat bonus."
            );
            case TIDEBORN -> List.of(
                    "Tidal Grace: improved underwater movement and breathing support.",
                    "Waterborn: prevents normal drowning and greatly reduces fall damage when landing in water.",
                    "Tidal Combat: deals increased damage underwater and can push nearby hostile mobs away."
            );
            case TITAN -> List.of(
                    "Colossus: significantly reduces hostile-mob damage and fall damage.",
                    "Heavy Armor: has a chance to prevent armor durability loss."
            );
            case VOIDBORN -> List.of(
                    "Void Affinity: reduces incoming damage in the End and provides a smaller benefit in the Nether.",
                    "Void Hunter: deals bonus damage to End-related creatures and other non-player creatures.",
                    "Nether Hunter: gains a smaller damage bonus in the Nether."
            );
            case CELESTIAL -> List.of(
                    "Divine Protection: reduces fall, fire, fire-tick and hot-surface damage.",
                    "Divine Recovery: can recover health when heavily injured."
            );
            case PHOENIX -> List.of(
                    "Phoenix Rebirth: has up to 2 charges per life to survive eligible lethal damage.",
                    "Rebirth restores health and briefly protects the player; void deaths and /kill are excluded.",
                    "Fire passive: gains additional protection against fire-related damage."
            );
            case ARCANE -> List.of(
                    "Arcane Ward: has a chance to greatly reduce incoming damage.",
                    "Arcane Strike: attacks can trigger a magical combat strike."
            );
            case DRAGONBORN -> List.of(
                    "Draconic Resistance: strongly reduces fire, lava, fire-tick and hot-surface damage.",
                    "Draconic Might: deals increased damage to non-player creatures, with a stronger bonus against bosses and dangerous mobs."
            );
            case MAYON -> List.of(
                    "Silverine Presence: configurable transient attribute buffs and a silver-white particle aura.",
                    "Silvering Aura: admin-controlled levitation and movement lock around the Mayon holder."
            );
        };
    }

    private String getAbilityDescription(Bloodline bloodline) {
        return switch (bloodline) {
            case NORMAL -> "None; Normal has no active ability.";
            case VAMPIRE -> "Blood Frenzy: heals you, grants temporary absorption health, damages nearby enemies and pulls them inward.";
            case FROSTBORN -> "Frozen Core: damages nearby enemies, builds freeze, and knocks them back.";
            case SHADOW -> "Shadow Step: blinks forward safely and damages/knocks back nearby enemies at the destination.";
            case DEMON -> "Infernal Fury: blasts nearby enemies with fire damage, ignites and knocks them away; grants Strength III, Resistance II and Fire Resistance briefly."; 
            case STORMBORN -> "Tempest: releases an electric shockwave that damages and launches nearby enemies.";
            case BERSERKER -> "Blood Rage: heals you slightly and strikes enemies in front of you with a heavy knockback.";
            case TIDEBORN -> "Tidal Rush: dashes and damages/knocks back nearby enemies; grants enhanced aquatic movement and breathing."; 
            case TITAN -> "Colossus: leaps upward and slams nearby enemies away with a heavy impact; grants temporary Resistance II and Strength I."; 
            case VOIDBORN -> "Void Blink: safely teleports forward, then damages and knocks back nearby enemies.";
            case CELESTIAL -> "Divine Grace: restores health, grants absorption, clears fire and pushes nearby enemies away; adds a short regeneration and resistance window."; 
            case PHOENIX -> "Phoenix Flame: clears fire, restores health and sends out a fiery blast that damages, ignites and knocks back enemies; briefly grants fire protection, Strength and regeneration."; 
            case ARCANE -> "Arcane Resonance: fires a magical bolt at a target in front of you for high damage and knockback.";
            case DRAGONBORN -> "Dragon's Roar: unleashes a forward dragon blast that heavily damages, ignites and launches enemies; grants Strength III, Resistance II and Fire Resistance briefly.";
            case MAYON -> "Silverine Particle Storm: a short rising silver spiral and expanding ring; optionally configurable damage.";
        };
    }

    private void showList(CommandSender sender) {
        sender.sendMessage(Component.text("Bloodlines", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));

        for (Bloodline bloodline : Bloodline.values()) {
            String chance = String.format(Locale.ROOT, "%.2f%%", bloodline.getChance());
            Component line = Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(bloodline.getDisplayName(), bloodline.getColor()))
                    .append(Component.text(" — " + bloodline.getRarity() + " (" + chance + ")", NamedTextColor.GRAY));

            if (bloodline == Bloodline.MAYON) {
                line = line.append(Component.text(" [Special]", NamedTextColor.GOLD));
            }
            sender.sendMessage(line);
        }
    }

    private void setBloodline(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /bloodline set <player> <bloodline>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("That player must be online.", NamedTextColor.RED));
            return;
        }

        Bloodline bloodline = Bloodline.fromString(args[2]);
        if (bloodline == null) {
            sender.sendMessage(Component.text("Unknown Bloodline. Use /bloodline list.", NamedTextColor.RED));
            return;
        }

        Bloodline previous = manager.getBloodline(target.getUniqueId());
        manager.setBloodline(target.getUniqueId(), target.getName(), bloodline);
        awakening.cancel(target.getUniqueId());
        listener.handleBloodlineChange(target, previous, bloodline);

        if (animation != null) {
            animation.start(target, bloodline, previous);
        }

        sender.sendMessage(Component.text("Set " + target.getName() + "'s Bloodline to " + bloodline.getDisplayName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text("Your Bloodline is now ", NamedTextColor.GRAY)
                .append(Component.text(bloodline.getDisplayName(), bloodline.getColor()))
                .append(Component.text(".", NamedTextColor.GRAY)));
    }

    private void resetBloodline(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /bloodline reset <player>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("That player must be online.", NamedTextColor.RED));
            return;
        }

        Bloodline previous = manager.getBloodline(target.getUniqueId());
        boolean hadAwakening = manager.hasAwakening(target.getUniqueId());
        boolean removed = manager.resetBloodline(target.getUniqueId());
        if (!removed && !hadAwakening) {
            sender.sendMessage(Component.text("That player has no Bloodline or pending awakening to reset.", NamedTextColor.YELLOW));
            return;
        }

        awakening.cancel(target.getUniqueId());
        listener.handleBloodlineChange(target, previous, null);

        BloodlineSMP plugin = manager.getPlugin();
        boolean autoAssign = plugin.getConfig().getBoolean("settings.auto-assign", true);
        if (autoAssign) {
            String mode = plugin.getConfig().getString("settings.assignment-mode", "timer");
            long delay = Math.max(0L, plugin.getConfig().getLong("settings.assignment-delay", 60L));
            if ("direct".equalsIgnoreCase(mode) || delay == 0L) {
                Bloodline reassigned = manager.assignIfMissing(target.getUniqueId(), target.getName());
                listener.handleBloodlineChange(target, null, reassigned);
                if (reassigned != null && animation != null && plugin.getConfig().getBoolean("settings.reveal-animation", true)) {
                    animation.start(target, reassigned, null);
                }
                target.sendMessage(Component.text("Your Bloodline has been reset and reassigned.", NamedTextColor.YELLOW));
            } else if (delay <= Long.MAX_VALUE / 1000L) {
                long start = System.currentTimeMillis();
                long duration = delay * 1000L;
                if (start <= Long.MAX_VALUE - duration && manager.startAwakening(target.getUniqueId(), target.getName(), start, start + duration)) {
                    awakening.start(target.getUniqueId(), start, duration);
                    target.sendMessage(Component.text("Your Bloodline was reset. A new awakening has begun.", NamedTextColor.YELLOW));
                }
            }
        } else {
            target.sendMessage(Component.text("Your Bloodline or pending awakening has been reset.", NamedTextColor.YELLOW));
        }
        sender.sendMessage(Component.text("Reset " + target.getName() + "'s Bloodline state.", NamedTextColor.GREEN));
    }

    private void rerollBloodline(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /bloodline reroll <player>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("That player must be online.", NamedTextColor.RED));
            return;
        }

        Bloodline previous = manager.getBloodline(target.getUniqueId());
        Bloodline result = manager.rerollBloodline(target.getUniqueId(), target.getName());
        if (result == null) {
            sender.sendMessage(Component.text("Could not assign a Bloodline.", NamedTextColor.RED));
            return;
        }

        awakening.cancel(target.getUniqueId());
        listener.handleBloodlineChange(target, previous, result);

        if (animation != null) {
            animation.start(target, result, previous);
        }

        sender.sendMessage(Component.text("Rerolled " + target.getName() + "'s Bloodline to " + result.getDisplayName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text("Your Bloodline has been rerolled to ", NamedTextColor.GRAY)
                .append(Component.text(result.getDisplayName(), result.getColor()))
                .append(Component.text(".", NamedTextColor.GRAY)));
    }

    private void giveSigil(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /bloodline givesigil <player>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("That player must be online.", NamedTextColor.RED));
            return;
        }

        giveOrDrop(target, item.createSigil(null));
        sender.sendMessage(Component.text("Gave a Bloodline Sigil to " + target.getName() + ".", NamedTextColor.GREEN));
    }

    private void giveItem(CommandSender sender, String[] args) {
        if (!requireAdmin(sender)) return;
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /bloodline give <player> <sigil|coin>", NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("That player must be online.", NamedTextColor.RED));
            return;
        }

        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "sigil" -> giveOrDrop(target, item.createSigil(null));
            case "coin" -> giveOrDrop(target, item.createCoin());
            default -> {
                sender.sendMessage(Component.text("Item must be sigil or coin.", NamedTextColor.RED));
                return;
            }
        }

        sender.sendMessage(Component.text("Gave a Bloodline " + args[2].toLowerCase(Locale.ROOT) + " to " + target.getName() + ".", NamedTextColor.GREEN));
    }

    private void giveOrDrop(Player player, org.bukkit.inventory.ItemStack stack) {
        java.util.Map<Integer, org.bukkit.inventory.ItemStack> leftovers = player.getInventory().addItem(stack);
        for (org.bukkit.inventory.ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("bloodline.admin")) return true;
        sender.sendMessage(Component.text("You do not have permission to do that.", NamedTextColor.RED));
        return false;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("BloodlineSMP Commands", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        sender.sendMessage(Component.text("/bloodline info ", NamedTextColor.YELLOW).append(Component.text("View your Bloodline.", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/bloodline list ", NamedTextColor.YELLOW).append(Component.text("List all Bloodlines and chances.", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/bloodline help ", NamedTextColor.YELLOW).append(Component.text("Show commands.", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/bl ability", NamedTextColor.YELLOW).append(Component.text("Use your Bloodline ability.", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("/bl particles <on|off>", NamedTextColor.YELLOW));

        if (sender.hasPermission("bloodline.admin")) {
            sender.sendMessage(Component.text("/bloodline info <player>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/bloodline set <player> <bloodline>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/bloodline reset <player>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/bloodline reroll <player>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/bloodline give <player> <sigil|coin>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/bloodline givesigil <player>", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/bl mayon <storm|particles|aura> ...", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/bl givecoinall [amount]", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/bl reload", NamedTextColor.YELLOW));
            sender.sendMessage(Component.text("/bl ability <player> [force]", NamedTextColor.YELLOW));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> commands = new ArrayList<>(Arrays.asList("info", "list", "help", "ability"));
            if (sender.hasPermission("bloodline.admin")) {
                commands.addAll(Arrays.asList("set", "reset", "reroll", "give", "givesigil", "mayon", "givecoinall", "reload"));
            }
            return filter(commands, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("ability") && sender.hasPermission("bloodline.admin")) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("ability") && sender.hasPermission("bloodline.admin")) return filter(Arrays.asList("force"), args[2]);
        if (args.length == 2 && args[0].equalsIgnoreCase("particles")) return filter(Arrays.asList("on", "off"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("mayon")) return filter(Arrays.asList("storm", "particles", "aura"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("givecoinall") && sender.hasPermission("bloodline.admin")) return filter(Arrays.asList("1", "2", "4", "8", "16"), args[1]);
        if (args.length == 2 && Arrays.asList("info", "set", "reset", "reroll", "give", "givesigil").contains(args[0].toLowerCase(Locale.ROOT))) {
            if (sender.hasPermission("bloodline.admin")) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
            }
            if (args[0].equalsIgnoreCase("info") && args[1].isEmpty()) {
                return Collections.emptyList();
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("mayon") && sender.hasPermission("bloodline.admin")) {
            if (args[1].equalsIgnoreCase("aura") && args[2].equalsIgnoreCase("off")) return filter(Arrays.asList("all"), args[2]);
            if (Arrays.asList("storm", "particles", "aura").contains(args[1].toLowerCase(Locale.ROOT)))
                return filter(Bukkit.getOnlinePlayers().stream().filter(p -> manager.getBloodline(p.getUniqueId()) == Bloodline.MAYON).map(Player::getName).collect(Collectors.toList()), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("mayon") && sender.hasPermission("bloodline.admin")) {
            if (args[1].equalsIgnoreCase("aura") && args[2].equalsIgnoreCase("off")) return filter(Arrays.asList("all"), args[3]);
            if (args[1].equalsIgnoreCase("particles") || args[1].equalsIgnoreCase("aura")) return filter(Arrays.asList("on", "off"), args[3]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set") && sender.hasPermission("bloodline.admin")) {
            return filter(Arrays.stream(Bloodline.values()).map(Bloodline::name).collect(Collectors.toList()), args[2]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give") && sender.hasPermission("bloodline.admin")) {
            return filter(Arrays.asList("sigil", "coin"), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .collect(Collectors.toList());
    }
}
