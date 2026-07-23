package org.xyplugin.xycore.internal.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.api.data.PlayerSession;
import org.xyplugin.xycore.internal.death.DeathKeepModule;
import org.xyplugin.xycore.internal.lore.LoreCommandBindService;
import org.xyplugin.xycore.internal.module.CoreModule;
import org.xyplugin.xycore.internal.permission.WorldPermissionModule;
import org.xyplugin.xycore.internal.protect.WorldProtectModule;
import org.xyplugin.xycore.internal.time.AlwaysDayModule;
import org.xyplugin.xycore.internal.weather.NoRainModule;
import org.xyplugin.xycore.internal.pvp.PvpProtectModule;

/** XyCore 管理命令。 */
public final class CoreCommand implements CommandExecutor {

    private final XyCorePlugin plugin;

    public CoreCommand(XyCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("xycore.admin")) {
            sender.sendMessage(color("&c你没有权限使用 XyCore 管理命令。"));
            return true;
        }
        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "status":
                status(sender);
                return true;
            case "reload":
                plugin.reloadCore();
                sender.sendMessage(color("&aXyCore 配置和已注册 Xy 扩展已重载。"));
                return true;
            case "modules":
            case "module":
                modules(sender);
                return true;
            case "save":
                save(sender, args);
                return true;
            case "info":
                info(sender, args);
                return true;
            default:
                help(sender);
                return true;
        }
    }

    private void status(CommandSender sender) {
        sender.sendMessage(color("&bXyCore &f" + plugin.getApi().getVersion()));
        sender.sendMessage(color("&7存储: &f" + plugin.getApi().getStorage().getBackendName()));
        sender.sendMessage(color("&7在线数据会话: &f" + plugin.getApi().getPlayerData().getActiveSessionCount()));
        sender.sendMessage(color("&7Vault: &f" + plugin.getApi().getEconomy().getProviderName()));
        sender.sendMessage(color("&7AttributePlus: &f" + plugin.getApi().getAttributes().getProviderName()));
        sender.sendMessage(color("&7客户端桥接: &f" + plugin.getApi().getClientBridge().getProviderName()));
        sender.sendMessage(color("&7物品提供器: &f" + plugin.getApi().getItems().getProviders().size()));
        sender.sendMessage(color("&7可重载扩展: &f" + plugin.getApi().getReloads().getIds()));
        sender.sendMessage(color("&7模块: &f" + (plugin.getModuleManager() == null
                ? "[]" : plugin.getModuleManager().getStates())));
        sender.sendMessage(color("&7LoreCommandBind 规则: &f" + (plugin.getLoreCommandBind() == null
                ? 0 : plugin.getLoreCommandBind().size())));
    }

    private void modules(CommandSender sender) {
        if (plugin.getModuleManager() == null) {
            sender.sendMessage(color("&c模块管理器尚未初始化。"));
            return;
        }
        sender.sendMessage(color("&b=== XyCore Modules ==="));
        for (CoreModule module : plugin.getModuleManager().getModules()) {
            String extra = "";
            if (module instanceof LoreCommandBindService) {
                extra = " &7规则: &f" + ((LoreCommandBindService) module).size();
            } else if (module instanceof WorldProtectModule) {
                extra = " &7世界: &f" + ((WorldProtectModule) module).getWorldCount();
            } else if (module instanceof WorldPermissionModule) {
                WorldPermissionModule worldPermission = (WorldPermissionModule) module;
                extra = " &7世界: &f" + worldPermission.getWorldCount()
                        + " &7权限附件: &f" + worldPermission.getActiveAttachmentCount();
            } else if (module instanceof DeathKeepModule) {
                extra = " &7世界: &f" + ((DeathKeepModule) module).getWorldCount();
            } else if (module instanceof PvpProtectModule) {
                extra = " &7世界: &f" + ((PvpProtectModule) module).getWorldCount();
            } else if (module instanceof AlwaysDayModule) {
                extra = " &7世界: &f" + ((AlwaysDayModule) module).getWorldCount();
            } else if (module instanceof NoRainModule) {
                extra = " &7世界: &f" + ((NoRainModule) module).getWorldCount();
            }
            sender.sendMessage(color("&e" + module.getId() + " &7" + (module.isEnabled() ? "启用" : "关闭")
                    + " &8(" + module.getConfigResourcePath() + ")" + extra));
        }
    }

    private void save(CommandSender sender, String[] args) {
        if (args.length < 2 || "all".equalsIgnoreCase(args[1])) {
            plugin.getApi().getPlayerData().saveAll().whenComplete((ignored, failure) ->
                    sendLater(sender, failure == null ? "&a全部玩家数据已提交保存。" : "&c玩家数据保存失败。"));
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage(color("&c玩家不在线。"));
            return;
        }
        plugin.getApi().getPlayerData().save(player.getUniqueId()).whenComplete((ignored, failure) ->
                sendLater(sender, failure == null ? "&a玩家数据已提交保存。" : "&c玩家数据保存失败。"));
    }

    private void info(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(color("&e用法: /xycore info <玩家>"));
            return;
        }
        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage(color("&c玩家不在线。"));
            return;
        }
        PlayerSession session = plugin.getApi().getPlayerData().getSession(player.getUniqueId());
        sender.sendMessage(color("&7UUID: &f" + player.getUniqueId()));
        sender.sendMessage(color("&7数据状态: &f" + (session != null && session.isReady() ? "READY" : "LOADING")));
        sender.sendMessage(color("&7已加载模块: &f" + (session == null ? "[]" : session.getLoadedModules())));
    }

    private void help(CommandSender sender) {
        sender.sendMessage(color("&b=== XyCore ==="));
        sender.sendMessage(color("&e/xycore status &7查看 Core 状态"));
        sender.sendMessage(color("&e/xycore reload &7重载配置和已注册扩展"));
        sender.sendMessage(color("&e/xycore modules &7查看模块状态"));
        sender.sendMessage(color("&e/xycore save [all|玩家] &7保存玩家数据"));
        sender.sendMessage(color("&e/xycore info <玩家> &7查看数据会话"));
    }

    private String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private void sendLater(CommandSender sender, String message) {
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(color(message)));
    }
}
