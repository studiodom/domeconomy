package domeconomy.gui;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class AtmInventoryHolder implements InventoryHolder {
    private final String type;
    private Inventory inventory;

    public AtmInventoryHolder(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            inventory = Bukkit.createInventory(this, 9);
        }
        return inventory;
    }
}