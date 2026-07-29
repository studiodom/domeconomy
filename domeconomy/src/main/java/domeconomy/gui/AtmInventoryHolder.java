package domeconomy.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class AtmInventoryHolder implements InventoryHolder {
    private final String type;

    public AtmInventoryHolder(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}