package net.runelite.client.plugins.microbot.barrows;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provides;
import lombok.Getter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.barrows.models.TheBarrowsBrothers;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@PluginDescriptor(
        name = PluginDescriptor.Pumster + "Barrows",
        description = "Microbot barrows plugin",
        tags = {"pvm", "microbot", "barrows"},
        enabledByDefault = false
)
public class AutoBarrowsPlugin extends Plugin {
    private static final Map<TheBarrowsBrothers, WorldPoint> CRYPT_LOCATIONS = new HashMap<>();
    private static final Map<TheBarrowsBrothers, WorldPoint> CRYPT_BELOW_LOCATIONS = new HashMap<>();
    private static final Map<Integer, String> BROTHER_CRYPT_MAPPING = new HashMap<>();

    private boolean isPlayerFightingBrother = false;

    static {
        // Define the approximate center points for each crypt
        CRYPT_LOCATIONS.put(TheBarrowsBrothers.AHRIM, new WorldPoint(3565, 3289, 0));
        CRYPT_LOCATIONS.put(TheBarrowsBrothers.DHAROK, new WorldPoint(3575, 3298, 0));
        CRYPT_LOCATIONS.put(TheBarrowsBrothers.GUTHAN, new WorldPoint(3575, 3283, 0));
        CRYPT_LOCATIONS.put(TheBarrowsBrothers.KARIL, new WorldPoint(3566, 3276, 0));
        CRYPT_LOCATIONS.put(TheBarrowsBrothers.TORAG, new WorldPoint(3554, 3282, 0));
        CRYPT_LOCATIONS.put(TheBarrowsBrothers.VERAC, new WorldPoint(3557, 3297, 0));

        CRYPT_BELOW_LOCATIONS.put(TheBarrowsBrothers.AHRIM, new WorldPoint(3556, 9701, 3));
        CRYPT_BELOW_LOCATIONS.put(TheBarrowsBrothers.DHAROK, new WorldPoint(3555, 9716, 3));
        CRYPT_BELOW_LOCATIONS.put(TheBarrowsBrothers.GUTHAN, new WorldPoint(3537, 9704, 3));
        CRYPT_BELOW_LOCATIONS.put(TheBarrowsBrothers.KARIL, new WorldPoint(3550, 9684, 3));
        CRYPT_BELOW_LOCATIONS.put(TheBarrowsBrothers.TORAG, new WorldPoint(3569, 9685, 3));
        CRYPT_BELOW_LOCATIONS.put(TheBarrowsBrothers.VERAC, new WorldPoint(3575, 9706, 3));

        // Map each Barrows Brother to their respective crypt
        BROTHER_CRYPT_MAPPING.put(NpcID.AHRIM_THE_BLIGHTED, "Ahrim");
        BROTHER_CRYPT_MAPPING.put(NpcID.DHAROK_THE_WRETCHED, "Dharok");
        BROTHER_CRYPT_MAPPING.put(NpcID.GUTHAN_THE_INFESTED, "Guthan");
        BROTHER_CRYPT_MAPPING.put(NpcID.KARIL_THE_TAINTED, "Karil");
        BROTHER_CRYPT_MAPPING.put(NpcID.TORAG_THE_CORRUPTED, "Torag");
        BROTHER_CRYPT_MAPPING.put(NpcID.VERAC_THE_DEFILED, "Verac");
    }

    private static final int[] BARROWS_BROTHERS_IDS = {
            NpcID.AHRIM_THE_BLIGHTED,
            NpcID.DHAROK_THE_WRETCHED,
            NpcID.GUTHAN_THE_INFESTED,
            NpcID.KARIL_THE_TAINTED,
            NpcID.TORAG_THE_CORRUPTED,
            NpcID.VERAC_THE_DEFILED
    };

    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private AutoBarrowsOverlay barrowsOverlay;

    @Inject
    private AutoBarrowsConfig config;

    @Inject
    public AutoBarrowsScript barrowsScript;

    @Provides
    AutoBarrowsConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(AutoBarrowsConfig.class);
    }

    @Getter
    private TheBarrowsBrothers brotherToFight;

    @Override
    protected void startUp() throws Exception {
        overlayManager.add(barrowsOverlay);
        Microbot.setClient(client); // Ensure the client is set

        barrowsScript.run(config, this); // Start the script
    }

    @Override
    protected void shutDown() throws Exception {
        overlayManager.remove(barrowsOverlay);
        barrowsScript.shutdown();
    }

    public int getSarcophagusId(String brotherName) {
        switch (brotherName.toLowerCase()) {
            case "ahrim":
                return ObjectID.SARCOPHAGUS_20770;
            case "dharok":
                return ObjectID.SARCOPHAGUS_20720;
            case "guthan":
                return ObjectID.SARCOPHAGUS_20722;
            case "torag":
                return ObjectID.SARCOPHAGUS_20721;
            case "karil":
                return ObjectID.SARCOPHAGUS_20771;
            case "verac":
                return ObjectID.SARCOPHAGUS_20772;
            default:
                throw new IllegalArgumentException("Invalid brother name: " + brotherName);
        }
    }

    public int getStaircaseId(String brotherName) {
        switch (brotherName.toLowerCase()) {
            case "ahrim":
                return ObjectID.STAIRCASE_20667;
            case "dharok":
                return ObjectID.STAIRCASE_20668;
            case "guthan":
                return ObjectID.STAIRCASE_20669;
            case "karil":
                return ObjectID.STAIRCASE_20670;
            case "torag":
                return ObjectID.STAIRCASE_20671;
            case "verac":
                return ObjectID.STAIRCASE_20672;
            default:
                throw new IllegalArgumentException("Invalid brother name: " + brotherName);
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        int npcId = event.getNpc().getId();
        // Id	15007745 FOUDND A TUNNEL

        if (isInCrypt() && Arrays.stream(BARROWS_BROTHERS_IDS).anyMatch(x -> x == npcId) && !event.getNpc().isInteracting()  && brotherToFight == TheBarrowsBrothers.fromId(npcId)) {
            isPlayerFightingBrother = true;
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        int npcId = event.getNpc().getId();
        // Id	15007745 FOUDND A TUNNEL

        if (isInCrypt() && Arrays.stream(BARROWS_BROTHERS_IDS).anyMatch(x -> x == npcId) && !event.getNpc().isInteracting()  && brotherToFight == TheBarrowsBrothers.fromId(npcId)) {
            isPlayerFightingBrother = false;
        }
    }

    public boolean isFightingBrother() {
        return isPlayerFightingBrother;
    }

    public static Map<TheBarrowsBrothers, WorldPoint> getCryptBelowLocations() {
        return CRYPT_BELOW_LOCATIONS;
    }




    public boolean isInCrypt() {
        return Rs2Player.getWorldLocation().getRegionID() == 14231;
    }

    public boolean isPlayerFightingBrother() {
        return isPlayerFightingBrother;
    }
}
