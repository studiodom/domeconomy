package domeconomy.gui;

import domeconomy.DomEconomyMain;
import domeconomy.model.PhysicalCurrency;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class AtmMenu {

    private static final AtmMenu INSTANCE = new AtmMenu();

    public static AtmMenu getInstance() {
        return INSTANCE;
    }

    public static final Component SELECT_TITLE = Component.text("銀行: 操作選択", NamedTextColor.DARK_GRAY);
    public static final Component DEPOSIT_CONFIRM_TITLE = Component.text("💰 預け入れ確認", NamedTextColor.DARK_GREEN);
    public static final Component WITHDRAW_TITLE = Component.text("銀行: お引き出し窓口", NamedTextColor.DARK_RED);

    public static final int SLOT_DEPOSIT = 11;
    public static final int SLOT_WITHDRAW = 15;
    public static final int SELECT_SIZE = 27;
    public static final int DEPOSIT_CONFIRM_SIZE = 27;
    public static final int WITHDRAW_SIZE = 36;
    public static final double MAX_TRANSACTION_LIMIT = 9000000000000.0;
    public static final int ATM_CUSTOM_MODEL_DATA = 9999;

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

    private static final ItemStack SELECT_BACKGROUND_GLASS;
    private static final ItemStack WITHDRAW_BACKGROUND_GLASS;
    private static final ItemStack DEPOSIT_BUTTON;
    private static final ItemStack WITHDRAW_BUTTON;
    private static final ItemStack[] CACHED_WITHDRAW_ITEMS;

    static {
        SELECT_BACKGROUND_GLASS = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta selectMeta = SELECT_BACKGROUND_GLASS.getItemMeta();
        if (selectMeta != null) {
            selectMeta.displayName(Component.text(" "));
            SELECT_BACKGROUND_GLASS.setItemMeta(selectMeta);
        }

        WITHDRAW_BACKGROUND_GLASS = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta withdrawMeta = WITHDRAW_BACKGROUND_GLASS.getItemMeta();
        if (withdrawMeta != null) {
            withdrawMeta.displayName(Component.text(" "));
            WITHDRAW_BACKGROUND_GLASS.setItemMeta(withdrawMeta);
        }

        DEPOSIT_BUTTON = new ItemStack(Material.CHEST);
        ItemMeta depMeta = DEPOSIT_BUTTON.getItemMeta();
        if (depMeta != null) {
            depMeta.displayName(Component.text("💰 お預け入れ (デジタル口座へチャージ)", NamedTextColor.GREEN));
            DEPOSIT_BUTTON.setItemMeta(depMeta);
        }

        WITHDRAW_BUTTON = new ItemStack(Material.DISPENSER);
        ItemMeta withMeta = WITHDRAW_BUTTON.getItemMeta();
        if (withMeta != null) {
            withMeta.displayName(Component.text("💵 お引き出し (物理貨幣の発行)", NamedTextColor.RED));
            WITHDRAW_BUTTON.setItemMeta(withMeta);
        }

        CACHED_WITHDRAW_ITEMS = new ItemStack[WITHDRAW_CURRENCIES.length];
        for (int i = 0; i < WITHDRAW_CURRENCIES.length; i++) {
            CACHED_WITHDRAW_ITEMS[i] = WITHDRAW_CURRENCIES[i].createItemStack(1);
        }
    }

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
        AtmInventoryHolder holder = new AtmInventoryHolder("SELECT");
        Inventory inv = Bukkit.createInventory(holder, SELECT_SIZE, SELECT_TITLE);
        holder.setInventory(inv);
        for (int i = 0; i < SELECT_SIZE; i++) {
            inv.setItem(i, SELECT_BACKGROUND_GLASS);
        }
        inv.setItem(SLOT_DEPOSIT, DEPOSIT_BUTTON);
        inv.setItem(SLOT_WITHDRAW, WITHDRAW_BUTTON);
        player.openInventory(inv);
    }

    public void openDepositConfirm(Player player) {
        double totalOnPlayer = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            double val = PhysicalCurrency.getMoneyValue(item);
            if (val > 0) {
                totalOnPlayer += (val * item.getAmount());
            }
        }

        if (totalOnPlayer <= 0) {
            player.sendMessage(Component.text("❌ インベントリに預け入れ可能な物理通貨がありません。", NamedTextColor.RED));
            return;
        }

        AtmInventoryHolder holder = new AtmInventoryHolder("DEPOSIT_CONFIRM");
        Inventory inv = Bukkit.createInventory(holder, DEPOSIT_CONFIRM_SIZE, DEPOSIT_CONFIRM_TITLE);
        holder.setInventory(inv);

        for (int i = 0; i < DEPOSIT_CONFIRM_SIZE; i++) {
            inv.setItem(i, SELECT_BACKGROUND_GLASS);
        }

        ItemStack confirmBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta confMeta = confirmBtn.getItemMeta();
        if (confMeta != null) {
            confMeta.displayName(Component.text("🟢 預け入れを実行する", NamedTextColor.GREEN));
            confMeta.lore(java.util.List.of(Component.text("インベントリ内のすべての物理通貨を一括チャージします。", NamedTextColor.GRAY)));
            confirmBtn.setItemMeta(confMeta);
        }

        double balance = 0;
        Economy eco = DomEconomyMain.getEconomy();
        if (eco != null) {
            balance = eco.getBalance(player);
        }

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(Component.text("📋 預け入れ詳細", NamedTextColor.YELLOW));
            infoMeta.lore(java.util.List.of(
                    Component.text("預け入れ額: " + String.format("%,.0f円", totalOnPlayer), NamedTextColor.GREEN),
                    Component.text("現在の残高: " + String.format("%,.0f円", balance), NamedTextColor.GRAY),
                    Component.text("預入後の残高: " + String.format("%,.0f円", (balance + totalOnPlayer)), NamedTextColor.GRAY)
            ));
            info.setItemMeta(infoMeta);
        }

        ItemStack cancelBtn = new ItemStack(Material.BARRIER);
        ItemMeta cancMeta = cancelBtn.getItemMeta();
        if (cancMeta != null) {
            cancMeta.displayName(Component.text("❌ キャンセルする", NamedTextColor.RED));
            cancelBtn.setItemMeta(cancMeta);
        }

        inv.setItem(11, confirmBtn);
        inv.setItem(13, info);
        inv.setItem(15, cancelBtn);

        player.openInventory(inv);
    }

    public void openWithdraw(Player player) {
        AtmInventoryHolder holder = new AtmInventoryHolder("WITHDRAW");
        Inventory inv = Bukkit.createInventory(holder, WITHDRAW_SIZE, WITHDRAW_TITLE);
        holder.setInventory(inv);
        for (int i = 0; i < WITHDRAW_SIZE; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == 3 || col == 0 || col == 1 || col == 7 || col == 8) {
                inv.setItem(i, WITHDRAW_BACKGROUND_GLASS);
            }
        }
        for (int i = 0; i < CACHED_WITHDRAW_ITEMS.length && i < WITHDRAW_BUTTONS.length; i++) {
            inv.setItem(WITHDRAW_BUTTONS[i], CACHED_WITHDRAW_ITEMS[i]);
        }
        player.openInventory(inv);
    }
}