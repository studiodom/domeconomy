package domeconomy.listener;

import domeconomy.DomEconomyMain;
import domeconomy.gui.PayMenu;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PayListener implements Listener {

    private static final Set<UUID> processingPlayers = ConcurrentHashMap.newKeySet();

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        player.removeMetadata("pay_chat_waiting", DomEconomyMain.getInstance());
        player.removeMetadata("pay_target_uuid", DomEconomyMain.getInstance());
        player.removeMetadata("pay_current_amount", DomEconomyMain.getInstance());
        player.removeMetadata("pay_select_page", DomEconomyMain.getInstance());
        processingPlayers.remove(player.getUniqueId());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Component titleComp = event.getView().title();
        if (titleComp.equals(PayMenu.CONFIRM_TITLE)) {
            if (event.getPlayer() instanceof Player player) {
                player.removeMetadata("pay_target_uuid", DomEconomyMain.getInstance());
                player.removeMetadata("pay_current_amount", DomEconomyMain.getInstance());
                processingPlayers.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Component titleComp = event.getView().title();
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (titleComp.equals(PayMenu.SELECT_TITLE)) {
            int slot = event.getRawSlot();
            if (slot < 0) return;
            if (event.getClickedInventory() == player.getInventory()) return;
            event.setCancelled(true);

            int page = player.hasMetadata("pay_select_page") ? player.getMetadata("pay_select_page").get(0).asInt() : 0;

            if (slot == 48 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                new PayMenu().openPlayerSelection(player, page - 1);
                return;
            }

            if (slot == 50 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                new PayMenu().openPlayerSelection(player, page + 1);
                return;
            }

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() != Material.PLAYER_HEAD) return;

            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(PayMenu.KEY_PAY_TARGET_UUID, PersistentDataType.STRING)) {
                String targetUuidStr = meta.getPersistentDataContainer().get(PayMenu.KEY_PAY_TARGET_UUID, PersistentDataType.STRING);
                if (targetUuidStr != null) {
                    Player target = Bukkit.getPlayerExact(targetUuidStr);
                    if (target == null) {
                        try {
                            target = Bukkit.getPlayer(UUID.fromString(targetUuidStr));
                        } catch (Exception e) {}
                    }
                    if (target != null && target.isOnline()) {
                        player.closeInventory();
                        player.setMetadata("pay_target_uuid", new FixedMetadataValue(DomEconomyMain.getInstance(), targetUuidStr));
                        player.setMetadata("pay_chat_waiting", new FixedMetadataValue(DomEconomyMain.getInstance(), true));

                        player.sendMessage(Component.text("=====================================", NamedTextColor.GOLD));
                        player.sendMessage(Component.text("💸 送金先: " + target.getName(), NamedTextColor.YELLOW));
                        player.sendMessage(Component.text("チャット欄に「送金したい金額」を半角数字で直接入力してください。", NamedTextColor.AQUA));
                        player.sendMessage(Component.text("キャンセルする場合は、適当な文字（数字以外）を入力してください。", NamedTextColor.GRAY));
                        player.sendMessage(Component.text("=====================================", NamedTextColor.GOLD));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
                    } else {
                        player.sendMessage(Component.text("❌ 対象のプレイヤーはオフラインになりました。", NamedTextColor.RED));
                        player.closeInventory();
                    }
                }
            }
            return;
        }

        if (titleComp.equals(PayMenu.CONFIRM_TITLE)) {
            int slot = event.getRawSlot();
            if (slot < 0) return;
            if (event.getClickedInventory() == player.getInventory()) return;
            event.setCancelled(true);

            if (!player.hasMetadata("pay_target_uuid") || !player.hasMetadata("pay_current_amount")) {
                player.closeInventory();
                return;
            }

            if (processingPlayers.contains(player.getUniqueId())) {
                return;
            }

            String targetUuidStr = player.getMetadata("pay_target_uuid").get(0).asString();
            double currentAmount = player.getMetadata("pay_current_amount").get(0).asDouble();

            Player target = Bukkit.getPlayerExact(targetUuidStr);
            if (target == null) {
                try {
                    target = Bukkit.getPlayer(UUID.fromString(targetUuidStr));
                } catch (Exception e) {}
            }
            if (target == null || !target.isOnline()) {
                player.sendMessage(Component.text("❌ 対象のプレイヤーはオフラインになりました。", NamedTextColor.RED));
                player.closeInventory();
                return;
            }

            if (slot == 11) {
                processingPlayers.add(player.getUniqueId());

                Economy econ = DomEconomyMain.getEconomy();
                if (econ.getBalance(player) < currentAmount) {
                    player.sendMessage(Component.text("❌ 所持金が不足しています。", NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                    processingPlayers.remove(player.getUniqueId());
                    return;
                }

                econ.withdrawPlayer(player, currentAmount);
                econ.depositPlayer(target, currentAmount);

                player.closeInventory();
                player.removeMetadata("pay_target_uuid", DomEconomyMain.getInstance());
                player.removeMetadata("pay_current_amount", DomEconomyMain.getInstance());
                processingPlayers.remove(player.getUniqueId());

                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f);
                target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

                player.sendMessage(Component.text("💸 " + target.getName() + " に " + String.format("%,.0f円", currentAmount) + " を送金しました。", NamedTextColor.GREEN));
                target.sendMessage(Component.text("💸 " + player.getName() + " から " + String.format("%,.0f円", currentAmount) + " を受け取りました。", NamedTextColor.GREEN));
                return;
            }

            if (slot == 15) {
                player.closeInventory();
                player.removeMetadata("pay_target_uuid", DomEconomyMain.getInstance());
                player.removeMetadata("pay_current_amount", DomEconomyMain.getInstance());
                player.sendMessage(Component.text("❌ 送金手続きをキャンセルしました。", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.0f);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!player.hasMetadata("pay_chat_waiting") || !player.hasMetadata("pay_target_uuid")) return;

        event.setCancelled(true);

        String message = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        String targetUuidStr = player.getMetadata("pay_target_uuid").get(0).asString();

        double amount = -1.0;
        try {
            amount = Double.parseDouble(message);
            if (!Double.isFinite(amount) || Double.isNaN(amount)) throw new NumberFormatException();
        } catch (NumberFormatException e) {
        }

        final double finalAmount = amount;
        Bukkit.getScheduler().runTask(DomEconomyMain.getInstance(), () -> {
            player.removeMetadata("pay_chat_waiting", DomEconomyMain.getInstance());

            if (finalAmount <= 0.0) {
                player.removeMetadata("pay_target_uuid", DomEconomyMain.getInstance());
                player.sendMessage(Component.text("❌ 送金手続きをキャンセルしました。", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
                return;
            }

            Economy econ = DomEconomyMain.getEconomy();
            if (econ.getBalance(player) < finalAmount) {
                player.removeMetadata("pay_target_uuid", DomEconomyMain.getInstance());
                player.sendMessage(Component.text("❌ キャンセルされました。所持金が不足しています。", NamedTextColor.RED));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                return;
            }

            Player target = Bukkit.getPlayerExact(targetUuidStr);
            if (target == null) {
                try {
                    target = Bukkit.getPlayer(UUID.fromString(targetUuidStr));
                } catch (Exception e) {}
            }
            if (target != null && target.isOnline()) {
                player.setMetadata("pay_current_amount", new FixedMetadataValue(DomEconomyMain.getInstance(), finalAmount));
                new PayMenu().openConfirmMenu(player, target, finalAmount);
            } else {
                player.removeMetadata("pay_target_uuid", DomEconomyMain.getInstance());
                player.sendMessage(Component.text("❌ 対象のプレイヤーはオフラインになりました。", NamedTextColor.RED));
            }
        });
    }
}