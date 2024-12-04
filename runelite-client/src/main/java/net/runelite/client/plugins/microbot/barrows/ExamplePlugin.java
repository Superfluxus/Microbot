package net.runelite.client.plugins.microbot.barrows;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.*;

@PluginDescriptor(
        name = PluginDescriptor.Default + "Barrows",
        description = "Microbot Barrows plugin",
        tags = {"Barrows", "microbot"},
        enabledByDefault = false
)
@Slf4j
public class BarrowsPlugin extends Plugin {
    @Inject
    private BarrowsConfig config;
    @Provides
    barrowsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(barrowsConfig.class);
    }

    @Inject
    private OverlayManager overlayManager;
    @Inject
    private barrowsOverlay barrowsOverlay;

    @Inject
    barrowsScript barrowsScript;


    @Override
    protected void startUp() throws AWTException {
        if (overlayManager != null) {
            overlayManager.add(barrowsOverlay);
        }
        barrowsScript.run(config);
    }

    protected void shutDown() {
        barrowsScript.shutdown();
        overlayManager.remove(barrowsOverlay);
    }
    int ticks = 10;

}
