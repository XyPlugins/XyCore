package org.xyplugin.xycore.internal.permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Cached permission grants and denials for one world. */
final class WorldPermissionRule {

    final boolean enabled;
    final List<String> grantPermissions;
    final List<String> denyPermissions;

    WorldPermissionRule(boolean enabled, List<String> grantPermissions, List<String> denyPermissions) {
        this.enabled = enabled;
        this.grantPermissions = immutableCleanList(grantPermissions);
        this.denyPermissions = immutableCleanList(denyPermissions);
    }

    boolean isEmpty() {
        return grantPermissions.isEmpty() && denyPermissions.isEmpty();
    }

    static WorldPermissionRule disabled() {
        return new WorldPermissionRule(false, Collections.emptyList(), Collections.emptyList());
    }

    private static List<String> immutableCleanList(List<String> input) {
        if (input == null || input.isEmpty()) return Collections.emptyList();
        List<String> cleaned = new ArrayList<>();
        for (String value : input) {
            if (value == null || value.trim().isEmpty()) continue;
            cleaned.add(value.trim().toLowerCase());
        }
        return Collections.unmodifiableList(cleaned);
    }
}
