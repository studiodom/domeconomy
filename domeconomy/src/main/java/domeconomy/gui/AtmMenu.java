package domeconomy.gui;

import domeconomy.model.PhysicalCurrency;
import domeconomy.listener.AtmListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AtmMenu {

    public static final Component SELECT_TITLE = Component.text("銀行: 操作選択", NamedTextColor.DARK_GRAY);
    public static final Component DEPOSIT_TITLE = Component.text("銀行: お預け入れ窓口", NamedTextColor.DARK_GREEN);
    public static final Component WITHDRAW_TITLE = Component.text("銀行: お引き出し窓口", NamedTextColor.DARK_RED);

    public static final int[] WITHDRAW_BUTTONS = {
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24
    };

    private static final PhysicalCurrency[] WITHDRAW_CURRENCIES = {
            PhysicalCurrency.YEN_1,
            PhysicalCurrency.YEN_10,
            PhysicalCurrency.YEN_100,
            PhysicalCurrency.YEN_1000,
            PhysicalCurrency.YEN_10000,
            PhysicalCurrency.YEN_100000,
            PhysicalCurrency.YEN_1000000,
            PhysicalCurrency.YEN_10000000,
            PhysicalCurrency.YEN_100000000,
            PhysicalCurrency.YEN_1000000000000L
    };

    private ItemStack getGlass(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            glass.setItemMeta(meta);
        }
        return glass;
    }

    public void openSelection(Player player) {
        Inventory inv = Bukkit.createInventory(new AtmInventoryHolder("SELECT"), 27, SELECT_TITLE);
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, getGlass(Material.GRAY_STAINED_GLASS_PANE));
        }

        ItemStack depBtn = new ItemStack(Material.CHEST);
        ItemMeta depMeta = depBtn.getItemMeta();
        if (depMeta != null) {
            depMeta.displayName(Component.text("💰 お預け入れ (デジタル口座へチャージ)", NamedTextColor.GREEN));
            depBtn.setItemMeta(depMeta);
        }

        ItemStack withBtn = new ItemStack(Material.DISPENSER);
        ItemMeta withMeta = withBtn.getItemMeta();
        if (withMeta != null) {
            withMeta.displayName(Component.text("💵 お引き出し (物理貨幣の発行)", NamedTextColor.RED));
            withBtn.setItemMeta(withMeta);
        }

        inv.setItem(11, depBtn);
        inv.setItem(15, withBtn);
        player.openInventory(inv);
    }

    public void openDeposit(Player player) {
        Inventory inv = Bukkit.createInventory(new AtmInventoryHolder("DEPOSIT"), 27, DEPOSIT_TITLE);
        player.openInventory(inv);
    }

    public void openWithdraw(Player player) {
        Inventory inv = Bukkit.createInventory(new AtmInventoryHolder("WITHDRAW"), 36, WITHDRAW_TITLE);
        ItemStack redGlass = getGlass(Material.RED_STAINED_GLASS_PANE);

        for (int i = 0; i < 36; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == 3 || col == 0 || col == 1 || col == 7 || col == 8) {
                inv.setItem(i, redGlass);
            }
        }

        for (int i = 0; i < WITHDRAW_CURRENCIES.length && i < WITHDRAW_BUTTONS.length; i++) {
            inv.setItem(WITHDRAW_BUTTONS[i], WITHDRAW_CURRENCIES[i].createItemStack(1));
        }
        AtmListener.initializeWithdrawSession(player);
        player.openInventory(inv);
    }
}