package org.xyplugin.xycore.internal.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.Plugin;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.api.economy.EconomyResult;
import org.xyplugin.xycore.api.economy.EconomyService;

import java.lang.reflect.Method;

/** 不引用 Vault 编译类的反射式经济适配器。 */
public final class VaultEconomyService implements EconomyService {

    private final XyCorePlugin plugin;
    private volatile Object provider;
    private volatile boolean available;

    public VaultEconomyService(XyCorePlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    public void refresh() {
        try {
            Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
            ClassLoader loader = vault == null ? getClass().getClassLoader() : vault.getClass().getClassLoader();
            Class<?> economyType = Class.forName("net.milkbowl.vault.economy.Economy", true, loader);
            @SuppressWarnings("unchecked")
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration((Class) economyType);
            provider = registration == null ? null : registration.getProvider();
            available = provider != null;
        } catch (Exception failure) {
            provider = null;
            available = false;
        }
    }

    @Override
    public boolean isAvailable() {
        return available && provider != null;
    }

    @Override
    public String getProviderName() {
        return provider == null ? "unavailable" : provider.getClass().getName();
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        if (!isAvailable() || player == null) return 0D;
        try {
            Object value = invoke("getBalance", new Class<?>[]{OfflinePlayer.class}, player);
            return value instanceof Number ? ((Number) value).doubleValue() : 0D;
        } catch (Exception failure) {
            return 0D;
        }
    }

    @Override
    public EconomyResult deposit(OfflinePlayer player, double amount, String reason) {
        return transact("depositPlayer", player, amount);
    }

    @Override
    public EconomyResult withdraw(OfflinePlayer player, double amount, String reason) {
        return transact("withdrawPlayer", player, amount);
    }

    private EconomyResult transact(String method, OfflinePlayer player, double amount) {
        if (!isAvailable()) return EconomyResult.failure(EconomyResult.Status.UNAVAILABLE, "Vault unavailable", 0D);
        if (player == null || Double.isNaN(amount) || Double.isInfinite(amount) || amount <= 0D) {
            return EconomyResult.failure(EconomyResult.Status.INVALID_AMOUNT, "Invalid amount", getBalance(player));
        }
        try {
            Object response = invoke(method, new Class<?>[]{OfflinePlayer.class, double.class}, player, amount);
            boolean success = response != null && Boolean.TRUE.equals(invoke(response, "transactionSuccess"));
            double balance = getBalance(player);
            return success
                    ? EconomyResult.success(amount, balance)
                    : EconomyResult.failure(EconomyResult.Status.FAILED, String.valueOf(response), balance);
        } catch (Exception failure) {
            return EconomyResult.failure(EconomyResult.Status.FAILED, failure.getMessage(), getBalance(player));
        }
    }

    private Object invoke(String method, Class<?>[] types, Object... args) throws Exception {
        Method target = provider.getClass().getMethod(method, types);
        return target.invoke(provider, args);
    }

    private Object invoke(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }
}
