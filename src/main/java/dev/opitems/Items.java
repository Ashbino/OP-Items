package dev.opitems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/** Every custom item in the plugin. Items are tagged with a PersistentDataContainer id. */
public enum Items {

    SKELETON_STAFF(Material.BONE, "Skeleton Staff", 0xE8E4C9, null, 0,
            "Right-click to charge (3s).",
            "While charging you take 1.5x damage.",
            "Right-click when charged to fire a",
            "beam of souls that one-shots its target.",
            "Totems still save them."),

    WITHER_SCYTHE(Material.NETHERITE_HOE, "Wither Scythe", 0x4A4A4A, EquipmentSlotGroup.MAINHAND, 0,
            "Hits inflict Wither III and heal you.",
            "Right-click: Soul Wave - blasts everything",
            "within 6 blocks (10s cooldown)."),

    THUNDER_HAMMER(Material.MACE, "Thunder Hammer", 0xFFE14D, null, 0,
            "Right-click: call lightning on where",
            "you are looking (6s cooldown)."),

    BLINK_BLADE(Material.DIAMOND_SWORD, "Blink Blade", 0xB46BFF, null, 0,
            "Right-click: blink up to 12 blocks",
            "forward (2s cooldown)."),

    WRAITH_HELMET(Material.LEATHER_HELMET, "Wraith Helmet", 0x1AB6C9, EquipmentSlotGroup.HEAD, 3, armorLore()),
    WRAITH_CHESTPLATE(Material.LEATHER_CHESTPLATE, "Wraith Chestplate", 0x1AB6C9, EquipmentSlotGroup.CHEST, 8, armorLore()),
    WRAITH_LEGGINGS(Material.LEATHER_LEGGINGS, "Wraith Leggings", 0x1AB6C9, EquipmentSlotGroup.LEGS, 6, armorLore()),
    WRAITH_BOOTS(Material.LEATHER_BOOTS, "Wraith Boots", 0x1AB6C9, EquipmentSlotGroup.FEET, 3, armorLore());

    private static String[] armorLore() {
        return new String[]{
                "Full set bonus:",
                " - Speed II, Jump Boost I",
                " - Night Vision, Fire Resistance",
                " - 25% less damage, no fall damage"
        };
    }

    public static NamespacedKey ID_KEY;
    private static JavaPlugin plugin;

    public static void init(JavaPlugin p) {
        plugin = p;
        ID_KEY = new NamespacedKey(p, "item_id");
    }

    private final Material material;
    private final String displayName;
    private final int color;
    private final EquipmentSlotGroup slot;
    private final double armorPoints;
    private final String[] lore;

    Items(Material material, String displayName, int color, EquipmentSlotGroup slot, double armorPoints, String... lore) {
        this.material = material;
        this.displayName = displayName;
        this.color = color;
        this.slot = slot;
        this.armorPoints = armorPoints;
        this.lore = lore;
    }

    public boolean isArmor() {
        return name().startsWith("WRAITH_");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(displayName, TextColor.color(color))
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        List<Component> loreLines = new ArrayList<>();
        for (String line : lore) {
            loreLines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(loreLines);

        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.setEnchantmentGlintOverride(true);
        if (material.getMaxStackSize() > 1) {
            meta.setMaxStackSize(1);
        }

        switch (this) {
            case SKELETON_STAFF -> { }
            case WITHER_SCYTHE -> {
                // Custom attributes replace the vanilla ones, so define damage + speed fully.
                addMod(meta, Attribute.ATTACK_DAMAGE, "damage", 10.0, EquipmentSlotGroup.MAINHAND);
                addMod(meta, Attribute.ATTACK_SPEED, "speed", -2.0, EquipmentSlotGroup.MAINHAND);
            }
            case THUNDER_HAMMER -> meta.addEnchant(Enchantment.DENSITY, 5, true);
            case BLINK_BLADE -> {
                meta.addEnchant(Enchantment.SHARPNESS, 6, true);
                meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
            }
            default -> {
                // Armor
                meta.addEnchant(Enchantment.PROTECTION, 4, true);
                LeatherArmorMeta lm = (LeatherArmorMeta) meta;
                lm.setColor(Color.fromRGB(color));
                meta.addItemFlags(ItemFlag.HIDE_DYE);
                addMod(meta, Attribute.ARMOR, "armor", armorPoints, slot);
                addMod(meta, Attribute.ARMOR_TOUGHNESS, "toughness", 4.0, slot);
                addMod(meta, Attribute.MAX_HEALTH, "health", 4.0, slot);
                addMod(meta, Attribute.KNOCKBACK_RESISTANCE, "kb", 0.1, slot);
            }
        }

        meta.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, name());
        item.setItemMeta(meta);
        return item;
    }

    private void addMod(ItemMeta meta, Attribute attribute, String suffix, double amount, EquipmentSlotGroup group) {
        NamespacedKey key = new NamespacedKey(plugin, name().toLowerCase() + "_" + suffix);
        meta.addAttributeModifier(attribute,
                new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER, group));
    }

    // ---------- lookup helpers ----------

    public static Items fromStack(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return null;
        String id = stack.getItemMeta().getPersistentDataContainer().get(ID_KEY, PersistentDataType.STRING);
        if (id == null) return null;
        try {
            return valueOf(id);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static boolean is(ItemStack stack, Items type) {
        return fromStack(stack) == type;
    }

    public static Items byName(String name) {
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
