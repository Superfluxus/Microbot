package net.runelite.client.plugins.microbot.example;

import com.google.inject.Provides;
import lombok.Getter;
import lombok.Setter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.config.ConfigManager;

import static net.runelite.api.HitsplatID.DAMAGE_ME;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.api.annotations.HitsplatType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.*;
import javax.inject.Inject;

import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

@PluginDescriptor(
        name = "Example Plugin",
        description = "An example plugin that interacts with Supply Crates.",
        tags = {"example", "supply crate", "microbot"}
)
public class ExamplePlugin extends Plugin {
    private static final int SAFE_SPOT_NPC_ID = 13015;

    @Getter
    private int elapsedTicks = 0;

    @Getter
    private final Set<NPC> safeSpotNpcs = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Getter
    private boolean jaguarSpawned = false; // Boolean to track if a Jaguar has

    @Getter
    private boolean jaguarAttacked = false; // Boolean to track if a Jaguar has spawned

    private static final Set<Integer> WHITELISTED_OBJECT_IDS = Set.of(
            51046 // Example: Blood splat ID
    );

    private static final Set<Integer> WHITELISTED_NPC_IDS = Set.of(
            13015, // Example: Blood splat ID
            13021
    );

    private String BOSS_DEAD_MSG = "the blood moon of peril is sufficiently distracted";

    private int JAGUAR_NPC_ID = 13021;

    @Getter
    private boolean BOSS_DEAD = false;

    private ExampleScript script;

    @Inject
    private ConfigManager configManager;

    public void resetJaguarAttackFlag() {
        jaguarAttacked = false;
    }

    private final ExecutorService taskQueue = Executors.newSingleThreadExecutor();
    private volatile boolean eatQueued = false;

    @Override
    protected void startUp() throws Exception {
        script = new ExampleScript(this);
        script.run(); // Start the script
    }


    public NPC findNpcById(int npcId) {
        return Microbot.getClient().getNpcs().stream()
                .filter(npc -> npc.getId() == npcId)
                .findFirst()
                .orElse(null);
    }

    @Getter
    WorldPoint currentSafeSpot = null;

    public WorldPoint getClosestBloodSplat(WorldPoint currentPlayerLocation) {
        return dangerousTiles.stream()
                // Filter out tiles that are diagonally adjacent
                .filter(tile -> {
                    int dx = Math.abs(tile.getX() - currentPlayerLocation.getX());
                    int dy = Math.abs(tile.getY() - currentPlayerLocation.getY());
                    return (dx + dy) == 1; // Ensure it's only one step away, either horizontally or vertically
                })
                // Find the closest tile among valid tiles
                .min(Comparator.comparingInt(tile -> tile.distanceTo(currentPlayerLocation)))
                .orElse(null);
    }

    private WorldPoint closestBloodSplat = null; // Closest blood splat tile
    private int bloodSplatTickCounter = 0; // Tick counter for the current blood splat

    @Subscribe
    public void onGameTick(GameTick gameTick) {
        elapsedTicks = elapsedTicks + 1;
    }


    @Inject
    private Client client;

    @Getter
    private final Set<WorldPoint> dangerousTiles = new HashSet<>();

    @Getter
    private final Set<WorldPoint> safeTiles = new HashSet<>();

    public void setBossDead(boolean isBossDead)
    {
        BOSS_DEAD = isBossDead;
    }


    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        Tile tile = event.getTile();
        TileObject gameObject = event.getGameObject();
        // Check if the game object is null or not in the whitelist
        if (gameObject == null || !WHITELISTED_OBJECT_IDS.contains(gameObject.getId())) {
            return; // Skip processing for non-whitelisted objects
        }

        if (tile == null || gameObject == null) {
            return;
        }

