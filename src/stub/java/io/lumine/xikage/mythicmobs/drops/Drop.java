package io.lumine.xikage.mythicmobs.drops;

import io.lumine.xikage.mythicmobs.io.MythicLineConfig;

public abstract class Drop {
    public Drop(String line, MythicLineConfig config) {
    }

    public double getAmount() {
        return 1.0D;
    }
}
