package domeconomy.listener;

import domeconomy.DomEconomyMain;
import domeconomy.gui.AtmInventoryHolder;
import domeconomy.gui.AtmMenu;
import domeconomy.model.PhysicalCurrency;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AtmListener implements Listener {

    public static final NamespacedKey KEY_IS_ATM = new NamespacedKey("money", "is_atm");
    public static final NamespacedKey KEY_ATM_OWNER = new NamespacedKey("money", "atm_owner");

    public static final double ARMOR_STAND_Y_OFFSET = -1.2;
    public static final int ATM_CUSTOM_MODEL_DATA = 9999;

    private static final Map<BlockLoc, UUID> atmCache = new HashMap<>(128, 0.75f);

    public AtmListener() {}

    public static void flushAllPendingWithdrawals() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AtmInventoryHolder) {
                player.closeInventory();
            }
        }
        atmCache.clear();
    }

    public static BlockLoc getBlockLoc(Location loc) {
        if (loc == null) return null;
        return new BlockLoc(loc.getWorld().getUID(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private static BlockLoc parseBlockLocStr(org.bukkit.World world, String str) {
        if (world == null || str == null) return null;
        String[] parts = str.split(",");
        if (parts.length < 4) return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new BlockLoc(world.getUID(), x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static ItemStack createAtmItem() {
        ItemStack atm = new ItemStack(Material.NETHERITE_INGOT);
        ItemMeta meta = atm.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("🏛️ 銀行 ATM端末", NamedTextColor.AQUA));
            meta.setCustomModelData(ATM_CUSTOM_MODEL_DATA);
            meta.getPersistentDataContainer().set(KEY_IS_ATM, PersistentDataType.BOOLEAN, true);
            atm.setItemMeta(meta);
        }
        return atm;
    }

    private Entity getAtmEntity(Block block) {
        BlockLoc loc = getBlockLoc(block.getLocation());
        UUID uuid = atmCache.get(loc);
        if (uuid != null) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof ArmorStand stand && stand.getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN)) {
                return entity;
            }
        }
        String baseLocStr = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
        Collection<Entity> entities = block.getWorld().getNearbyEntities(block.getLocation().add(0.5, ARMOR_STAND_Y_OFFSET, 0.5), 0.5, 1.5, 0.5);
        for (Entity entity : entities) {
            if (entity instanceof ArmorStand stand) {
                String storedLoc = stand.getPersistentDataContainer().get(KEY_ATM_OWNER, PersistentDataType.STRING);
                if (storedLoc != null && storedLoc.equals(baseLocStr)) {
                    if (stand.getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN)) {
                        atmCache.put(loc, stand.getUniqueId());
                        return stand;
                    }
                }
            }
        }
        return null;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        boolean isAtm = item.getItemMeta().getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN);
        if (!isAtm) {
            return;
        }

        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.isOp() || player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked == null || clicked.getType() == Material.BARRIER) return;

            Block target = clicked.getRelative(event.getBlockFace());
            if (target.getType() == Material.AIR || target.getType() == Material.CAVE_AIR) {
                target.setType(Material.BARRIER);
                Location spawnLoc = target.getLocation().add(0.5, ARMOR_STAND_Y_OFFSET, 0.5);
                spawnLoc.setYaw(player.getLocation().getYaw() + 180f);

                target.getWorld().spawn(spawnLoc, ArmorStand.class, armorStand -> {
                    armorStand.setInvisible(true);
                    armorStand.setGravity(false);
                    armorStand.setInvulnerable(true);
                    
                    if (armorStand.getEquipment() != null) {
                        armorStand.getEquipment().setHelmet(createAtmItem());
                    }

                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        try {
                            armorStand.addDisabledSlots(slot);
                        } catch (Exception e) {}
                    }

                    armorStand.getPersistentDataContainer().set(KEY_IS_ATM, PersistentDataType.BOOLEAN, true);
                    String baseLocStr = target.getWorld().getName() + "," + target.getX() + "," + target.getY() + "," + target.getZ();
                    armorStand.getPersistentDataContainer().set(KEY_ATM_OWNER, PersistentDataType.STRING, baseLocStr);
                    atmCache.put(getBlockLoc(target.getLocation()), armorStand.getUniqueId());
                });
            }
        }
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity clicked = event.getRightClicked();
        if (clicked instanceof ArmorStand stand) {
            if (stand.getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN)) {
                event.setCancelled(true);
                AtmMenu.getInstance().openSelection(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ArmorStand stand) {
            if (stand.getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN)) {
                event.setCancelled(true);
                if (event.getDamager() instanceof Player player && player.isOp() && player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                    String storedLoc = stand.getPersistentDataContainer().get(KEY_ATM_OWNER, PersistentDataType.STRING);
                    BlockLoc cachedLoc = parseBlockLocStr(stand.getWorld(), storedLoc);
                    if (cachedLoc != null) {
                        Block block = stand.getWorld().getBlockAt(cachedLoc.x(), cachedLoc.y(), cachedLoc.z());
                        if (block.getType() == Material.BARRIER) {
                            block.setType(Material.AIR);
                        }
                        atmCache.remove(cachedLoc);
                    }
                    stand.remove();
                    player.sendMessage(Component.text("ATM端末を管理権限で強制破壊しました。", NamedTextColor.RED));
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BARRIER) return;

        BlockLoc loc = getBlockLoc(block.getLocation());
        Entity atmEntity = getAtmEntity(block);
        boolean isAtmBroken = false;

        if (atmEntity != null) {
            Player player = event.getPlayer();
            if (player.isOp() && player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                isAtmBroken = true;
                atmCache.remove(loc);
                atmEntity.remove();
            } else {
                event.setCancelled(true);
                return;
            }
        } else {
            String baseLocStr = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
            Collection<Entity> entities = block.getWorld().getNearbyEntities(block.getLocation().add(0.5, ARMOR_STAND_Y_OFFSET, 0.5), 0.5, 1.5, 0.5);
            for (Entity entity : entities) {
                if (entity instanceof ArmorStand stand) {
                    String storedLoc = stand.getPersistentDataContainer().get(KEY_ATM_OWNER, PersistentDataType.STRING);
                    if (storedLoc != null && storedLoc.equals(baseLocStr)) {
                        if (stand.getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN)) {
                            Player player = event.getPlayer();
                            if (player.isOp() && player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                                isAtmBroken = true;
                                atmCache.remove(loc);
                                stand.remove();
                            } else {
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                }
            }
        }

        if (isAtmBroken) {
            event.setDropItems(false);
            event.getPlayer().sendMessage(Component.text("ATM端末を破壊しました。", NamedTextColor.YELLOW));
        }
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof AtmInventoryHolder holder)) return;

        String type = holder.getType();

        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        if (event.getClick() == ClickType.NUMBER_KEY) {
            event.setCancelled(true);
            return;
        }

        if (type.equals("SELECT")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0) return;
            if (event.getClickedInventory() == player.getInventory()) return;
            if (slot == AtmMenu.SLOT_DEPOSIT) AtmMenu.getInstance().openDepositConfirm(player);
            if (slot == AtmMenu.SLOT_WITHDRAW) AtmMenu.getInstance().openWithdraw(player);
            return;
        }

        if (type.equals("DEPOSIT_CONFIRM")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0) return;
            if (event.getClickedInventory() == player.getInventory()) return;

            if (slot == 11) {
                double totalDeposit = 0;
                java.util.List<ItemStack> depositItems = new java.util.ArrayList<>();
                for (ItemStack item : player.getInventory().getContents()) {
                    double val = PhysicalCurrency.getMoneyValue(item);
                    if (val > 0) {
                        totalDeposit += (val * item.getAmount());
                        depositItems.add(item.clone());
                    }
                }

                if (totalDeposit <= 0) {
                    player.sendMessage(Component.text("❌ 預け入れ可能な物理通貨がありません。", NamedTextColor.RED));
                    player.closeInventory();
                    return;
                }

                if (totalDeposit > AtmMenu.MAX_TRANSACTION_LIMIT) {
                    player.sendMessage(Component.text("❌ 一回の取引上限を超える預け入れは行えません。", NamedTextColor.RED));
                    return;
                }

                Economy eco = DomEconomyMain.getEconomy();
                if (eco == null) {
                    player.sendMessage(Component.text("❌ 銀行システムが一時的にオフラインです。", NamedTextColor.RED));
                    return;
                }

                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    if (item != null && PhysicalCurrency.getMoneyValue(item) > 0) {
                        player.getInventory().setItem(i, null);
                    }
                }

                net.milkbowl.vault.economy.EconomyResponse response = eco.depositPlayer(player, totalDeposit);
                if (response != null && response.transactionSuccess()) {
                    player.sendMessage(Component.text("合計 " + String.format("%,.0f", totalDeposit) + "円 をデジタル口座に入金しました！", NamedTextColor.GREEN));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                    player.closeInventory();
                } else {
                    player.sendMessage(Component.text("❌ 入金処理に失敗したため、紙幣を返却しました。", NamedTextColor.RED));
                    for (ItemStack rem : depositItems) {
                        java.util.HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(rem);
                        for (ItemStack drop : remaining.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                        }
                    }
                    player.closeInventory();
                }
                return;
            }

            if (slot == 15) {
                AtmMenu.getInstance().openSelection(player);
            }
            return;
        }

        if (type.equals("WITHDRAW")) {
            int slot = event.getRawSlot();
            if (slot < 0) return;
            if (event.getClickedInventory() == player.getInventory()) {
                if (event.getClick().isShiftClick()) {
                    event.setCancelled(true);
                }
                return;
            }
            event.setCancelled(true);
            boolean isButton = false;
            for (int btnSlot : AtmMenu.WITHDRAW_BUTTONS) { if (btnSlot == slot) { isButton = true; break; } }
            if (!isButton) return;

            ItemStack clickedItem = event.getCurrentItem();
            double value = PhysicalCurrency.getMoneyValue(clickedItem);
            if (value > 0) {
                if (value > AtmMenu.MAX_TRANSACTION_LIMIT) {
                    player.sendMessage(Component.text("❌ 一回の取引上限を超える引き出しは行えません。", NamedTextColor.RED));
                    return;
                }

                int firstEmpty = player.getInventory().firstEmpty();
                boolean hasSpace = false;
                if (firstEmpty != -1) {
                    hasSpace = true;
                } else {
                    for (ItemStack invItem : player.getInventory().getStorageContents()) {
                        if (invItem != null && invItem.isSimilar(clickedItem) && invItem.getAmount() < invItem.getMaxStackSize()) {
                            hasSpace = true;
                            break;
                        }
                    }
                }
                if (!hasSpace) {
                    player.sendMessage(Component.text("❌ インベントリに十分な空きがありません！", NamedTextColor.RED));
                    return;
                }

                Economy eco = DomEconomyMain.getEconomy();
                if (eco == null) {
                    player.sendMessage(Component.text("❌ 銀行システムが一時的にオフラインです。", NamedTextColor.RED));
                    return;
                }
                if (eco.getBalance(player) < value) {
                    player.sendMessage(Component.text("❌ 銀行口座のデジタル残高が足りません！", NamedTextColor.RED));
                    return;
                }
                net.milkbowl.vault.economy.EconomyResponse response = eco.withdrawPlayer(player, value);
                if (response == null || !response.transactionSuccess()) {
                    player.sendMessage(Component.text("❌ 引き出し処理に失敗しました。", NamedTextColor.RED));
                    return;
                }

                player.getInventory().addItem(clickedItem.clone());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
            }
        }
    }

    @EventHandler
    public void onGuiDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory topInv = event.getView().getTopInventory();
        if (topInv.getHolder() instanceof AtmInventoryHolder holder) {
            event.setCancelled(true);
        }
    }
}