        try {
            WorldPoint tileLocation = tile.getWorldLocation();
            if (tileLocation == null) {

                return;
            }


            onTileObject(tile, null, gameObject);
        } catch (Exception e) {
            Microbot.log("Error processing GameObjectSpawned event: " + e.getMessage());
        }
    }

    public void setFoodCount(int count)
    {
        this.foodcount = count;
    }


    int foodcount = 0;

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        Tile tile = event.getTile();
        TileObject gameObject = event.getGameObject();

        // Check if the game object is null or not in the whitelist
        if (gameObject == null || !WHITELISTED_OBJECT_IDS.contains(gameObject.getId())) {
            return; // Skip processing for non-whitelisted objects
        }

        if (tile == null || gameObject == null) {
            return;
        }

        try {
            WorldPoint tileLocation = tile.getWorldLocation();
            if (tileLocation == null) {

                return;
            }


            onTileObject(tile, gameObject, null);
        } catch (Exception e) {
            Microbot.log("Error processing GameObjectDespawned event: " + e.getMessage());
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned npcSpawned) {
        NPC npc = npcSpawned.getNpc();
        // Check if the game object is null or not in the whitelist
        if (npcSpawned == null || !WHITELISTED_NPC_IDS.contains(npc.getId())) {
            return; // Skip processing for non-whitelisted objects
        }

        // Ensure NPC is not null and matches the safe spot NPC ID
        if (npc != null && npc.getId() == SAFE_SPOT_NPC_ID) {
            // Add the NPC to the safeSpotNpcs set
            Microbot.log("New Safe Spot just spawned: " + npc);
            safeSpotNpcs.add(npc);
            currentSafeSpot = npc.getWorldLocation();
        }

        if (npc != null && npc.getId() == JAGUAR_NPC_ID) {
            jaguarSpawned = true;
        }
    }


    @Subscribe
    public void onNpcDespawned(NpcDespawned npcDespawned) {
        NPC npc = npcDespawned.getNpc();
        // Check if the game object is null or not in the whitelist
        if (npcDespawned == null || !WHITELISTED_NPC_IDS.contains(npc.getId())) {
            return; // Skip processing for non-whitelisted objects
        }

        // Ensure NPC is not null
        if (npc != null && npc.getId() == SAFE_SPOT_NPC_ID) {
            safeSpotNpcs.remove(npc);
        }

        if (npc != null && npc.getId() == JAGUAR_NPC_ID) {
            jaguarSpawned = false; // Update the boolean when a Jaguar despawns
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage chatMessage) {
            String chatMsg = chatMessage.getMessage().toLowerCase();
            if (chatMsg.contains(BOSS_DEAD_MSG)) {
                setBossDead(true);
                Microbot.log("Boss dead message detected: " + chatMsg);
            }
    }
    @Subscribe
    public void onAnimationChanged(AnimationChanged e) {
        if (!jaguarSpawned) {
            return;
        }

        // Check if the actor is an NPC
        if (!(e.getActor() instanceof NPC)) {
            return;
        }

        // Cast the actor to NPC
        NPC actor = (NPC) e.getActor();

        // Check if the NPC is the Jaguar with ID 13021
        if (actor.getId() == 13021) {
            // Check if the Jaguar is within 2 tiles of the player's location
            WorldPoint npcLocation = actor.getWorldLocation();
            WorldPoint playerLocation = Rs2Player.getWorldLocation();

            if (npcLocation != null && playerLocation.distanceTo(npcLocation) <= 2) {
                // Get the current animation of the Jaguar
                int animation = actor.getAnimation();

                // Check if the animation corresponds to the attack animation (10959 in this case)
                if (animation == 10959) {
                    jaguarAttacked = true;
                    lastJaguarAttackTick = elapsedTicks;
                    Microbot.log("Jaguar just attacked!");
                } else {
                    jaguarAttacked = false;
                }
            }
        }
    }


    public int getFoodCount()
    {
        return this.foodcount;
    }

    public NPC getNearestSafeSpotNpc(WorldPoint playerLocation) {
        return safeSpotNpcs.stream()
                .min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(playerLocation)))
                .orElse(null);
    }

    public boolean isSafeSpotAvailable() {
        return safeSpotNpcs.stream().anyMatch(Objects::nonNull);
    }

    @Getter
    private int lastJaguarAttackTick = -1; // Default to -1, meaning no attack yet



    private void onTileObject(Tile tile, TileObject oldObject, TileObject newObject) {
        // Determine the tile's WorldPoint
        WorldPoint tileLocation = tile.getWorldLocation();

        // If a new object is spawned
        if (newObject != null) {
            int objectId = newObject.getId();

            // Add dangerous tiles based on specific object IDs
            if (objectId == 51046) { // Example: blood splat ID
                dangerousTiles.add(tileLocation);
            }
        }

        // If an old object is despawned
        if (oldObject != null) {
            int objectId = oldObject.getId();

            // Remove dangerous tiles
            if (objectId == 51046) {
                dangerousTiles.remove(tileLocation);
            }
        }
    }

    @Override
    protected void shutDown() throws Exception {
        if (script != null) {
            Microbot.log("Shutting down script...");
            script.shutdown(); // Ensure proper cleanup
        }
    }

    @Provides
    ExampleConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(ExampleConfig.class);
    }
}
