package domeconomy.gui;

import domeconomy.DomEconomyMain;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PayMenu {

    public static final Component SELECT_TITLE = Component.text("💵 送金先プレイヤーを選択", NamedTextColor.DARK_BLUE);
    public static final Component CONFIRM_TITLE = Component.text("💵 送金確認画面", NamedTextColor.DARK_BLUE);
    public static final NamespacedKey KEY_PAY_TARGET_UUID = new NamespacedKey("domeconomy", "pay_target_uuid");

    private ItemStack getGlass(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
            glass.setItemMeta(meta);
        }
        return glass;
    }

    public void openPlayerSelection(Player sender, int page) {
        sender.setMetadata("pay_select_page", new FixedMetadataValue(DomEconomyMain.getInstance(), page));

        List<Player> targets = Bukkit.getOnlinePlayers().stream()
                .map(p -> (Player) p)
                .filter(p -> !p.getUniqueId().equals(sender.getUniqueId()))
                .skip(page * 45L)
                .limit(46)
                .toList();

        Inventory inv = Bukkit.createInventory(null, 54, SELECT_TITLE);

        for (int i = 45; i < 54; i++) {
            inv.setItem(i, getGlass(Material.GRAY_STAINED_GLASS_PANE));
        }

        int itemsToShow = Math.min(45, targets.size());

        for (int i = 0; i < itemsToShow; i++) {
            Player target = targets.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text(target.getName(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                meta.setPlayerProfile(target.getPlayerProfile());
                meta.getPersistentDataContainer().set(KEY_PAY_TARGET_UUID, PersistentDataType.STRING, target.getUniqueId().toString());
                head.setItemMeta(meta);
            }
            inv.setItem(i, head);
        }

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            if (prevMeta != null) {
                prevMeta.displayName(Component.text("◀ 前のページへ", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                prev.setItemMeta(prevMeta);
            }
            inv.setItem(48, prev);
        }

        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        if (pageInfoMeta != null) {
            pageInfoMeta.displayName(Component.text("ページ " + (page + 1), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            pageInfo.setItemMeta(pageInfoMeta);
        }
        inv.setItem(49, pageInfo);

        if (targets.size() > 45) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            if (nextMeta != null) {
                nextMeta.displayName(Component.text("次のページへ ▶", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                next.setItemMeta(nextMeta);
            }
            inv.setItem(50, next);
        }

        sender.openInventory(inv);
    }

    public void openConfirmMenu(Player sender, Player target, double amount) {
        Inventory inv = Bukkit.createInventory(null, 27, CONFIRM_TITLE);

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, getGlass(Material.GRAY_STAINED_GLASS_PANE));
        }

        double balance = DomEconomyMain.getEconomy().getBalance(sender);

        ItemStack confirmBtn = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta confMeta = confirmBtn.getItemMeta();
        if (confMeta != null) {
            confMeta.displayName(Component.text("🟢 送金を確定する", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("クリックすると実際に送金を実行します。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            confMeta.lore(lore);
            confirmBtn.setItemMeta(confMeta);
        }
        inv.setItem(11, confirmBtn);

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.displayName(Component.text("📋 送金情報", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("送金先: " + target.getName(), NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("送金額: " + String.format("%,.0f円", amount), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("送金後の残高: " + String.format("%,.0f円", (balance - amount)), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            infoMeta.lore(lore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(13, info);

        ItemStack cancelBtn = new ItemStack(Material.BARRIER);
        ItemMeta cancMeta = cancelBtn.getItemMeta();
        if (cancMeta != null) {
            cancMeta.displayName(Component.text("❌ 送金をキャンセルする", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("送金を中止し、画面を閉じます。", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            cancMeta.lore(lore);
            cancelBtn.setItemMeta(cancMeta);
        }
        inv.setItem(15, cancelBtn);

        sender.openInventory(inv);
    }
}