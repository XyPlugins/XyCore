package org.xyplugin.xycore.internal;

import org.bukkit.entity.Player;
import org.xyplugin.xycore.XyCorePlugin;
import org.xyplugin.xycore.api.XyCoreApi;
import org.xyplugin.xycore.api.attribute.AttributeService;
import org.xyplugin.xycore.api.client.ClientBridgeService;
import org.xyplugin.xycore.api.data.PlayerDataManager;
import org.xyplugin.xycore.api.economy.EconomyService;
import org.xyplugin.xycore.api.item.ItemLibraryService;
import org.xyplugin.xycore.api.item.ItemTagService;
import org.xyplugin.xycore.api.placeholder.PlaceholderRegistry;
import org.xyplugin.xycore.api.service.ReloadManager;
import org.xyplugin.xycore.api.service.ServiceRegistry;
import org.xyplugin.xycore.api.storage.StorageManager;
import org.xyplugin.xycore.internal.attribute.AttributePlusPlaceholderService;
import org.xyplugin.xycore.internal.attribute.UnavailableAttributeService;
import org.xyplugin.xycore.internal.client.DragonCoreClientBridgeService;
import org.xyplugin.xycore.internal.client.UnavailableClientBridgeService;
import org.xyplugin.xycore.internal.data.PlayerDataManagerImpl;
import org.xyplugin.xycore.internal.data.ReloadManagerImpl;
import org.xyplugin.xycore.internal.data.ServiceRegistryImpl;
import org.xyplugin.xycore.internal.data.StorageManagerImpl;
import org.xyplugin.xycore.internal.economy.UnavailableEconomyService;
import org.xyplugin.xycore.internal.economy.VaultEconomyService;
import org.xyplugin.xycore.internal.item.ItemLibraryServiceImpl;
import org.xyplugin.xycore.internal.item.MythicMobsItemProvider;
import org.xyplugin.xycore.internal.item.NmsItemTagService;
import org.xyplugin.xycore.internal.item.VanillaItemProvider;
import org.xyplugin.xycore.internal.placeholder.PlaceholderRegistryImpl;

/** 组装 Core 所有默认服务的内部实现。 */
public final class CoreApiImpl implements XyCoreApi {

    private final XyCorePlugin plugin;
    private final StorageManagerImpl storage;
    private final PlayerDataManagerImpl playerData;
    private final ServiceRegistryImpl services;
    private final PlaceholderRegistryImpl placeholders;
    private final ReloadManagerImpl reloads;
    private final ItemLibraryServiceImpl items;
    private final ItemTagService itemTags;
    private EconomyService economy;
    private AttributeService attributes;
    private final ClientBridgeService clientBridge;

    public CoreApiImpl(XyCorePlugin plugin) {
        this.plugin = plugin;
        storage = new StorageManagerImpl(plugin);
        playerData = new PlayerDataManagerImpl(plugin, storage);
        services = new ServiceRegistryImpl();
        placeholders = new PlaceholderRegistryImpl();
        reloads = new ReloadManagerImpl(plugin);
        items = new ItemLibraryServiceImpl();
        itemTags = new NmsItemTagService(plugin);

        items.registerProvider(new VanillaItemProvider());
        if (plugin.getConfig().getBoolean("integrations.mythicmobs", true)
                && plugin.getServer().getPluginManager().getPlugin("MythicMobs") != null) {
            MythicMobsItemProvider mythic = new MythicMobsItemProvider(plugin);
            if (mythic.isAvailable()) items.registerProvider(mythic);
        }

        economy = createEconomy();
        attributes = createAttributes();
        registerCorePlaceholders();
        clientBridge = createClientBridge();
        registerServices();
    }

    private EconomyService createEconomy() {
        if (!plugin.getConfig().getBoolean("integrations.vault", true)) return new UnavailableEconomyService();
        VaultEconomyService vault = new VaultEconomyService(plugin);
        return vault.isAvailable() ? vault : new UnavailableEconomyService();
    }

    private AttributeService createAttributes() {
        if (!plugin.getConfig().getBoolean("integrations.attributeplus.enabled", true)) {
            return new UnavailableAttributeService();
        }
        AttributePlusPlaceholderService service = new AttributePlusPlaceholderService(plugin);
        return service.isAvailable() ? service : new UnavailableAttributeService();
    }

    private ClientBridgeService createClientBridge() {
        if (!plugin.getConfig().getBoolean("integrations.dragoncore.enabled", true)
                || plugin.getServer().getPluginManager().getPlugin("DragonCore") == null) {
            return new UnavailableClientBridgeService();
        }
        DragonCoreClientBridgeService service = new DragonCoreClientBridgeService(plugin, placeholders);
        return service.isAvailable() ? service : new UnavailableClientBridgeService();
    }

    private void registerServices() {
        services.register(PlayerDataManager.class, playerData);
        services.register(StorageManager.class, storage);
        services.register(ServiceRegistry.class, services);
        services.register(EconomyService.class, economy);
        services.register(AttributeService.class, attributes);
        services.register(ClientBridgeService.class, clientBridge);
        services.register(ItemLibraryService.class, items);
        services.register(ItemTagService.class, itemTags);
        services.register(PlaceholderRegistry.class, placeholders);
        services.register(ReloadManager.class, reloads);
    }

    private void registerCorePlaceholders() {
        placeholders.register(new org.xyplugin.xycore.api.placeholder.PlaceholderProvider() {
            @Override
            public String getNamespace() {
                return "xycore";
            }

            @Override
            public String resolve(Player player, String params) {
                if (params == null) return "";
                switch (params.toLowerCase()) {
                    case "uuid":
                        return player == null ? "" : player.getUniqueId().toString();
                    case "data_loaded":
                        return player != null && playerData.isReady(player.getUniqueId()) ? "true" : "false";
                    case "storage":
                        return storage.getBackendName();
                    case "economy_available":
                        return String.valueOf(economy.isAvailable());
                    case "attribute_available":
                        return String.valueOf(attributes.isAvailable());
                    case "dragoncore_available":
                        return String.valueOf(clientBridge != null && clientBridge.isAvailable());
                    case "item_providers":
                        StringBuilder providers = new StringBuilder();
                        for (org.xyplugin.xycore.api.item.ItemProvider provider : items.getProviders()) {
                            if (providers.length() > 0) providers.append(',');
                            providers.append(provider.getId());
                        }
                        return providers.toString();
                    case "version":
                        return getVersion();
                    default:
                        return "";
                }
            }
        });
    }

    public void refreshOptionalIntegrations() {
        if (economy instanceof VaultEconomyService) {
            ((VaultEconomyService) economy).refresh();
        }
    }

    @Override
    public PlayerDataManager getPlayerData() {
        return playerData;
    }

    @Override
    public StorageManager getStorage() {
        return storage;
    }

    public StorageManagerImpl getStorageInternal() {
        return storage;
    }

    public PlayerDataManagerImpl getPlayerDataInternal() {
        return playerData;
    }

    @Override
    public ServiceRegistry getServices() {
        return services;
    }

    @Override
    public EconomyService getEconomy() {
        return economy;
    }

    @Override
    public AttributeService getAttributes() {
        return attributes;
    }

    @Override
    public ClientBridgeService getClientBridge() {
        return clientBridge;
    }

    @Override
    public ItemLibraryService getItems() {
        return items;
    }

    @Override
    public ItemTagService getItemTags() {
        return itemTags;
    }

    @Override
    public PlaceholderRegistry getPlaceholders() {
        return placeholders;
    }

    @Override
    public ReloadManager getReloads() {
        return reloads;
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }
}
