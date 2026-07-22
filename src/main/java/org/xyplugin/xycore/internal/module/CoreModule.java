package org.xyplugin.xycore.internal.module;

import org.xyplugin.xycore.api.service.Reloadable;

/** A switchable XyCore feature backed by its own module configuration file. */
public interface CoreModule extends Reloadable {

    String getDisplayName();

    String getConfigResourcePath();

    boolean isEnabled();

    void enable() throws Exception;

    void disable();
}
