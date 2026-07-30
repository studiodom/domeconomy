package domeconomy;

import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import domeconomy.command.BankCommand;
import domeconomy.listener.AtmListener;

public final class DomEconomyMain extends JavaPlugin implements Listener {

    private static DomEconomyMain instance;
    private static Economy econ = null;

    @Override
    public void onEnable() {
        instance = this;

        BankCommand bankCommand = new BankCommand();
        if (this.getCommand("bank") != null) {
            this.getCommand("bank").setExecutor(bankCommand);
            this.getCommand("bank").setTabCompleter(bankCommand);
        }
        if (this.getCommand("atm") != null) {
            this.getCommand("atm").setExecutor(bankCommand);
            this.getCommand("atm").setTabCompleter(bankCommand);
        }

        getServer().getPluginManager().registerEvents(new AtmListener(), this);
        getServer().getPluginManager().registerEvents(this, this);

        updateEconomyProvider();

        getLogger().info("経済コアプラグイン (domeconomy) が正常に有効化されました！");
    }

    @Override
    public void onDisable() {
        AtmListener.flushAllPendingWithdrawals();
        getLogger().info("経済コアプラグイン (domeconomy) が無効化されました");
    }

    @EventHandler
    public void onServiceRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService() == Economy.class) {
            updateEconomyProvider();
        }
    }

    @EventHandler
    public void onServiceUnregister(ServiceUnregisterEvent event) {
        if (event.getProvider().getService() == Economy.class) {
            updateEconomyProvider();
        }
    }

    private void updateEconomyProvider() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            econ = null;
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        econ = rsp != null ? rsp.getProvider() : null;
    }

    public static DomEconomyMain getInstance() {
        return instance;
    }

    public static Economy getEconomy() {
        return econ;
    }
}