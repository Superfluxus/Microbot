package net.runelite.client.plugins.microbot.example;

import net.runelite.api.*;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.Plugin;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;

import java.util.*;

import java.util.Random;

import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.util.List;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

enum ExampleState {
    COLLECT_SUPPLIES,
    PREPARE_ITEMS,
    GET_LIZARDS,
    CLEANUP,
    GO_TO_FIGHT,
    NORMAL_FIGHT,
    MAIN_FIGHT,
    BLOOD_SPLATS,
    JAGUARS,
    CLAIM_CHEST
}

public class ExampleScript extends Script {

    @Inject
    private Client client;

    private ExamplePlugin plugin;

    // Constructor to initialize plugin
    public ExampleScript(ExamplePlugin plugin) {
        this.plugin = plugin;
    }

    private volatile boolean running = true;

    public boolean isRunning() {
        return running;
    }

    public void shutdown() {
        running = false;
    }

    //private ExampleState currentState = ExampleState.COLLECT_SUPPLIES;

    private ExampleState currentState = ExampleState.COLLECT_SUPPLIES;
    // IDs and constants
    private static final int SUPPLY_CRATE_ID = 51371;
    private static final int TREE_ID = 51358;
    private static final int GRUBBY_SAPLING_ID = 51365;// Tree ID
    private static final int GRUB_ID = 29078;
    private static final int PESTLE_AND_MORTAR_ID = 233;
    private static final int PASTE_ID = 29079;
    private static final int VIAL_ID = 227;
    private static final int DESIRED_VIAL_COUNT = 5;
    private static final int ROCK_ID = 51359; // Rock ID
    private static final String ACTION = "Trap"; // The action to perform
    private static final int STOVE_ID = 51362;
    private static final int RAW_LIZARD_ID = 29076; // The ID of the Supply Crate
    private static final int COOKED_LIZARD_ID = 29077;
    private static final int ONEDOSE_ID = 29083;
    private static final int TWODOSE_ID = 29082;
    private static final int THREEDOSE_ID = 29081;
    private static final int FOURDOSE_ID = 29080;
    private static final int GRUBPASTE_ID = 29079;
    private static final int BUTTERFLY_NET_ID = 10010;
    private static final int EN_ROUTE_STOVE = 51362;
    private boolean claimChest = false;



    private int sleepMin = 600;  // Default 500ms
    private int sleepMax = 2100; // Default 2100ms
    private int sleepTarget = 1200; // Midpoint as default

    private static final WorldPoint[] ROCK_LOCATIONS = {
            new WorldPoint(1388, 9709, 0),
            new WorldPoint(1390, 9707, 0),
            new WorldPoint(1392, 9707, 0),
            new WorldPoint(1394, 9705, 0)
    };

    @Override
    public boolean run() {
        Microbot.enableAutoRunOn = false;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }
                if(!this.isRunning()){
                    return;
                }

