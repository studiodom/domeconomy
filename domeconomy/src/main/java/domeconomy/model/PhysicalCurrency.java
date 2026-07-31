package domeconomy.model;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public enum PhysicalCurrency {
    YEN_1(1, "1円硬貨", Material.GOLD_NUGGET, 1001),
    YEN_5(5, "5円硬貨", Material.GOLD_NUGGET, 1005),
    YEN_10(10, "10円硬貨", Material.GOLD_NUGGET, 1010),
    YEN_50(50, "50円硬貨", Material.GOLD_NUGGET, 1050),
    YEN_100(100, "100円硬貨", Material.GOLD_NUGGET, 1100),
    YEN_500(500, "500円硬貨", Material.GOLD_NUGGET, 1500),
    YEN_1000(1000, "1000円札", Material.GOLD_NUGGET, 2000),
    YEN_5000(5000, "5000円札", Material.GOLD_NUGGET, 2005),
    YEN_10000(10000, "10000円札", Material.GOLD_NUGGET, 2010),
    YEN_100000(100000, "100000円札", Material.GOLD_NUGGET, 2011),
    YEN_1000000(1000000, "1000000円札", Material.GOLD_NUGGET, 2012),
    YEN_10000000(10000000, "10000000円札", Material.GOLD_NUGGET, 2013),
    YEN_100000000(100000000, "1億円札", Material.GOLD_NUGGET, 2014),
    YEN_1000000000000L(1000000000000L, "1兆円札", Material.GOLD_NUGGET, 2015);

    private final double value;
    private final String name;
    private final Material material;
    private final int customModelData;

    private static final Map<Integer, PhysicalCurrency> BY_CUSTOM_MODEL_DATA = new HashMap<>();
    public static final NamespacedKey KEY_VALUE = new NamespacedKey("domeconomy", "currency_value");

    static {
        for (PhysicalCurrency currency : values()) {
            BY_CUSTOM_MODEL_DATA.put(currency.customModelData, currency);
        }
    }

    PhysicalCurrency(double value, String name, Material material, int customModelData) {
        this.value = value;
        this.name = name;
        this.material = material;
        this.customModelData = customModelData;
    }

    public double getValue() { return value; }

    public ItemStack createItemStack(int amount) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(Component.text("価値: ", NamedTextColor.GRAY).append(Component.text(String.format("%.0f", value) + "円", NamedTextColor.GREEN)).decoration(TextDecoration.ITALIC, false)));
            meta.setCustomModelData(customModelData);
            meta.getPersistentDataContainer().set(KEY_VALUE, PersistentDataType.DOUBLE, value);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static double getMoneyValue(ItemStack item) {
        if (item == null) return 0;

        if (item.getType() != Material.GOLD_NUGGET) {
            return 0;
        }

        if (!item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();

        if (meta.getPersistentDataContainer().has(KEY_VALUE, PersistentDataType.DOUBLE)) {
            Double pVal = meta.getPersistentDataContainer().get(KEY_VALUE, PersistentDataType.DOUBLE);
            if (pVal != null) return pVal;
        }

        return 0;
    }
}