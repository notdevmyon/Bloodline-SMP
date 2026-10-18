# BloodlineSMP 

### *Your blood defines your power. Your skill defines your legacy.*

**BloodlineSMP** is a lightweight bloodline system for Minecraft SMP servers, built for Paper. Discover your identity, master your bloodline's strengths, and compete with other players through unique passive traits and special abilities.

Rather than turning survival into a massive RPG, BloodlineSMP adds a distinct layer of identity and combat to the vanilla experience. Every bloodline has its own theme, strengths, and playstyle, giving players a reason to develop their own strategies.

> **Designed for SMPs. Built around identity, combat, and counterplay.**

---
## Use /bl info with bloodline to get information about passives and actives
---
## Features

- **15 unique bloodlines** — From the balanced Normal bloodline to powerful elemental and supernatural identities.
- **Distinct combat identities** — Lifesteal, crowd control, defense, mobility, survival, and more.
- **Passive traits** — Bloodline-specific advantages that influence how you explore and fight.
- **Special abilities** — Activate supported abilities through your Bloodline Sigil.
- **Bloodline assignment and awakening** — Supports the configured assignment flow, including timed awakening.
- **Blood Coins and crafting** — Supports the plugin's bloodline reroll and item crafting mechanics.
- **Persistent player data** — Bloodline assignments and awakening data are saved between sessions.
- **Configurable gameplay** — Adjust supported values, cooldowns, probabilities, and other mechanics through the configuration.
- **Lightweight design** — Built for survival multiplayer rather than a large MMO-style progression system.


---

# Placeholders
Placeholder	Description
%bloodlinesmp_bloodline%	Player's current bloodline
%bloodlinesmp_has_ability%	Whether the player has an ability
%bloodlinesmp_ability%	Bloodline ability information

---

## Understanding Bloodline Grades

The grades provide a simple way to discuss the expected strength of each bloodline.

| Grade | Meaning |
|---|---|
| **Underpowered** | Struggles to compete and may need buffs. |
| **Medium** | Balanced or effective mainly in specific situations. |
| **Strong** | Offers significant advantages when used correctly. |
| **OP** | Potentially dominant and deserving of careful balance testing. |
| **Special** | Reserved or unique; not necessarily a power ranking. |

### Preliminary power overview

- **Potentially OP:** Berserker, Phoenix, Dragonborn
- **Strong:** Vampire, Frostborn, Shadow, Demon, Stormborn, Titan, Voidborn, Celestial, Arcane
- **Medium:** Normal, Tideborn
- **Underpowered:** None assigned
- **Special:** Mayon

*These are conceptual estimates rather than results from controlled PvP testing. Actual balance can change with configuration, player skill, equipment, and server-specific rules.*

---

## Bloodline Abilities and the Bloodline Sigil

BloodlineSMP supports special abilities through the **Bloodline Sigil**, the dedicated item used to activate supported powers.

The Sigil is intended to be the controlled route for ability activation, helping prevent abilities from being triggered through unintended command shortcuts.

Depending on the bloodline, an ability may focus on mobility, offense, defense, or survival.

**Important:** A passive and an active ability are different:

- **Passive:** An effect that operates automatically when its conditions are met.
- **Active ability:** A power that the player deliberately activates through the supported mechanic.
- **Cooldown:** A period during which an ability cannot be used again.
- **Counterplay:** Opportunities for opponents to respond, escape, defend, or punish poor timing.

The precise ability effects, cooldowns, activation rules, and numerical values depend on the implemented version and its configuration.

## Blood Coins and Rerolls

BloodlineSMP includes a Blood Coin mechanic for reroll-related gameplay.

Blood Coins provide a way to interact with bloodline selection without making the system a conventional equipment-progression grind. Their availability and cooldowns can be controlled through the plugin's supported settings.

Before using a reroll item, check the server's rules: changing a bloodline may change your combat strengths and preferred playstyle.

## Awakening and Player Progression

BloodlineSMP supports configurable bloodline assignment and awakening.

In timed-awakening mode, players wait through the configured awakening period before their bloodline powers become available. The plugin can retain awakening progress across sessions, depending on its configured behavior.

This creates a clear distinction between receiving a bloodline identity and gaining access to its powers.

## Configuration

The plugin provides a `config.yml` for supported settings.

Server owners should review the configuration before opening the server to players. Depending on the version, supported options include:

- **Awakening:** Assignment mode and awakening delay.
- **Abilities:** Supported ability values, cooldowns, and activation-related settings.
- **Blood Coins:** Recipe and cooldown settings.
- **Saving:** Player data and automatic save behavior.

Configuration keys can differ between versions. Always use the `config.yml` bundled with the exact version installed on your server. Do not copy settings from a different release without checking compatibility.

## Installation

1. Install a compatible **Paper** server version.
2. Download the BloodlineSMP plugin JAR for your Minecraft version.
3. Place the JAR inside your server's `plugins` folder.
4. Start or restart the server.
5. Review the generated configuration files.
6. Test bloodline assignment, passive effects, abilities, and crafting before allowing regular gameplay.

**Recommended:** Back up your world and existing plugin data before updating. Avoid replacing data files unless the release instructions explicitly require it.

## Compatibility

- **Server software:** Paper
- **Minecraft version:** Use a release explicitly marked compatible with your server version.
- **Java:** Follow the Java requirement of the selected plugin release.
- **Dependencies:** Check the release metadata and bundled plugin configuration for any required or optional integrations.

Compatibility with other server software, forks, and third-party plugins should be tested rather than assumed.

## Performance and Design Philosophy

BloodlineSMP aims to add identity and combat variety without turning a survival server into an oversized RPG.

Its design emphasizes:

- Meaningful differences between bloodlines.
- Passive traits that are easy to understand.
- Active abilities with opportunities for counterplay.
- Configurable mechanics for different SMP environments.
- Persistent player data without requiring an external database for the basic system.

Actual server performance depends on player count, hardware, other plugins, and the configured mechanics.

## Frequently Asked Questions

**Q: Is BloodlineSMP a complete RPG system?**

No. It is primarily a bloodline identity and combat system designed to complement ordinary SMP gameplay.

**Q: Are all bloodlines equally strong?**

They have different strengths and situational advantages. Their actual balance depends on the configuration and implementation.

**Q: Can I change the bloodline after receiving one?**

The plugin includes reroll-related mechanics. Availability depends on the Blood Coin system and the server's configuration.

**Q: Is Mayon available through normal random selection?**

No. Mayon is reserved and excluded from normal random bloodline selection.

**Q: Can server owners balance the plugin?**

Yes, through the settings supported by the installed version. Always check the bundled configuration for the exact available options.

**Q: Are the listed OP grades official test results?**

No. They are preliminary estimates based on the intended mechanics, not a guarantee that a bloodline will win a fight.

---

## Credits

**Developer:** Notdev / Notdev Studios  
**Project:** BloodlineSMP  
**Platform:** Paper

Built for SMP communities that want distinct player identities, exciting combat, and meaningful choices without sacrificing the core Minecraft survival experience.

*Discover your bloodline. Learn its strengths. Master its weaknesses.*
