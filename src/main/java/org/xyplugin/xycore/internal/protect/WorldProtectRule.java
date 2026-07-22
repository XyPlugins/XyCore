package org.xyplugin.xycore.internal.protect;

/** Cached protection switches for one world. */
final class WorldProtectRule {

    final boolean enabled;
    final String bypassPermission;
    final boolean blockBreak;
    final boolean blockPlace;
    final boolean bucketFill;
    final boolean bucketEmpty;
    final boolean hangingBreak;
    final boolean hangingPlace;
    final boolean itemFrameInteract;
    final boolean armorStandBreak;
    final boolean armorStandPlace;
    final boolean armorStandInteract;
    final boolean cropTrample;
    final Messages messages;

    WorldProtectRule(boolean enabled,
                     String bypassPermission,
                     boolean blockBreak,
                     boolean blockPlace,
                     boolean bucketFill,
                     boolean bucketEmpty,
                     boolean hangingBreak,
                     boolean hangingPlace,
                     boolean itemFrameInteract,
                     boolean armorStandBreak,
                     boolean armorStandPlace,
                     boolean armorStandInteract,
                     boolean cropTrample,
                     Messages messages) {
        this.enabled = enabled;
        this.bypassPermission = bypassPermission == null ? "" : bypassPermission.trim();
        this.blockBreak = blockBreak;
        this.blockPlace = blockPlace;
        this.bucketFill = bucketFill;
        this.bucketEmpty = bucketEmpty;
        this.hangingBreak = hangingBreak;
        this.hangingPlace = hangingPlace;
        this.itemFrameInteract = itemFrameInteract;
        this.armorStandBreak = armorStandBreak;
        this.armorStandPlace = armorStandPlace;
        this.armorStandInteract = armorStandInteract;
        this.cropTrample = cropTrample;
        this.messages = messages;
    }

    static WorldProtectRule disabled() {
        return new WorldProtectRule(false, "", false, false, false, false, false, false,
                false, false, false, false, false, Messages.defaults());
    }

    static final class Messages {
        final String blockBreak;
        final String blockPlace;
        final String bucketFill;
        final String bucketEmpty;
        final String hangingBreak;
        final String hangingPlace;
        final String itemFrameInteract;
        final String armorStandBreak;
        final String armorStandPlace;
        final String armorStandInteract;
        final String cropTrample;

        Messages(String blockBreak,
                 String blockPlace,
                 String bucketFill,
                 String bucketEmpty,
                 String hangingBreak,
                 String hangingPlace,
                 String itemFrameInteract,
                 String armorStandBreak,
                 String armorStandPlace,
                 String armorStandInteract,
                 String cropTrample) {
            this.blockBreak = blockBreak;
            this.blockPlace = blockPlace;
            this.bucketFill = bucketFill;
            this.bucketEmpty = bucketEmpty;
            this.hangingBreak = hangingBreak;
            this.hangingPlace = hangingPlace;
            this.itemFrameInteract = itemFrameInteract;
            this.armorStandBreak = armorStandBreak;
            this.armorStandPlace = armorStandPlace;
            this.armorStandInteract = armorStandInteract;
            this.cropTrample = cropTrample;
        }

        static Messages defaults() {
            return new Messages(
                    "&c该世界无法破坏方块",
                    "&c该世界无法放置方块",
                    "&c该世界无法使用桶收回流体或生物",
                    "&c该世界无法使用桶放出流体或生物",
                    "&c该世界无法破坏悬挂物",
                    "&c该世界无法放置悬挂物",
                    "&c该世界无法操作展示框",
                    "&c该世界无法破坏盔甲架",
                    "&c该世界无法放置盔甲架",
                    "&c该世界无法操作盔甲架",
                    "&c该世界无法踩踏耕地"
            );
        }
    }
}
