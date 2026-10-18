package com.notdev.bloodline;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates and validates shared Bloodline items and registers configured recipes. */
public final class BloodlineItem implements Listener {
    private static final int ITEM_VERSION = 1;

    private final BloodlineSMP plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey versionKey;
    private final NamespacedKey sigilRecipeKey;
    private final NamespacedKey coinRecipeKey;
    private final Map<Character, Material> sigilIngredients = new HashMap<>();
    private final Map<Character, Material> coinIngredients = new HashMap<>();
    private String[] sigilShape;
    private String[] coinShape;
    private boolean sigilRecipeEnabled;
    private boolean coinRecipeEnabled;

    public BloodlineItem(BloodlineSMP plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "bloodline_item");
        this.versionKey = new NamespacedKey(plugin, "bloodline_item_version");
        this.sigilRecipeKey = new NamespacedKey(plugin, "bloodline_sigil_recipe");
        this.coinRecipeKey = new NamespacedKey(plugin, "blood_coin_recipe");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void registerRecipes() {
        unregisterRecipes();
        loadRecipe("recipes.sigil", sigilIngredients, true);
        loadRecipe("recipes.coin", coinIngredients, false);

        if (sigilRecipeEnabled) {
            ShapedRecipe recipe = new ShapedRecipe(sigilRecipeKey, createSigil(null));
            recipe.shape(sigilShape);
            sigilIngredients.forEach((symbol, material) -> recipe.setIngredient(symbol, material));
            recipe.setCategory(CraftingBookCategory.MISC);
            if (!Bukkit.addRecipe(recipe)) {
                sigilRecipeEnabled = false;
                plugin.getLogger().warning("Could not register the Bloodline Sigil recipe; check for a conflicting recipe key.");
            }
        }

        if (coinRecipeEnabled) {
            ShapedRecipe recipe = new ShapedRecipe(coinRecipeKey, createCoin());
            recipe.shape(coinShape);
            coinIngredients.forEach((symbol, material) -> recipe.setIngredient(symbol, material));
            recipe.setCategory(CraftingBookCategory.MISC);
            if (!Bukkit.addRecipe(recipe)) {
                coinRecipeEnabled = false;
                plugin.getLogger().warning("Could not register the Blood Coin recipe; check for a conflicting recipe key.");
            }
        }
    }

    private void loadRecipe(String path, Map<Character, Material> ingredients, boolean sigil) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        ingredients.clear();
        if (section == null) {
            setRecipeState(sigil, false, null);
            return;
        }

        boolean enabled = section.getBoolean("enabled", false);
        List<String> rows = section.getStringList("shape");
        ConfigurationSection ingredientSection = section.getConfigurationSection("ingredients");
        if (!enabled || rows.size() != 3 || ingredientSection == null) {
            setRecipeState(sigil, false, null);
            return;
        }

        String[] shape = rows.toArray(String[]::new);
        for (String row : shape) {
            if (row == null || row.length() != 3) {
                plugin.getLogger().warning("Disabled invalid custom recipe at " + path + ": shape rows must each contain exactly 3 characters.");
                setRecipeState(sigil, false, null);
                return;
            }
        }

        for (String row : shape) {
            for (char symbol : row.toCharArray()) {
                if (symbol == ' ') {
                    continue;
                }
                String configuredMaterial = ingredientSection.getString(String.valueOf(symbol));
                Material material = configuredMaterial == null ? null : Material.matchMaterial(configuredMaterial.trim());
                if (material == null || !material.isItem()) {
                    plugin.getLogger().warning("Disabled invalid custom recipe at " + path + ": missing or invalid ingredient for symbol '" + symbol + "'.");
                    ingredients.clear();
                    setRecipeState(sigil, false, null);
                    return;
                }
                ingredients.put(symbol, material);
            }
        }

