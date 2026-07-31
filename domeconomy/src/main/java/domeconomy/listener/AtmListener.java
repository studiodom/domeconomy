package domeconomy.listener;

import domeconomy.DomEconomyMain;
import domeconomy.gui.AtmInventoryHolder;
import domeconomy.gui.AtmMenu;
import domeconomy.model.PhysicalCurrency;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ATMの物理的な設置・破壊、およびGUIの操作イベントを管理するリスナークラスです。
 * 徹底した安全対策（多重クリック防止、ロールバック、保護プラグイン連携）を実装しています。
 */
public class AtmListener implements Listener {

    public static final NamespacedKey KEY_IS_ATM = new NamespacedKey("money", "is_atm");
    public static final NamespacedKey KEY_ATM_OWNER = new NamespacedKey("money", "atm_owner");

    public static final double ARMOR_STAND_Y_OFFSET = -1.2;
    public static final int ATM_CUSTOM_MODEL_DATA = 9999;

    // スレッドセーフなキャッシュマップ
    private static final Map<BlockLoc, UUID> atmCache = new ConcurrentHashMap<>(128, 0.75f);

    // パケット遅延や連打マクロによる重複引き落とし・無限増殖（Dupe）を完全に防ぐセッションロック
    private static final Set<UUID> processingPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public AtmListener() {}

