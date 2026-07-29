package domeconomy;

import org.bukkit.plugin.java.JavaPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import domeconomy.command.BankCommand;
import domeconomy.command.PayCommand;
import domeconomy.listener.AtmListener;
import domeconomy.listener.PayListener;

public final class DomEconomyMain extends JavaPlugin {

    private static DomEconomyMain instance;
    private static Economy econ = null;

    @Override
    public void onEnable() {
        instance = this;

        if (!setupEconomy()) {
            getLogger().severe("Vault または経済プラグインが見つかりません！プラグインを無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        BankCommand bankCommand = new BankCommand();
        if (this.getCommand("bank") != null) {
            this.getCommand("bank").setExecutor(bankCommand);
            this.getCommand("bank").setTabCompleter(bankCommand);
        }
        if (this.getCommand("atm") != null) {
            this.getCommand("atm").setExecutor(bankCommand);
            this.getCommand("atm").setTabCompleter(bankCommand);
        }

        PayCommand payCommand = new PayCommand();
        if (this.getCommand("pay") != null) {
            this.getCommand("pay").setExecutor(payCommand);
            this.getCommand("pay").setTabCompleter(payCommand);
        }

        getServer().getPluginManager().registerEvents(new AtmListener(this), this);
        getServer().getPluginManager().registerEvents(new PayListener(), this);

        getLogger().info("経済コアプラグイン (domeconomy) が正常に有効化されました！");
    }

    @Override
    public void onDisable() {
        AtmListener.flushAllPendingWithdrawals();
        getLogger().info("経済コアプラグイン (domeconomy) が無効化されました");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static DomEconomyMain getInstance() {
        return instance;
    }

    public static Economy getEconomy() {
        return econ;
    }
}