        if (ingredients.isEmpty()) {
            setRecipeState(sigil, false, null);
            return;
        }
        setRecipeState(sigil, true, shape);
    }

    private void setRecipeState(boolean sigil, boolean enabled, String[] shape) {
        if (sigil) {
            sigilRecipeEnabled = enabled;
            sigilShape = shape;
        } else {
            coinRecipeEnabled = enabled;
            coinShape = shape;
        }
    }

    public void unregisterRecipes() {
        Bukkit.removeRecipe(sigilRecipeKey);
        Bukkit.removeRecipe(coinRecipeKey);
        sigilRecipeEnabled = false;
        coinRecipeEnabled = false;
        sigilIngredients.clear();
        coinIngredients.clear();
        sigilShape = null;
        coinShape = null;
    }

    public ItemStack createSigil(UUID owner) {
        ItemStack item = new ItemStack(Material.ECHO_SHARD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Bloodline Sigil", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Right-click to activate your Bloodline ability.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Anyone can use it. Cooldown is per player.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "sigil");
        meta.getPersistentDataContainer().set(versionKey, PersistentDataType.INTEGER, ITEM_VERSION);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createCoin() {
        ItemStack item = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Blood Coin", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Right-click to reroll your Bloodline.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Consumed on a successful reroll.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "coin");
        meta.getPersistentDataContainer().set(versionKey, PersistentDataType.INTEGER, ITEM_VERSION);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createCoinAmount(int amount) {
        ItemStack coin = createCoin();
        coin.setAmount(Math.max(1, Math.min(coin.getMaxStackSize(), amount)));
        return coin;
    }

    public boolean isSigil(ItemStack item) {
        return isBloodlineItem(item, "sigil");
    }

    public boolean hasSigilInInventory(Player player) {
        if (player == null) return false;
        for (ItemStack stack : player.getInventory().getContents()) if (isSigil(stack)) return true;
        return false;
    }

    public boolean isCoin(ItemStack item) {
        return isBloodlineItem(item, "coin");
    }

    private boolean isBloodlineItem(ItemStack item, String type) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        String storedType = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        Integer version = meta.getPersistentDataContainer().get(versionKey, PersistentDataType.INTEGER);
        return type.equals(storedType) && version != null && version == ITEM_VERSION;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getView().getPlayer() instanceof Player)) {
            return;
        }
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();
        for (ItemStack ingredient : matrix) {
            if (isSigil(ingredient) || isCoin(ingredient)) {
                inventory.setResult(null);
                return;
            }
        }

        if (sigilRecipeEnabled && matches(matrix, sigilShape, sigilIngredients)) {
            inventory.setResult(createSigil(null));
            return;
        }
        if (coinRecipeEnabled && matches(matrix, coinShape, coinIngredients)) {
            inventory.setResult(createCoin());
            return;
        }

        ItemStack result = inventory.getResult();
        if (isSigil(result) || isCoin(result)) {
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }

        ItemStack[] matrix = event.getInventory().getMatrix();
        boolean sigil = sigilRecipeEnabled && matches(matrix, sigilShape, sigilIngredients);
        boolean coin = coinRecipeEnabled && matches(matrix, coinShape, coinIngredients);
        if (!sigil && !coin) {
            if (isSigil(event.getCurrentItem()) || isCoin(event.getCurrentItem())) {
                event.setCancelled(true);
            }
            return;
        }

        if (sigil) {
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftingInventoryClick(InventoryClickEvent event) {
        // CraftItemEvent extends InventoryClickEvent; its dedicated handler above owns
        // the transaction so the first successful sigil craft is not cancelled here.
        if (event instanceof CraftItemEvent || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        InventoryType topType = event.getView().getTopInventory().getType();
        if (topType != InventoryType.WORKBENCH && topType != InventoryType.CRAFTING) {
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.RESULT) {
            return;
        }
        ItemStack current = event.getCurrentItem();
    }

    private boolean matches(ItemStack[] matrix, String[] shape, Map<Character, Material> ingredients) {
        if (matrix == null || shape == null || shape.length != 3 || matrix.length != 9) {
            return false;
        }
        for (int i = 0; i < 9; i++) {
            char symbol = shape[i / 3].charAt(i % 3);
            ItemStack stack = matrix[i];
            if (symbol == ' ') {
                if (stack != null && !stack.getType().isAir()) {
                    return false;
                }
                continue;
            }
            Material expected = ingredients.get(symbol);
            if (expected == null || stack == null || stack.getType() != expected || stack.getAmount() < 1 || isBloodlineItem(stack, "sigil") || isBloodlineItem(stack, "coin")) {
                return false;
            }
        }
        return true;
    }
}
