package org.xyplugin.xycore.api.economy;

/** 货币操作的明确结果，避免业务插件只判断异常。 */
public final class EconomyResult {

    public enum Status {
        SUCCESS,
        UNAVAILABLE,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS,
        FAILED
    }

    private final Status status;
    private final double amount;
    private final double balance;
    private final String message;

    private EconomyResult(Status status, double amount, double balance, String message) {
        this.status = status;
        this.amount = amount;
        this.balance = balance;
        this.message = message;
    }

    public static EconomyResult success(double amount, double balance) {
        return new EconomyResult(Status.SUCCESS, amount, balance, "success");
    }

    public static EconomyResult failure(Status status, String message, double balance) {
        return new EconomyResult(status, 0D, balance, message);
    }

    public Status getStatus() {
        return status;
    }

    public double getAmount() {
        return amount;
    }

    public double getBalance() {
        return balance;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
