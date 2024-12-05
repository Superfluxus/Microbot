package net.runelite.client.plugins.microbot.barrows;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("Barrows")
public interface AutoBarrowsConfig extends Config {
    @ConfigItem(
            keyName = "minSlots",
            name = "Min Free Inv Slots",
            description = "Minimum number of inventory slots before we bank",
            position = 0
    )
    default int minSlots() {
        return 4;
    }

    @ConfigItem(
            keyName = "minHP",
            name = "Minimum HP",
            description = "Minimum HP before we eat",
            position = 1
    )
    default int minHP() {
        return 50;
    }

    @ConfigItem(
            keyName = "lootKeys",
            name = "Loot Brimstone Keys",
            description = "Enable Brimstone Key looting",
            position = 2
    )
    default boolean lootKeys() {
        return false;
    }


}