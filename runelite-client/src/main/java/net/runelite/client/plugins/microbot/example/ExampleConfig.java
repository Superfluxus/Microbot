package net.runelite.client.plugins.microbot.example;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("exampleFightPrep")
public interface ExampleConfig extends Config {
    @ConfigItem(
            keyName = "requiredPotionDoses",
            name = "Required Potion Doses",
            description = "Number of potion doses required for preparation",
            position = 0
    )
    default int requiredPotionDoses() {
        return 2; // Default value
    }

    @ConfigItem(
            keyName = "requiredCookedLizards",
            name = "Required Cooked Lizards",
            description = "Number of cooked lizards required for preparation",
            position = 1
    )
    default int requiredCookedLizards() {
        return 8; // Default value
    }
}
