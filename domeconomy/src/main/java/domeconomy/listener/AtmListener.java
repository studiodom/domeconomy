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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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

    private static final Map<String, UUID> atmCache = new HashMap<>();
    private static final Map<UUID, Double> cachedBalances = new HashMap<>();
    private static final Map<UUID, Double> pendingWithdrawals = new HashMap<>();

    private final DomEconomyMain plugin;

    public AtmListener(DomEconomyMain plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, Double> entry : pendingWithdrawals.entrySet()) {
                double pending = entry.getValue();
                if (pending > 0) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        DomEconomyMain.getEconomy().withdrawPlayer(player, pending);
                        entry.setValue(0.0);
                    }
                }
            }
        }, 10L, 10L);
    }

    public static void initializeWithdrawSession(Player player) {
        double balance = DomEconomyMain.getEconomy().getBalance(player);
        cachedBalances.put(player.getUniqueId(), balance);
        pendingWithdrawals.put(player.getUniqueId(), 0.0);
    }

    public static void flushAllPendingWithdrawals() {
        for (Map.Entry<UUID, Double> entry : pendingWithdrawals.entrySet()) {
            double pending = entry.getValue();
            if (pending > 0) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    DomEconomyMain.getEconomy().withdrawPlayer(player, pending);
                }
            }
        }
        pendingWithdrawals.clear();
        cachedBalances.clear();
    }

    public static String getBlockLocString(Location loc) {
        if (loc == null) return "";
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public static ItemStack createAtmItem() {
        ItemStack atm = new ItemStack(Material.NETHERITE_INGOT);
        ItemMeta meta = atm.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("🏛️ 銀行 ATM端末", NamedTextColor.AQUA));
            meta.setCustomModelData(9999);
            meta.getPersistentDataContainer().set(KEY_IS_ATM, PersistentDataType.BOOLEAN, true);
            atm.setItemMeta(meta);
        }
        return atm;
    }

    private Entity getAtmEntity(Block block) {
        String baseLocStr = getBlockLocString(block.getLocation());
        UUID uuid = atmCache.get(baseLocStr);
        if (uuid != null) {
            Entity entity = org.bukkit.Bukkit.getEntity(uuid);
            if (entity instanceof ArmorStand stand && stand.getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN)) {
                return entity;
            }
        }
        Collection<Entity> entities = block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 1.5, 2.0, 1.5);
        for (Entity entity : entities) {
            if (entity instanceof ArmorStand stand) {
                String storedLoc = stand.getPersistentDataContainer().get(KEY_ATM_OWNER, PersistentDataType.STRING);
                if (storedLoc != null && storedLoc.equals(baseLocStr)) {
                    if (stand.getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN)) {
                        atmCache.put(baseLocStr, stand.getUniqueId());
                        return stand;
                    }
                }
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (!item.hasItemMeta()) return;

        boolean isAtm = item.getItemMeta().getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN);
        if (!isAtm) return;

        event.setCancelled(false);
        Block baseBlock = event.getBlockPlaced();
        baseBlock.setType(Material.BARRIER);

        Player player = event.getPlayer();
        float yaw = player.getLocation().getYaw();

        Location spawnLoc = baseBlock.getLocation().add(0.5, -1.2, 0.5);
        spawnLoc.setYaw(yaw + 180f);

        baseBlock.getWorld().spawn(spawnLoc, ArmorStand.class, armorStand -> {
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
            String baseLocStr = getBlockLocString(baseBlock.getLocation());
            armorStand.getPersistentDataContainer().set(KEY_ATM_OWNER, PersistentDataType.STRING, baseLocStr);
            atmCache.put(baseLocStr, armorStand.getUniqueId());
        });
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;

        ItemStack item = event.getItem();

        if (item != null && item.hasItemMeta()) {
            if (item.getItemMeta().getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN)) {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    Block clicked = event.getClickedBlock();
                    if (clicked != null && clicked.getType() != Material.BARRIER) {
                        Block target = clicked.getRelative(event.getBlockFace());
                        if (target.getType() == Material.AIR || target.getType() == Material.CAVE_AIR) {
                            BlockPlaceEvent fake = new BlockPlaceEvent(target, target.getState(), clicked, item, event.getPlayer(), true, EquipmentSlot.HAND);
                            org.bukkit.Bukkit.getPluginManager().callEvent(fake);
                            if (!fake.isCancelled() && event.getPlayer().getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                                item.setAmount(item.getAmount() - 1);
                                event.setCancelled(true);
                            }
                        }
                    }
                }
                return;
            }

            if (PhysicalCurrency.getMoneyValue(item) > 0) {
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                    if (event.getClickedBlock().getType().isInteractable() && !event.getPlayer().isSneaking()) {
                        return;
                    }
                }
                event.setCancelled(true);
                new AtmMenu().openSelection(event.getPlayer());
                return;
            }
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block == null || block.getType() != Material.BARRIER) return;

            Entity atmEntity = getAtmEntity(block);
            if (atmEntity != null) {
                event.setCancelled(true);
                new AtmMenu().openSelection(event.getPlayer());
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
                new AtmMenu().openSelection(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof ArmorStand stand) {
            if (stand.getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN)) {
                event.setCancelled(true);
                if (event.getDamager() instanceof Player player && player.isOp()) {
                    Block block = stand.getLocation().add(0, 1.2, 0).getBlock();
                    if (block.getType() == Material.BARRIER) {
                        block.setType(Material.AIR);
                    }
                    String baseLocStr = getBlockLocString(block.getLocation());
                    atmCache.remove(baseLocStr);
                    stand.remove();
                    player.sendMessage(Component.text("ATM端末を管理権限で強制破壊しました。", NamedTextColor.RED));
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BARRIER) return;

        Entity atmEntity = getAtmEntity(block);
        boolean isAtmBroken = false;

        if (atmEntity != null) {
            isAtmBroken = true;
            String baseLocStr = getBlockLocString(block.getLocation());
            atmCache.remove(baseLocStr);
            atmEntity.remove();
        }

        if (isAtmBroken) {
            event.setDropItems(false);
            block.setType(Material.AIR);
            event.getPlayer().sendMessage(Component.text("ATM端末を破壊しました。", NamedTextColor.YELLOW));
        }
    }

    @EventHandler
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(java.util.List<Block> blocks) {
        for (Block block : blocks) {
            if (block.getType() == Material.BARRIER) {
                Entity atmEntity = getAtmEntity(block);
                if (atmEntity != null) {
                    String baseLocStr = getBlockLocString(block.getLocation());
                    atmCache.remove(baseLocStr);
                    atmEntity.remove();
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Double pending = pendingWithdrawals.remove(uuid);
        if (pending != null && pending > 0) {
            DomEconomyMain.getEconomy().withdrawPlayer(player, pending);
        }
        cachedBalances.remove(uuid);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof AtmInventoryHolder holder)) return;

        String type = holder.getType();

        if (type.equals("SELECT")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0) return;
            if (event.getClickedInventory() == player.getInventory()) return;
            if (slot == 11) new AtmMenu().openDeposit(player);
            if (slot == 15) new AtmMenu().openWithdraw(player);
            return;
        }

        if (type.equals("DEPOSIT")) {
            int slot = event.getRawSlot();
            if (slot < 0) return;

            if (event.getClick().isShiftClick() && event.getClickedInventory() == player.getInventory()) {
                ItemStack item = event.getCurrentItem();
                if (item != null && item.getType() != Material.AIR) {
                    if (PhysicalCurrency.getMoneyValue(item) == 0) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("お金以外のアイテムは投入できません！", NamedTextColor.RED));
                        return;
                    }
                }
            }

            if (event.getClickedInventory() != player.getInventory() && slot >= 0 && slot < 27) {
                ItemStack draggedItem = event.getCursor();
                if (draggedItem != null && draggedItem.getType() != Material.AIR) {
                    if (PhysicalCurrency.getMoneyValue(draggedItem) == 0) {
                        event.setCancelled(true);
                        player.sendMessage(Component.text("お金以外のアイテムは投入できません！", NamedTextColor.RED));
                        return;
                    }
                }

                if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                    ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                    if (hotbarItem != null && hotbarItem.getType() != Material.AIR) {
                        if (PhysicalCurrency.getMoneyValue(hotbarItem) == 0) {
                            event.setCancelled(true);
                            player.sendMessage(Component.text("お金以外のアイテムは投入できません！", NamedTextColor.RED));
                            return;
                        }
                    }
                }
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
                UUID uuid = player.getUniqueId();
                double cachedBal = cachedBalances.getOrDefault(uuid, 0.0);
                if (cachedBal < value) {
                    player.sendMessage(Component.text("銀行口座のデジタル残高が足りません！", NamedTextColor.RED));
                    return;
                }
                cachedBalances.put(uuid, cachedBal - value);
                pendingWithdrawals.put(uuid, pendingWithdrawals.getOrDefault(uuid, 0.0) + value);

                HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(clickedItem.clone());
                for (ItemStack rem : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rem);
                }
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
            }
        }
    }

    @EventHandler
    public void onGuiDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory topInv = event.getView().getTopInventory();
        if (topInv.getHolder() instanceof AtmInventoryHolder holder) {
            String type = holder.getType();
            if (type.equals("SELECT") || type.equals("WITHDRAW")) {
                event.setCancelled(true);
                return;
            }
            if (type.equals("DEPOSIT")) {
                for (ItemStack item : event.getNewItems().values()) {
                    if (item != null && item.getType() != Material.AIR) {
                        if (PhysicalCurrency.getMoneyValue(item) == 0) {
                            event.setCancelled(true);
                            player.sendMessage(Component.text("お金以外のアイテムは投入できません！", NamedTextColor.RED));
                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onAtmGuiClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory topInv = event.getView().getTopInventory();
        if (topInv.getHolder() instanceof AtmInventoryHolder holder) {
            String type = holder.getType();
            if (type.equals("DEPOSIT")) {
                Inventory inv = event.getInventory();
                double totalDeposit = 0;
                for (int i = 0; i < 27; i++) {
                    ItemStack item = inv.getItem(i);
                    if (item == null) continue;
                    double value = PhysicalCurrency.getMoneyValue(item);
                    if (value > 0) {
                        totalDeposit += (value * item.getAmount());
                        inv.setItem(i, null);
                    } else {
                        HashMap<Integer, ItemStack> rawRemaining = player.getInventory().addItem(item.clone());
                        for (ItemStack rem : rawRemaining.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation(), rem);
                        }
                        inv.setItem(i, null);
                    }
                }
                if (totalDeposit > 0) {
                    DomEconomyMain.getEconomy().depositPlayer(player, totalDeposit);
                    player.sendMessage(Component.text("合計 " + String.format("%.0f", totalDeposit) + "円 をデジタル口座に入金しました！", NamedTextColor.GREEN));
                }
            }

            if (type.equals("WITHDRAW")) {
                UUID uuid = player.getUniqueId();
                Double pending = pendingWithdrawals.remove(uuid);
                if (pending != null && pending > 0) {
                    DomEconomyMain.getEconomy().withdrawPlayer(player, pending);
                }
                cachedBalances.remove(uuid);
            }
        }
    }
}