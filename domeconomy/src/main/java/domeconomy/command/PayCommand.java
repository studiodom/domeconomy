package domeconomy.command;

import domeconomy.DomEconomyMain;
import domeconomy.gui.PayMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PayCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        if (args.length == 0) {
            new PayMenu().openPlayerSelection(player, 0);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("❌ 対象のプレイヤーが見つからないか、オフラインです。", NamedTextColor.RED));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("❌ 自分自身に送金することはできません。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 1) {
            player.setMetadata("pay_target_uuid", new FixedMetadataValue(DomEconomyMain.getInstance(), target.getUniqueId().toString()));
            player.setMetadata("pay_chat_waiting", new FixedMetadataValue(DomEconomyMain.getInstance(), true));

            player.sendMessage(Component.text("=====================================", NamedTextColor.GOLD));
            player.sendMessage(Component.text("💸 送金先: " + target.getName(), NamedTextColor.YELLOW));
            player.sendMessage(Component.text("チャット欄に「送金したい金額」を半角数字で直接入力してください。", NamedTextColor.AQUA));
            player.sendMessage(Component.text("キャンセルする場合は、適当な文字（数字以外）を入力してください。", NamedTextColor.GRAY));
            player.sendMessage(Component.text("=====================================", NamedTextColor.GOLD));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
            return true;
        }

        if (args.length == 2) {
            double amount;
            try {
                amount = Double.parseDouble(args[1]);
                if (amount <= 0 || !Double.isFinite(amount) || Double.isNaN(amount)) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("❌ 正しい金額を入力してください（1以上の数値）。", NamedTextColor.RED));
                return true;
            }

            Economy econ = DomEconomyMain.getEconomy();
            if (econ.getBalance(player) < amount) {
                player.sendMessage(Component.text("❌ 所持金が不足しています。", NamedTextColor.RED));
                return true;
            }

            player.setMetadata("pay_target_uuid", new FixedMetadataValue(DomEconomyMain.getInstance(), target.getUniqueId().toString()));
            player.setMetadata("pay_current_amount", new FixedMetadataValue(DomEconomyMain.getInstance(), amount));
            new PayMenu().openConfirmMenu(player, target, amount);
            return true;
        }

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (sender instanceof Player player && !player.canSee(p)) {
                    continue;
                }
                if (!p.getName().equalsIgnoreCase(sender.getName()) && p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    list.add(p.getName());
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}