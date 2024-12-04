package net.runelite.client.plugins.microbot.barrows;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;

@ConfigGroup("barrows")
public interface barrowsConfig extends Config {
    @ConfigItem(
            keyName = "killAll",
            name = "Kill all brothers",
            description = "Toggle on to kill every brother each run",
            position = 0
    )
    default boolean killAll
    {
        return false;
    }
}
