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

import java.util.List;

/**
 * ATMのGUIを管理・生成するクラスです。
 * スレッドセーフかつリファクタリングが容易な構造を提供します。
 */
public class AtmMenu {

    private static final AtmMenu INSTANCE = new AtmMenu();

    public static AtmMenu getInstance() {
        return INSTANCE;
    }

    // GUIタイトル
    public static final Component SELECT_TITLE = Component.text("銀行: 操作選択", NamedTextColor.DARK_GRAY);
    public static final Component DEPOSIT_TITLE = Component.text("💰 預け入れ窓口 (お金を入れて閉じる)", NamedTextColor.DARK_GREEN);
    public static final Component WITHDRAW_TITLE = Component.text("銀行: お引き出し窓口", NamedTextColor.DARK_RED);

    // メニューサイズ
    public static final int SELECT_SIZE = 27;
    public static final int DEPOSIT_SIZE = 27; // 空の預入スペース（チェスト1個分）
    public static final int WITHDRAW_SIZE = 36;

    // スロット配置用定数 (マジックナンバーの排除)
    public static final int SLOT_DEPOSIT = 11;
    public static final int SLOT_WITHDRAW = 15;

    // 制限設定
    public static final double MAX_TRANSACTION_LIMIT = 9000000000000.0;
    public static final int ATM_CUSTOM_MODEL_DATA = 9999;

    // 引き出しスロット配列
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

    // キャッシュアイテム
    private static final ItemStack SELECT_BACKGROUND_GLASS;
    private static final ItemStack WITHDRAW_BACKGROUND_GLASS;
    private static final ItemStack DEPOSIT_BUTTON;
    private static final ItemStack WITHDRAW_BUTTON;
    private static final ItemStack[] CACHED_WITHDRAW_ITEMS;

    static {
        // 背景ガラスの設定
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

        // お預け入れボタンの設定
        DEPOSIT_BUTTON = new ItemStack(Material.CHEST);
        ItemMeta depMeta = DEPOSIT_BUTTON.getItemMeta();
        if (depMeta != null) {
            depMeta.displayName(Component.text("💰 お預け入れ (デジタル口座へチャージ)", NamedTextColor.GREEN));
            DEPOSIT_BUTTON.setItemMeta(depMeta);
        }

        // お引き出しボタンの設定
        WITHDRAW_BUTTON = new ItemStack(Material.DISPENSER);
        ItemMeta withMeta = WITHDRAW_BUTTON.getItemMeta();
        if (withMeta != null) {
            withMeta.displayName(Component.text("💵 お引き出し (物理貨幣の発行)", NamedTextColor.RED));
            WITHDRAW_BUTTON.setItemMeta(withMeta);
        }

        // 引き出し可能な通貨のキャッシュ化
        CACHED_WITHDRAW_ITEMS = new ItemStack[WITHDRAW_CURRENCIES.length];
        for (int i = 0; i < WITHDRAW_CURRENCIES.length; i++) {
            CACHED_WITHDRAW_ITEMS[i] = WITHDRAW_CURRENCIES[i].createItemStack(1);
        }
    }

    /**
     * ATM操作の最初の画面「操作選択画面」を開きます。
     */
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

    /**
     * 【新規・修正】プレイヤーがお金を自由に入れて閉じるだけの、空の預け入れ専用GUIを開きます。
     */
    public void openDeposit(Player player) {
        AtmInventoryHolder holder = new AtmInventoryHolder("DEPOSIT");
        Inventory inv = Bukkit.createInventory(holder, DEPOSIT_SIZE, DEPOSIT_TITLE);
        holder.setInventory(inv);

        // アイテムを置くため、中身は空のままにしてプレイヤーに開きます
        player.openInventory(inv);
    }

    /**
     * 物理通貨を引き出すための窓口画面を開きます。
     */
    public void openWithdraw(Player player) {
        AtmInventoryHolder holder = new AtmInventoryHolder("WITHDRAW");
        Inventory inv = Bukkit.createInventory(holder, WITHDRAW_SIZE, WITHDRAW_TITLE);
        holder.setInventory(inv);

        // 周囲に赤いガラスを敷き詰めるレイアウト処理
        for (int i = 0; i < WITHDRAW_SIZE; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0 || row == 3 || col == 0 || col == 1 || col == 7 || col == 8) {
                inv.setItem(i, WITHDRAW_BACKGROUND_GLASS);
            }
        }

        // キャッシュ化された引き出しボタンの配置
        for (int i = 0; i < CACHED_WITHDRAW_ITEMS.length && i < WITHDRAW_BUTTONS.length; i++) {
            inv.setItem(WITHDRAW_BUTTONS[i], CACHED_WITHDRAW_ITEMS[i]);
        }

        player.openInventory(inv);
    }
}