    /**
     * プラグイン無効化時等に、実行中の全トランザクションを初期化しインベントリを閉じます。
     */
    public static void flushAllPendingWithdrawals() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof AtmInventoryHolder) {
                player.closeInventory();
            }
        }
        atmCache.clear();
        processingPlayers.clear();
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

    /**
     * バリアブロックに紐づけられたATM実体（ArmorStand）を取得します。
     */
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
        Collection<Entity> entities = block.getWorld().getNearbyEntities(block.getLocation().add(0.5, ARMOR_STAND_Y_OFFSET, 0.5), 0.2, 0.5, 0.2);
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

    /**
     * プレイヤーがクリックした際、起動判定や設置判定を行います。
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // =========================================================================
        // 【1】ATM（バリアブロック）の起動判定
        // 手に何を持っていても、目の前にATM（バリアブロック）があれば最優先でATMとしての起動を行います。
        // =========================================================================
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getType() == Material.BARRIER) {
                Entity atmEntity = getAtmEntity(clicked);
                if (atmEntity != null) {
                    event.setCancelled(true);

                    // 多重ログイン/多重操作ロック中の場合は無視
                    if (processingPlayers.contains(player.getUniqueId())) return;

                    AtmMenu.getInstance().openSelection(player);
                    return; // 処理完了
                }
            }
        }

        // =========================================================================
        // 【2】手元の現金を右クリックしたときのGUI直接起動判定 (ATMが目の前にない場合)
        // 手元にあるアイテムが「現金（PhysicalCurrency）」かつ、右クリック（空気中/他ブロック）ならATMを開きます。
        // =========================================================================
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (item != null && item.hasItemMeta()) {
                double currencyValue = PhysicalCurrency.getMoneyValue(item);
                if (currencyValue > 0) {
                    event.setCancelled(true);

                    // 取引処理中の場合は無視
                    if (processingPlayers.contains(player.getUniqueId())) return;

                    AtmMenu.getInstance().openSelection(player);
                    return; // 処理完了
                }
            }
        }

        // =========================================================================
        // 【3】ATMの新規設置処理 (ATM設置用アイテムを他の一般ブロックに右クリックした場合)
        // =========================================================================
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() == Material.BARRIER) return;

        if (item == null || !item.hasItemMeta()) return;

        boolean isAtm = item.getItemMeta().getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN);
        if (!isAtm) return;

        // 設置しようとしているので元のバニラ動作をキャンセル
        event.setCancelled(true);

        // 管理者（クリエイティブかつOp）のみ設置を許可
        if (!player.isOp() || player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            return;
        }

        Block target = clicked.getRelative(event.getBlockFace());
        if (target.getType() == Material.AIR || target.getType() == Material.CAVE_AIR) {

            // WorldGuardやTowny等の土地保護領域での無制限な設置を防止するため、設置イベントをシミュレートして発火 [1]
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            BlockPlaceEvent placeEvent = new BlockPlaceEvent(
                    target,
                    target.getState(),
                    clicked,
                    itemInHand,
                    player,
                    true,
                    EquipmentSlot.HAND
            );
            Bukkit.getPluginManager().callEvent(placeEvent);
            if (placeEvent.isCancelled()) {
                player.sendMessage(Component.text("❌ この場所にATMを設置する権限がありません。(保護領域)", NamedTextColor.RED));
                return;
            }

            // バリアブロックの設置と、ATMモデル付きアーマースタンドの安全な召喚
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

                // すべての装備スロットへのアクセスを無効化し、アイテムの盗み出しを防止
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    try {
                        armorStand.addDisabledSlots(slot);
                    } catch (Exception ignored) {}
                }

                armorStand.getPersistentDataContainer().set(KEY_IS_ATM, PersistentDataType.BOOLEAN, true);
                String baseLocStr = target.getWorld().getName() + "," + target.getX() + "," + target.getY() + "," + target.getZ();
                armorStand.getPersistentDataContainer().set(KEY_ATM_OWNER, PersistentDataType.STRING, baseLocStr);
                atmCache.put(getBlockLoc(target.getLocation()), armorStand.getUniqueId());
            });
        }
    }

    /**
     * ATMエンティティに直接照準が合い、右クリックされた場合の補助フォールバック処理です。
     */
    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity clicked = event.getRightClicked();
        if (clicked instanceof ArmorStand stand) {
            if (stand.getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN)) {
                event.setCancelled(true);
                // セッションロック中のプレイヤーには開かせない
                if (processingPlayers.contains(event.getPlayer().getUniqueId())) return;
                AtmMenu.getInstance().openSelection(event.getPlayer());
            }
        }
    }

    /**
     * 物理的なATM破壊（ArmorStandへのクリエイティブ管理攻撃）の処理です。
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
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

    /**
     * バリアブロックが破壊された際のATM処分処理です。
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.BARRIER) return;

        BlockLoc loc = getBlockLoc(block.getLocation());
        Player player = event.getPlayer();
        boolean isCreativeOp = player.isOp() && player.getGameMode() == org.bukkit.GameMode.CREATIVE;

        Entity atmEntity = getAtmEntity(block);
        boolean isAtmBroken = false;

        // 通常のバリアブロックが他プレイヤーによって破壊不能になってしまう判定不具合を修正
        if (atmEntity != null || atmCache.containsKey(loc)) {
            if (isCreativeOp) {
                isAtmBroken = true;
                atmCache.remove(loc);
                if (atmEntity != null) {
                    atmEntity.remove();
                }
            } else {
                event.setCancelled(true);
                player.sendMessage(Component.text("❌ ATM端末は管理者（クリエイティブ）のみ破壊可能です。", NamedTextColor.RED));
                return;
            }
        } else {
            // キャッシュ・ダイレクト検索に無かった場合は一応範囲検索するが、ATMのものでないバリアブロックは正常にスルー
            String baseLocStr = block.getWorld().getName() + "," + block.getX() + "," + block.getY() + "," + block.getZ();
            Collection<Entity> entities = block.getWorld().getNearbyEntities(block.getLocation().add(0.5, ARMOR_STAND_Y_OFFSET, 0.5), 0.5, 1.5, 0.5);
            boolean isAtmBarrier = false;
            for (Entity entity : entities) {
                if (entity instanceof ArmorStand stand) {
                    String storedLoc = stand.getPersistentDataContainer().get(KEY_ATM_OWNER, PersistentDataType.STRING);
                    if (storedLoc != null && storedLoc.equals(baseLocStr)) {
                        if (stand.getPersistentDataContainer().has(KEY_IS_ATM, PersistentDataType.BOOLEAN)) {
                            isAtmBarrier = true;
                            if (isCreativeOp) {
                                isAtmBroken = true;
                                atmCache.remove(loc);
                                stand.remove();
                            } else {
                                event.setCancelled(true);
                                player.sendMessage(Component.text("❌ ATM端末は管理者（クリエイティブ）のみ破壊可能です。", NamedTextColor.RED));
                                return;
                            }
                        }
                    }
                }
            }
            // ATMではない、通常のマップ構築用バリアブロックであればイベントをキャンセルせずスルーする
        }

        if (isAtmBroken) {
            event.setDropItems(false);
            event.getPlayer().sendMessage(Component.text("ATM端末を破壊しました。", NamedTextColor.YELLOW));
        }
    }

    /**
     * GUI内のクリックイベントを安全に処理します。
     */
    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory topInv = event.getView().getTopInventory();
        if (!(topInv.getHolder() instanceof AtmInventoryHolder holder)) return;

        String type = holder.getType();
        UUID playerUUID = player.getUniqueId();

        // ==========================================
        // 【修正・追加】預け入れ窓口 (DEPOSIT) 画面の処理
        // プレイヤーがお金を自由に入れたり並べたりできるように、クリックの全面的なキャンセルを解除します。
        // ==========================================
        if (type.equals("DEPOSIT")) {
            // クリエイティブモードによる中クリック（アイテム複製）を用いたDupeを確実にブロック
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                event.setCancelled(true);
                player.sendMessage(Component.text("❌ クリエイティブモードでの預け入れ操作はセキュリティ上禁止されています。", NamedTextColor.RED));
                return;
            }
            // 通常のアイテム配置やシフト移動等はキャンセルせずに完了させ、プレイヤーが自在にアイテムを置けるようにします
            return;
        }

        // 預け入れ(DEPOSIT)以外のGUI展開中は、プレイヤー自身のインベントリも含め、不具合・Dupe防止のために一切の操作を完全にキャンセル
        event.setCancelled(true);

        if (event.getClick() == ClickType.DOUBLE_CLICK
                || event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.SWAP_OFFHAND) {
            return;
        }

        // ==========================================
        //  操作選択画面 (SELECT)
        // ==========================================
        if (type.equals("SELECT")) {
            int slot = event.getRawSlot();
            if (slot < 0) return;
            if (event.getClickedInventory() == player.getInventory()) return;

            if (slot == AtmMenu.SLOT_DEPOSIT) {
                // 【修正】確定画面ではなく、空の預け入れGUI(DEPOSIT)を直接開く
                AtmMenu.getInstance().openDeposit(player);
            } else if (slot == AtmMenu.SLOT_WITHDRAW) {
                AtmMenu.getInstance().openWithdraw(player);
            }
            return;
        }

        // ==========================================
        //  お引き出し画面 (WITHDRAW)
        // ==========================================
        if (type.equals("WITHDRAW")) {
            int slot = event.getRawSlot();
            if (slot < 0) return;
            if (event.getClickedInventory() == player.getInventory()) return;

            boolean isButton = false;
            for (int btnSlot : AtmMenu.WITHDRAW_BUTTONS) {
                if (btnSlot == slot) {
                    isButton = true;
                    break;
                }
            }
            if (!isButton) return;

            ItemStack clickedItem = event.getCurrentItem();
            double value = PhysicalCurrency.getMoneyValue(clickedItem);
            if (value > 0) {
                if (value > AtmMenu.MAX_TRANSACTION_LIMIT) {
                    player.sendMessage(Component.text("❌ 一回の取引上限を超える引き出しは行えません。", NamedTextColor.RED));
                    return;
                }

                // インベントリメイン収納領域の「確実な空き容量確認」
                int firstEmpty = player.getInventory().firstEmpty();
                boolean hasSpace = false;
                if (firstEmpty != -1 && firstEmpty < 36) { // 36未満（0-35）が通常の収納領域
                    hasSpace = true;
                } else {
                    // 空枠がない場合でも、すでに同アイテムを保持していてスタック限界値未満であれば引き出し可能
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

                // トランザクションロックの獲得
                if (!processingPlayers.add(playerUUID)) {
                    return;
                }

                // 先に銀行口座から引き落としを実行
                EconomyResponse response = eco.withdrawPlayer(player, value);
                if (response == null || !response.transactionSuccess()) {
                    player.sendMessage(Component.text("❌ 引き出し処理に失敗しました。(Vaultエラー)", NamedTextColor.RED));
                    processingPlayers.remove(playerUUID);
                    return;
                }

                // インベントリへの追加処理。万が一追加できずに残ってしまった場合のロールバック機構
                HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(clickedItem.clone());
                if (!remaining.isEmpty()) {
                    // 入ってきたトランザクションの払い戻し (逆操作) を行い、資金のロストを防止する
                    eco.depositPlayer(player, value);
                    player.sendMessage(Component.text("❌ 物理紙幣の受け渡しに失敗したため、トランザクションを払い戻しました。", NamedTextColor.RED));
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                }

                processingPlayers.remove(playerUUID); // ロック解除
            }
        }
    }

    /**
     * 【新規・修正】預け入れ画面が閉じられたときに自動で集計とチャージを実行します。
     * 🟢 確定ボタン等を必要としない、最も直感的で不正のない安全な預入処理です。
     */
    @EventHandler
    public void onGuiClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory topInv = event.getInventory();
        if (!(topInv.getHolder() instanceof AtmInventoryHolder holder)) return;

        String type = holder.getType();
        UUID playerUUID = player.getUniqueId();

        if (type.equals("DEPOSIT")) {
            // クローズ処理における重複発火を防ぐためロックを獲得
            if (!processingPlayers.add(playerUUID)) return;

            double totalDeposit = 0;
            List<ItemStack> currencyItems = new ArrayList<>();
            List<ItemStack> nonCurrencyItems = new ArrayList<>();

            // 1. 中身を完全にスロットスキャン
            for (ItemStack item : topInv.getContents()) {
                if (item == null || item.getType() == Material.AIR) continue;

                double val = PhysicalCurrency.getMoneyValue(item);
                if (val > 0) {
                    totalDeposit += (val * item.getAmount());
                    currencyItems.add(item.clone());
                } else {
                    // 物理通貨ではない無関係なアイテムは返却用にキープ
                    nonCurrencyItems.add(item.clone());
                }
            }

            // 2. スキャン完了後、不整合・増殖(Dupe)の余地をなくすため、即時にGUI内を完全に消去
            topInv.clear();

            // 3. 各条件判定およびVault預金トランザクション処理
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
                // クリエイティブプレイヤーの場合は、入金を一切行わずアイテムをすべて返却
                nonCurrencyItems.addAll(currencyItems);
                player.sendMessage(Component.text("❌ クリエイティブモードのため、預け入れは行われずアイテムがすべて返却されました。", NamedTextColor.RED));
            } else if (totalDeposit > 0) {
                if (totalDeposit > AtmMenu.MAX_TRANSACTION_LIMIT) {
                    player.sendMessage(Component.text("❌ 取引上限（" + String.format("%,.0f", AtmMenu.MAX_TRANSACTION_LIMIT) + "円）を超える預け入れは一度に行えません。投入されたお金を返却しました。", NamedTextColor.RED));
                    nonCurrencyItems.addAll(currencyItems);
                } else {
                    Economy eco = DomEconomyMain.getEconomy();
                    if (eco == null) {
                        player.sendMessage(Component.text("❌ 銀行システムが一時的にオフラインです。お預かりしたお金をすべて返却しました。", NamedTextColor.RED));
                        nonCurrencyItems.addAll(currencyItems);
                    } else {
                        // Vaultを介して入金処理を実施
                        EconomyResponse response = eco.depositPlayer(player, totalDeposit);
                        if (response != null && response.transactionSuccess()) {
                            player.sendMessage(Component.text("💰 合計 " + String.format("%,.0f", totalDeposit) + "円 をデジタル口座に入金しました！", NamedTextColor.GREEN));
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                        } else {
                            player.sendMessage(Component.text("❌ 口座入金システムのエラーにより、投入されたお金をすべて返却しました。", NamedTextColor.RED));
                            nonCurrencyItems.addAll(currencyItems);
                        }
                    }
                }
            }

            // 4. 非物理通貨（無関係なアイテム）および、預入に失敗・キャンセルされた物理通貨をプレイヤーに返却
            if (!nonCurrencyItems.isEmpty()) {
                for (ItemStack rem : nonCurrencyItems) {
                    HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(rem);
                    // プレイヤーのインベントリから溢れた場合は、他プレイヤーに盗まれないよう、足元へ安全に自然ドロップして保護
                    for (ItemStack drop : remaining.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }

            processingPlayers.remove(playerUUID); // セッションロックの解放
        }
    }

    /**
     * GUI展開中のドラッグによるアイテム移動を防止します。（預け入れGUI内でのドラッグ配置のみ許可）
     */
    @EventHandler
    public void onGuiDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Inventory topInv = event.getView().getTopInventory();
        if (topInv.getHolder() instanceof AtmInventoryHolder holder) {
            String type = holder.getType();
            // DEPOSIT (預け入れ窓口) 以外の画面のみドラッグ操作を一律キャンセルして不正をブロック
            if (!type.equals("DEPOSIT")) {
                event.setCancelled(true);
            }
        }
    }
}