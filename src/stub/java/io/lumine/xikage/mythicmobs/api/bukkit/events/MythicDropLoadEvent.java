package io.lumine.xikage.mythicmobs.api.bukkit.events;

import io.lumine.xikage.mythicmobs.drops.Drop;
import io.lumine.xikage.mythicmobs.drops.droppables.CustomDrop;
import io.lumine.xikage.mythicmobs.io.MythicLineConfig;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class MythicDropLoadEvent extends Event {
    public MythicDropLoadEvent(CustomDrop container, String dropName, MythicLineConfig config) {
    }

    public CustomDrop getContainer() {
        return null;
    }

    public String getDropName() {
        return null;
    }

    public MythicLineConfig getConfig() {
        return null;
    }

    public void register(Drop drop) {
    }

    @Override
    public HandlerList getHandlers() {
        return null;
    }

    public static HandlerList getHandlerList() {
        return null;
    }
}
