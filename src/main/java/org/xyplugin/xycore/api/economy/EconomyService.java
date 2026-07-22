package org.xyplugin.xycore.api.economy;

import org.bukkit.OfflinePlayer;

/** 统一货币接口，初代实现通过 Vault 适配。 */
public interface EconomyService {

    boolean isAvailable();

    String getProviderName();

    double getBalance(OfflinePlayer player);

    EconomyResult deposit(OfflinePlayer player, double amount, String reason);

    EconomyResult withdraw(OfflinePlayer player, double amount, String reason);
}