                switch (currentState) {
                    case COLLECT_SUPPLIES:
                        preparePhase();
                        break;

                    case GO_TO_FIGHT:
                        goToFight();
                        break;

                    case NORMAL_FIGHT:
                        normalfight();
                        break;

                    case MAIN_FIGHT:
                        mainfight();
                        break;

                    case BLOOD_SPLATS:
                        bloodsplats();
                        break;

                    case JAGUARS:
                        jaguars();
                        break;

                    case CLAIM_CHEST:
                        claimChestReward();
                        break;

                    default:
                        Microbot.log("Unknown state: " + currentState);
                        break;
                }

            } catch (Exception ex) {
                Microbot.log("Error in state " + currentState + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private NPC findNpcById(int npcId) {
        return Microbot.getClient().getNpcs().stream()
                .filter(npc -> npc.getId() == npcId)
                .findFirst()
                .orElse(null);
    }

    private boolean DoesSafeSpotExist(int safeSpotNpcId) {

        // Find the NPC representing the safe spot
        NPC safeSpotNpc = Microbot.getClient().getNpcs().stream()
                .filter(npc -> npc.getId() == safeSpotNpcId)
                .findFirst()
                .orElse(null);

        if (safeSpotNpc == null) {
            Microbot.log("Safe spot NPC with ID " + safeSpotNpcId + " not found.");
            return false;
        }

        return true;
    }

    private int calculateSleepDuration() {


        // Create a Random object
        Random random = new Random();

        // Fallback default values if not provided
        if (sleepMin == 0) sleepMin = 500;  // Default 500ms
        if (sleepMax == 0) sleepMax = 1500; // Default 1500ms
        if (sleepTarget == 0) sleepTarget = (sleepMin + sleepMax) / 2; // Midpoint as default


        // Calculate the mean (average) of sleepMin and sleepMax, adjusted by sleepTarget
        double mean = (sleepMin + sleepMax + sleepTarget) / 3.0;

        // Calculate the standard deviation with added noise
        double noiseFactor = 0.2; // Adjust the noise factor as needed (0.0 to 1.0)
        double stdDeviation = Math.abs(sleepTarget - mean) / 3.0 * (1 + noiseFactor * (random.nextDouble() - 0.5) * 2);

        // Generate a random number following a normal distribution
        int sleepDuration;
        do {
            // Generate a random number using nextGaussian method, scaled by standard deviation
            sleepDuration = (int) Math.round(mean + random.nextGaussian() * stdDeviation);
        } while (this.isRunning() && sleepDuration < sleepMin || sleepDuration > sleepMax); // Ensure the duration is within the specified range

        return sleepDuration;
    }

    public class MissingResources {
        private int missingPotionDoses;
        private int missingCookedLizards;

        // Constructor
        public MissingResources(int missingPotionDoses, int missingCookedLizards) {
            this.missingPotionDoses = missingPotionDoses;
            this.missingCookedLizards = missingCookedLizards;
        }

        // Getters
        public int getMissingPotionDoses() {
            return missingPotionDoses;
        }

        public int getMissingCookedLizards() {
            return missingCookedLizards;
        }

        // Check if no resources are missing
        public boolean isComplete() {
            return missingPotionDoses <= 0 && missingCookedLizards <= 0;
        }

        @Override
        public String toString() {
            return "MissingResources{" +
                    "missingPotionDoses=" + missingPotionDoses +
                    ", missingCookedLizards=" + missingCookedLizards +
                    '}';
        }
    }

    private MissingResources validateInventoryDetailed() {
        int potionDoses = Rs2Inventory.count(FOURDOSE_ID) * 4 +
                Rs2Inventory.count(THREEDOSE_ID) * 3 +
                Rs2Inventory.count(TWODOSE_ID) * 2 +
                Rs2Inventory.count(ONEDOSE_ID);
        int cookedLizards = Rs2Inventory.count(COOKED_LIZARD_ID);

        int missingPotionDoses = Math.max(0, 2 - potionDoses); // At least 2 doses required
        int missingCookedLizards = Math.max(0, 8 - cookedLizards); // At least 5 cooked lizards required

        return new MissingResources(missingPotionDoses, missingCookedLizards);
    }

    private void preparePhase() {

        WorldPoint stovePoint = new WorldPoint(1376, 9710, 0);

        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if(!(stovePoint.getPlane() == playerLocation.getPlane())){
            Microbot.log("Currently in main chamber, try to walk to stove.");
            Rs2Walker.walkTo(stovePoint);
            sleepUntilTrue(() -> (!Rs2Player.isAnimating()));
        }

        if (stovePoint.getPlane() == playerLocation.getPlane() && stovePoint.distanceTo(playerLocation) < 10) {
            Microbot.log("Accurately walk to stove");
            Rs2Walker.walkFastCanvas(stovePoint);
            sleepUntilTrue(() -> !Rs2Player.isAnimating());
        }

        Microbot.log("State: Preparation Phase");

        // Get inventory deficit
        MissingResources missingResources = validateInventoryDetailed();

        // Check for potion deficits
        if (missingResources.getMissingPotionDoses() > 0) {
            Microbot.log("Missing potion doses: " + missingResources.getMissingPotionDoses());

            // Step 1: Collect herblore supplies
            if (Rs2GameObject.interact(SUPPLY_CRATE_ID, "Take-from Herblore")) {
                Microbot.log("Collecting herblore supplies...");
                sleepUntilTrue(() -> Rs2Inventory.count("Vial of water") > 2, 100, 5000);
            }

            // Step 2: Process grub into paste and make potions
            if (Rs2GameObject.interact(GRUBBY_SAPLING_ID, "Collect-from")) {
                Microbot.log("Interacting with Grubby Sapling to collect paste ingredients...");
                sleepUntilTrue(() -> Rs2Inventory.count("Moonlight grub") > 3, 100, 5000);
            }

            while (this.isRunning() && Rs2Inventory.count(GRUB_ID) > 0) {
                if (Rs2Inventory.combine(PESTLE_AND_MORTAR_ID, GRUB_ID)) {
                    Microbot.log("Processing Moonlight Grub into paste...");
                    sleepUntilTrue(() -> Rs2Inventory.count("Moonlight grub paste") > 2, 100, 5000);
                }
            }

            while (this.isRunning() && Rs2Inventory.count(PASTE_ID) > 0 && Rs2Inventory.count(VIAL_ID) > 0) {
                if (Rs2Inventory.combine(PASTE_ID, VIAL_ID)) {
                    Microbot.log("Creating vials with Moonlight Grub paste...");
                }
                sleep(calculateSleepDuration());
            }

            int[] itemsToDrop = {ONEDOSE_ID, TWODOSE_ID, THREEDOSE_ID, VIAL_ID, GRUBPASTE_ID, PESTLE_AND_MORTAR_ID};

            for (int itemId : itemsToDrop) {
                while (this.isRunning() && Rs2Inventory.contains(itemId)) {
                    Rs2Inventory.drop(itemId);
                    sleep(400, 600);
                }
            }

            missingResources = validateInventoryDetailed();
            if (missingResources.getMissingPotionDoses() > 0) {
                Microbot.log("Failed to get enough potion doses, restart potion making.");
                return;
            }
        }

        // Check for cooked lizard deficits
        if (missingResources.getMissingCookedLizards() > 0) {
            Microbot.log("Missing cooked lizards: " + missingResources.getMissingCookedLizards());

            int lizardsNeeded = (int) Math.ceil(missingResources.getMissingCookedLizards() * 0.3);
            if (lizardsNeeded <= 1) {
                lizardsNeeded = 1;
            }
            Microbot.log("Need to collect at least " + lizardsNeeded + " raw lizards.");

            if (!Rs2Inventory.contains("Rope")) {
                Rs2GameObject.interact(SUPPLY_CRATE_ID, "Take-from Hunting");
                sleepUntilTrue(() -> Rs2Inventory.count("Rope") > 1, 100, 5000);
            }

            while (this.isRunning() && Rs2Inventory.count("Raw moss lizard") < lizardsNeeded) {
                // Step 3: Catch raw lizards
                for (WorldPoint location : ROCK_LOCATIONS) {
                    GameObject rock = Rs2GameObject.findObject(ROCK_ID, location);

                    if (rock != null) {
                        Microbot.log("Interacting with rock at: " + location);
                        Rs2GameObject.interact(rock, ACTION);
                        sleep(900,1200);
                        sleep(calculateSleepDuration());
                        sleepUntilTrue(() -> (!Rs2Player.isAnimating() && !Rs2Player.isMoving()));
                        if (Rs2Dialogue.isInDialogue()) {
                            if (Rs2Dialogue.hasContinue()) {
                                Rs2Dialogue.clickContinue();
                            }
                        }
                    }
                    sleep(200, 350);
                }

                // Shake the tree to spawn lizards
                if (Rs2GameObject.interact(TREE_ID, "Rustle")) {
                    Microbot.log("Shaking the tree to spawn lizards...");
                    sleep(calculateSleepDuration());
                    sleepUntilTrue(() -> Rs2GroundItem.exists("Raw moss lizard", 20), 100, 10000);

                    // Loot raw lizards until none are left nearby
                    while (this.isRunning() && Rs2GroundItem.exists("Raw moss lizard", 20)) {
                        sleep(calculateSleepDuration());
                        Rs2GroundItem.loot("Raw moss lizard", 20);
                        sleep(600,900);

                    }
                }
            }
            if (Rs2Inventory.hasItem("Raw moss lizard")) {
                Microbot.log("Found raw moss lizard in inventory, attempting to cook");
                Rs2Walker.walkFastCanvas(stovePoint);
                sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating()));
                Rs2GameObject.interact(STOVE_ID, "Cook");
                sleepUntilTrue(() -> !Rs2Inventory.hasItem(RAW_LIZARD_ID), 100, 10000);
            }
            missingResources = validateInventoryDetailed();
            if (missingResources.getMissingCookedLizards() > 0) {
                Microbot.log("Failed to get enough lizards doses, restart.");
                return;
            }

        }

        // Final Validation
        missingResources = validateInventoryDetailed();
        if (missingResources.isComplete()) {
            if (claimChest) {
                Microbot.log("Claiming rewards from last kill en route.");
                WorldPoint nearStove = new WorldPoint(1351, 9581, 0);
                Rs2Walker.walkTo(nearStove);
                sleepUntilTrue(() -> !Rs2Player.isAnimating());
                Rs2GameObject.interact(EN_ROUTE_STOVE, "Make-cuppa");
                sleep(calculateSleepDuration());
                currentState = ExampleState.CLAIM_CHEST;
                return;
            }
            Microbot.log("Preparation complete. Moving to GO_TO_FIGHT state.");
            currentState = ExampleState.GO_TO_FIGHT;
        } else {
            Microbot.log("Preparation incomplete. Retrying preparation phase.");
        }
    }

    private void claimChestReward() {
        WorldPoint chestRoom = new WorldPoint(1513, 9563, 0);
        Rs2Walker.walkTo(chestRoom);
        sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating()));
        sleep(1200,1800);
        WorldPoint nearChest = new WorldPoint(1513, 9578, 0);
        Microbot.log("Found chest room.");
        Rs2Walker.walkFastCanvas(chestRoom);
        sleep(calculateSleepDuration());
        sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating()));
        Microbot.log("Opening chest.");
        Rs2GameObject.interact(51346, "claim");
        sleep(calculateSleepDuration());
        sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating()));
        Microbot.log("Waiting for widget popup.");
        sleep(calculateSleepDuration());
        Rs2Widget.clickWidget(56885268);
        sleep(calculateSleepDuration());
        Microbot.log("Rewards claimed, ready to fight again");
        plugin.setBossDead(false);
        Microbot.log("Refuel enroute");
        WorldPoint nearStove = new WorldPoint(1351, 9581, 0);
        Rs2Walker.walkTo(nearStove);
        sleepUntilTrue(() -> !Rs2Player.isAnimating());
        Rs2GameObject.interact(EN_ROUTE_STOVE, "Make-cuppa");
        sleep(calculateSleepDuration());
        currentState = ExampleState.GO_TO_FIGHT;
    }


    private boolean safeWalkTo(WorldPoint target) {
        if (target == null) {
            Microbot.log("Target WorldPoint is null. Cannot walk.");
            return false;
        }

        try {
            return Rs2Walker.walkFastCanvas(target);
        } catch (Exception ex) {
            Microbot.log("Error while walking to target: " + ex.getMessage());
            return false;
        }
    }

    private boolean safeInteractWithGameObject(int objectId, String action) {
        try {
            return Rs2GameObject.interact(objectId, action);
        } catch (Exception ex) {
            Microbot.log("Error while interacting with object ID " + objectId + ": " + ex.getMessage());
            return false;
        }
    }


    private void goToFight() {
        Microbot.log("State: GoToFight");
        Widget parentWidget = null;
        boolean visibleTick = Rs2Widget.isWidgetVisible(56950788);
        if (visibleTick) {
            currentState = ExampleState.CLAIM_CHEST;
            return;
        }

        int cookedLizardsCount = Rs2Inventory.count(COOKED_LIZARD_ID); // Replace with the correct ID for cooked lizards
        plugin.setFoodCount(cookedLizardsCount);

        int runEnergy = Microbot.getClient().getEnergy();
        if (runEnergy < 6500) {
            WorldPoint stovePoint = new WorldPoint(1376, 9710, 0);
            Rs2Walker.walkFastCanvas(stovePoint);
            sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating()));
            Rs2GameObject.interact(STOVE_ID, "Make-cuppa");
        }
        Rs2Player.toggleRunEnergy(true);

        Microbot.log("Walking to Blood Moon Area");

        WorldPoint fightRoom = new WorldPoint(1418, 9632, 0);
        WorldPoint balcony = new WorldPoint(1410, 9630, 0);
        while (this.isRunning() && !Rs2Player.getWorldLocation().equals(fightRoom)) {
            Rs2Walker.walkTo(fightRoom);
            sleep(calculateSleepDuration());
        }
        Microbot.log("Walking to Balcony");
        while (this.isRunning() && !Rs2Player.getWorldLocation().equals(balcony)) {
            Rs2Walker.walkFastCanvas(balcony);
            sleep(calculateSleepDuration());
        }
        sleep(calculateSleepDuration());

        // Drink potion
        int rawStrength = Rs2Player.getRealSkillLevel(Skill.DEFENCE);
        int realStrength = Rs2Player.getBoostedSkillLevel(Skill.DEFENCE);
        if ((realStrength - rawStrength < 5)) {
            Rs2Inventory.interact("Moonlight potion", "Drink");
        }

        // Is jaguar phase on?

        NPC jaguaralive = findNpcById(13021);
        if (jaguaralive == null) {
            Microbot.log("Enter fight");
        } else {
            while (this.isRunning() && jaguaralive != null) {
                jaguaralive = findNpcById(13021);
                sleep(calculateSleepDuration());
            }
        }


        if (safeInteractWithGameObject(51372, "Use")) { // Interact with the fight object
            Microbot.log("Successfully interacted to start the fight.");
            plugin.setBossDead(false);
        } else {
            Microbot.log("Failed to start the fight.");
        }

        sleep(1200, 1500);

        // Transition to the next state
        currentState = ExampleState.NORMAL_FIGHT;
    }

    private void normalfight() {
        Microbot.log("Entering fight!");

        // Step 1: Run to the safe spot
        Microbot.log("Checking for safe spot...");
        NPC safeSpotNpc = plugin.getNearestSafeSpotNpc(Rs2Player.getWorldLocation());
        if (safeSpotNpc == null) {
            Microbot.log("No safe spot NPC found. Attacking boss directly.");
        } else {
            WorldPoint safeSpot = safeSpotNpc.getWorldLocation();
            Microbot.log("Safe spot found at: " + safeSpot + ". Moving there...");
            Rs2Walker.walkFastCanvas(safeSpot);
            sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating()));
            Microbot.log("Reached safe spot: " + safeSpot);
        }

        // Step 2: Hit the boss
        sleepUntilTrue(() -> !(Rs2Player.isMoving() && Rs2Player.isAnimating()));
        Rs2Npc.interact(13011, "Attack");

        Microbot.log("Start main fight loop");
        currentState = ExampleState.MAIN_FIGHT;
    }

    private void mainfight() {
        Microbot.log("Waiting for an event: safe spot moves, Moonfire spawns, or Jaguar spawns.");
        boolean safespotexists = false;
        WorldPoint safeSpotLocation = null;
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        Rs2Npc.interact(13011, "Attack");
        ;
        while (this.isRunning() && true) {
            int currentCount = Rs2Inventory.count(COOKED_LIZARD_ID);
            if (currentCount > 0) {
                Rs2Player.eatAt(75);
            } else {
                if((Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) < 50)){
                    Microbot.log("Trying to escape as food count is"  + Rs2Inventory.count(COOKED_LIZARD_ID) + "and our HP count is " + Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS));
                    Rs2GameObject.interact(53003, "Quick-escape");
                    sleep(3000);
                    currentState = ExampleState.COLLECT_SUPPLIES;
                    return;
            }else{
                    Microbot.log("We have no food but we do have " + Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS) + "HP");
                }
            }
            safeSpotLocation = plugin.getCurrentSafeSpot();
            safespotexists = plugin.isSafeSpotAvailable();

            // Check if Moonfire spawns
            if (Rs2GameObject.exists(51054)) {
                Microbot.log("Moonfire detected! Transitioning to BLOOD_SPLATS state.");
                currentState = ExampleState.BLOOD_SPLATS;
                return; // Exit the entire function
            }

            // Check if Jaguar spawns
            if (plugin.isJaguarSpawned()) { // Poll the boolean value
                Microbot.log("Jaguar detected! Transitioning to JAGUARS state.");
                currentState = ExampleState.JAGUARS;
                return; // Exit the entire function
            }

            // Check for fight finished
            if (plugin.isBOSS_DEAD()) {
                claimChest = true;
                Microbot.log("Boss is dead, restart loop.");
                currentState = ExampleState.COLLECT_SUPPLIES;
                return;
            }
            if (!safespotexists) {
                continue;
            }
            if (safespotexists && playerLocation.distanceTo(safeSpotLocation) > 1) {

                Microbot.log("Safe spot exists and we're not on it! Moving to the safe spot.");
                Rs2Walker.walkFastCanvas(safeSpotLocation);
                sleep(450,600);

                sleepUntilTrue(() ->
                        (!Rs2Player.isAnimating() && !Rs2Player.isMoving())
                                && (Rs2Player.getAnimation() != 1660 || Rs2Player.getAnimation() != 1661)
                );
                Rs2Npc.interact(13011, "Attack");
                Rs2Antiban.actionCooldown();
                playerLocation = Rs2Player.getWorldLocation();
            }
            sleep(600,900);
        }
    }

    private void updateSafeTiles() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        Set<WorldPoint> dangerousTiles = plugin.getDangerousTiles();
        Set<WorldPoint> safeTiles = plugin.getSafeTiles();

        // Get walkable tiles around the player
        List<WorldPoint> walkableTiles = Rs2Tile.getWalkableTilesAroundTile(playerLocation, 1);

        // Clear and repopulate the TreeSet with updated safe tiles
        safeTiles.clear();
        for (WorldPoint tile : walkableTiles) {
            if (!dangerousTiles.contains(tile)) {
                safeTiles.add(tile); // Add tiles that aren't dangerous
            }
        }
    }

    private boolean isCurrentLocationDangerous() {
        // Get the player's current location
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        // Get the dangerous tiles set from the plugin
        Set<WorldPoint> dangerousTiles = plugin.getDangerousTiles();

        // Check if the player's current location is in the dangerous tiles set
        boolean isDangerous = dangerousTiles.contains(playerLocation);

        return isDangerous;
    }


    private void bloodsplats() {
        Microbot.log("Entering blood splats phase...");

        // Find the ExamplePlugin instance
        ExamplePlugin plugin = (ExamplePlugin) Microbot.getPluginManager().getPlugins().stream()
                .filter(p -> p.getClass().getName().contains("ExamplePlugin"))
                .findFirst()
                .orElse(null);

        if (plugin == null) {
            Microbot.log("Error: Could not find ExamplePlugin instance!");
            return;
        }

        // Track the existing safespot NPCs at the start of this phase
        Set<NPC> knownSafeSpots = new HashSet<>(plugin.getSafeSpotNpcs());
        Microbot.log("Tracking initial safespot NPCs: " + knownSafeSpots);

        while (this.isRunning() && Rs2GameObject.exists(51054)) {
            sleep(200, 400);
            // Continuously fetch the current safespot NPCs


            updateSafeTiles();
            if (isCurrentLocationDangerous()) {
                Microbot.log("Current tile is dangerous. Moving to a safe tile...");
                WorldPoint safeTile = plugin.getSafeTiles().stream()
                        .sorted(Comparator.comparingInt(tile -> tile.distanceTo(Rs2Player.getWorldLocation())))
                        .findFirst()
                        .orElse(null);

                if (safeTile != null) {
                    Rs2Walker.walkFastCanvas(safeTile);
                    sleepUntilTrue(() -> Rs2Player.getWorldLocation().equals(safeTile));
                } else {
                    Microbot.log("No safe tiles found. Staying in place.");
                }
            }
        }
        Set<NPC> currentSafeSpots = plugin.getSafeSpotNpcs();
        NPC safeSpotNpc = plugin.getNearestSafeSpotNpc(Rs2Player.getWorldLocation());
        if (plugin.isSafeSpotAvailable()) {
            WorldPoint playerLocation = Rs2Player.getWorldLocation();
            WorldPoint safeSpotLocation = safeSpotNpc.getWorldLocation();
            Rs2Walker.walkFastCanvas(safeSpotLocation);
            sleepUntilTrue(() -> (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && playerLocation.distanceTo(safeSpotLocation) <= 1));
        }
        Microbot.log("Moonfire has gone, back to MainFight logic.");
        currentState = ExampleState.MAIN_FIGHT;
    }

    private void waitForNextTick(int currentTick) {
        while (this.isRunning() && plugin.getElapsedTicks() == currentTick) {
            sleep(10, 20); // Brief sleep to prevent busy-waiting
        }
    }


    private void jaguars() {
        Microbot.log("Starting Jaguar Phase...");
        boolean resync = false;

        // Ensure plugin instance is available
        for (Plugin p : Microbot.getPluginManager().getPlugins()) {
            if (p instanceof ExamplePlugin) {
                plugin = (ExamplePlugin) p;
                break;
            }
        }

        // Check for a safe spot NPC
        NPC safeSpotNpc = plugin.getNearestSafeSpotNpc(Rs2Player.getWorldLocation());
        if (safeSpotNpc == null) {
            Microbot.log("No safe spot NPC found. Exiting Jaguar Phase.");
            currentState = ExampleState.MAIN_FIGHT;
            return;
        }

        // Move to safe spot if necessary
        WorldPoint safeSpotLocation = safeSpotNpc.getWorldLocation();
        if (Rs2Player.getWorldLocation().distanceTo(safeSpotLocation) > 1) {
            final WorldPoint temp = safeSpotLocation;
            Microbot.log("Moving to the safe spot at: " + safeSpotLocation);
            Rs2Walker.walkFastCanvas(safeSpotLocation);
            sleepUntilTrue(() -> !Rs2Player.isMoving() && Rs2Player.getWorldLocation().distanceTo(temp) <= 1);
        }

        // Attack Jaguar initially
        Rs2Npc.interact(13021, "Attack");

        Microbot.log("At safe spot. Waiting for blood splats and Jaguar cycle.");

        // Main loop: Process Jaguar phase
        while (this.isRunning()){
            NPC jaguar = plugin.findNpcById(13021);
            if (jaguar == null) {
                Microbot.log("Jaguar despawned. Exiting Jaguar Phase.");
                currentState = ExampleState.MAIN_FIGHT;
                return;
            }
            if (plugin.isBOSS_DEAD()) {
                claimChest = true;
                Microbot.log("Boss is dead, restart loop.");
                currentState = ExampleState.COLLECT_SUPPLIES;
                return;
            }

            // Monitor for blood splats
            WorldPoint closestBloodSplat = plugin.getClosestBloodSplat(Rs2Player.getWorldLocation());
            while (this.isRunning() && closestBloodSplat == null ||
                    !plugin.getDangerousTiles().contains(closestBloodSplat) ||
                    Rs2Player.getWorldLocation().distanceTo(closestBloodSplat) > 1) {
                closestBloodSplat = plugin.getClosestBloodSplat(Rs2Player.getWorldLocation());
                sleep(200, 400);
            }


            // Handle attack cycle
            if (!plugin.isJaguarAttacked()) {
                Microbot.log("Attacking Jaguar...");
                Rs2Npc.interact(13021, "Attack");
                sleep(200, 400); // Wait for the action
            }

            // Tick monitoring logic
            int tickCounter = 0;
            int baseTick = plugin.getLastJaguarAttackTick(); // Synchronize with last attack tick
            resync = plugin.isResyncTicks();
            Microbot.log("Current state of resync:" + resync);

            if (baseTick == -1) {
                Microbot.log("Jaguar has not attacked yet. Waiting...");
                sleepUntilTrue(() -> plugin.getLastJaguarAttackTick() != -1);
                baseTick = plugin.getLastJaguarAttackTick();
            }
            Rs2Player.eatAt(80);

            while (this.isRunning() && plugin.findNpcById(13021) != null) {
                int playerHP = Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS);
                int foodRemaining = Rs2Inventory.count(COOKED_LIZARD_ID);
                if (playerHP < 35) {
                    Microbot.log("Trying to escape as our HP count is " + Rs2Player.getBoostedSkillLevel(Skill.HITPOINTS));
                    Rs2GameObject.interact(53003, "Quick-escape");
                    sleep(3000);
                    currentState = ExampleState.COLLECT_SUPPLIES;
                    return;
                }

                if(resync){
                    safeSpotLocation = safeSpotNpc.getWorldLocation();
                    final WorldPoint temp = safeSpotLocation;
                    if (Rs2Player.getWorldLocation().distanceTo(safeSpotLocation) > 1) {
                        Microbot.log("Moving to the safe spot at: " + safeSpotLocation);
                        Rs2Walker.walkFastCanvas(safeSpotLocation);
                        sleepUntilTrue(() -> !Rs2Player.isMoving() && Rs2Player.getWorldLocation().distanceTo(temp) <= 1);
                    }
                    plugin.setResync(false);
                }
                int currentTick = plugin.getElapsedTicks();
                closestBloodSplat = plugin.getClosestBloodSplat(Rs2Player.getWorldLocation());

                // Calculate tick offset from the last Jaguar attack
                int offset = (currentTick - baseTick) % 6;

                if (offset == 0) {
                    if (closestBloodSplat != null) {
                        Rs2Walker.walkFastCanvas(closestBloodSplat);
                    }
                } else if (offset == 1) {
                    Rs2Npc.interact(13021, "Attack");
                }

                if (offset == 0) {
                    tickCounter = 0; // Reset counter to synchronize with the Jaguar
                }

                waitForNextTick(currentTick);
                tickCounter++;
            }

        }
    }
}
