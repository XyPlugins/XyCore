package io.lumine.xikage.mythicmobs.drops;

import io.lumine.xikage.mythicmobs.adapters.AbstractItemStack;

public interface IItemDrop {
    AbstractItemStack getDrop(DropMetadata metadata);
}
