package org.xyplugin.xycore.internal.economy;

import org.bukkit.OfflinePlayer;
import org.xyplugin.xycore.api.economy.EconomyResult;
import org.xyplugin.xycore.api.economy.EconomyService;

/** Vault 不可用时的安全空实现。 */
public final class UnavailableEconomyService implements EconomyService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String getProviderName() {
        return "unavailable";
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return 0D;
    }

    @Override
    public EconomyResult deposit(OfflinePlayer player, double amount, String reason) {
        return EconomyResult.failure(EconomyResult.Status.UNAVAILABLE, "Vault unavailable", 0D);
    }

    @Override
    public EconomyResult withdraw(OfflinePlayer player, double amount, String reason) {
        return EconomyResult.failure(EconomyResult.Status.UNAVAILABLE, "Vault unavailable", 0D);
    }
}
