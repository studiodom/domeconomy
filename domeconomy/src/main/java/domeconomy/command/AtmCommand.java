package domeconomy.command;

import domeconomy.gui.AtmMenu;
import domeconomy.listener.AtmListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AtmCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("domeconomy.admin") && !sender.isOp()) {
                sender.sendMessage(Component.text("❌ 権限がありません。", NamedTextColor.RED));
                return true;
            }
            Player targetPlayer;
            if (args.length > 1) {
                targetPlayer = Bukkit.getPlayerExact(args[1]);
                if (targetPlayer == null) {
                    sender.sendMessage(Component.text("❌ 対象のプレイヤーが見つからないか、オフラインです。", NamedTextColor.RED));
                    return true;
                }
            } else {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("プレイヤー名を指定してください。");
                    return true;
                }
                targetPlayer = player;
            }

            java.util.HashMap<Integer, ItemStack> remaining = targetPlayer.getInventory().addItem(AtmListener.createAtmItem());
            for (ItemStack rem : remaining.values()) {
                targetPlayer.getWorld().dropItemNaturally(targetPlayer.getLocation(), rem);
            }

            targetPlayer.sendMessage(Component.text("🏛️ ATM端末を取得しました。地面に設置して使用してください。", NamedTextColor.GREEN));
            if (targetPlayer != sender) {
                sender.sendMessage(Component.text("🏛️ " + targetPlayer.getName() + " にATM端末を付与しました。", NamedTextColor.GREEN));
            }
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        AtmMenu.getInstance().openSelection(player);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if ("give".startsWith(args[0].toLowerCase()) && (sender.hasPermission("domeconomy.admin") || sender.isOp())) {
                list.add("give");
            }
            return list;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("domeconomy.admin") && !sender.isOp()) {
                return Collections.emptyList();
            }
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(p.getName());
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}