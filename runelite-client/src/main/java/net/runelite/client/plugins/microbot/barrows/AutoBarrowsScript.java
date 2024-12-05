package net.runelite.client.plugins.microbot.barrows;

import com.fasterxml.jackson.databind.jsontype.impl.MinimalClassNameIdResolver;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.barrows.enums.STATE;
import net.runelite.client.plugins.microbot.barrows.models.TheBarrowsBrothers;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.util.ImageCapture;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static java.lang.Math.ceil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public class AutoBarrowsScript extends Script {

    private TheBarrowsBrothers tunnelBrother;


    @Inject
    private Client client;

    public STATE state = STATE.IDLE;
    public boolean forceFirstCrypt = true;

    public static String version = "0.0.1";

    AutoBarrowsConfig config;

    AutoBarrowsPlugin plugin;

    public boolean run(AutoBarrowsConfig config, AutoBarrowsPlugin plugin) {
        Microbot.enableAutoRunOn = false;
        this.config = config;
        this.plugin = plugin;


        Microbot.log("Brothers status ready");

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || Microbot.pauseAllScripts) return;
                if (!super.run()) return;

                switch (state) {
                    case IDLE:
                        handleIdleState();
                        Microbot.log("End of IDLE case...");
                        break; // Prevent falling into the next case

                    case SEARCHING_GRAVE:
                        Microbot.log("Searching grave");
                        searchGrave(plugin);
                        break;

                    case WALKING:
                        walkToNextCrypt(plugin);
                        break;

                    case DIGGING:
                        digCrypt(plugin);
                        break;

                    case FIGHTING:
                        fightBrother(plugin);
                        break;

                    case LEAVING_CRYPT:
                        leaveCrypt(plugin);
                        break;

                    case CLAIMING:
                        handleClaimingState();
                        break;

                    case RESTARTING:
                        handleRestartingState();
                        break;

                    default:
                        Microbot.log("Unhandled state: " + state);
                        break;
                }

            } catch (Exception ex) {
                Microbot.log("Error: " + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleIdleState() {
        if(Rs2Inventory.hasItem(868)){
            Rs2Inventory.interact(868,"Wield");
        }
        int currentRegion = Rs2Player.getWorldLocation().getRegionID();
        if(currentRegion == 12083 || currentRegion == 32767){
            state = STATE.RESTARTING;
            return;
        }
        if (!plugin.isInCrypt()) {
            Microbot.log("Not in crypt, transitioning to WALKING.");
            state = STATE.WALKING;
            return;
        } else {
            Microbot.log("Already in crypt, transitioning to LEAVING_CRYPT.");
            state = STATE.LEAVING_CRYPT;
            return;
        }
    }

    private void walkToNextCrypt(AutoBarrowsPlugin plugin) {
        if(plugin.isInCrypt()){
            state = STATE.LEAVING_CRYPT;
            return;
        }
        TheBarrowsBrothers brother = getNextAliveBrother();

        if (brother == null) {
            state = STATE.CLAIMING;
            return;
        }
        WorldPoint nextCrypt = brother.getLocation();

        Microbot.log("Walking to crypt: " + brother.getName());
        Rs2Walker.walkTo(nextCrypt);
        sleepUntil(() -> !Rs2Player.isMoving(), 3000);
        Rs2Walker.walkFastCanvas(nextCrypt);
        sleepUntilTrue(() -> (!Rs2Player.isAnimating() && !Rs2Player.isMoving()));
        state = STATE.DIGGING;
        return;
    }

    private void digCrypt(AutoBarrowsPlugin plugin) {
        int retryCount = 0;
        while(!plugin.isInCrypt()) {
            if (Rs2Inventory.contains("Spade")) {
                Microbot.log("Digging");
                Rs2Inventory.interact("Spade", "Dig");
                sleepUntil(() -> plugin.isInCrypt(), 3000);
            } else {
                Microbot.log("Spade not found. Returning to IDLE.");
                state = STATE.IDLE;
                return;
            }
            if (plugin.isInCrypt()) {
                Microbot.log("We are in the crypt");
                state = STATE.SEARCHING_GRAVE;
                break;
            } else {
                if (retryCount > 3) {
                    Microbot.log("EXCEPTION: Failed to dig into crypt after 3 tries, review walking logic");
                    return;
                }
                Microbot.log("Not in crypt after digging, sleep and try again");
                sleep(2000, 3500);
                retryCount++;
            }
        }
    }

    private void searchGrave(AutoBarrowsPlugin plugin) {
        Microbot.log("Try to open sarcophagus");
        String brotherName = getCurrentCryptBrother().getName();
        int objectId = plugin.getSarcophagusId(brotherName);
        Rs2GameObject.interact(objectId, "Search");
        sleep(600,900);
        sleepUntilTrue(() -> (!Rs2Player.isAnimating() && !Rs2Player.isMoving()));

        if (Rs2Dialogue.isInDialogue()) {
            Microbot.log("Found the tunnel. Skipping and marking as tunnel brother.");
            Rs2Dialogue.clickContinue();
            sleep(900, 1200);
            Rs2Keyboard.keyPress('2'); // Continue without claiming

            // Identify the tunnel brother based on the current crypt
            TheBarrowsBrothers currentBrother = getCurrentCryptBrother();
            if (currentBrother != null) {
                tunnelBrother = currentBrother;
                Microbot.log("Tunnel is in: " + tunnelBrother.getName());
            } else {
                Microbot.log("Error: Could not identify tunnel brother.");
            }

            state = STATE.LEAVING_CRYPT;
            return;
        } else {
            Microbot.log("No dialogue popup, fight NPC");
            state = STATE.FIGHTING;
            return;
        }
    }

    private void fightBrother(AutoBarrowsPlugin plugin) {
        Microbot.log("Starting fight!");
        TheBarrowsBrothers brother = getCurrentCryptBrother();
        if (brother == null) {
            state = STATE.CLAIMING;
            return;
        }

        // Identify the target NPC
        NPC target = Microbot.getClient().getHintArrowNpc();
        if (target == null) {
            Microbot.log("No target found. Exiting fight.");
            state = STATE.LEAVING_CRYPT;
            return;
        }

        Rs2Npc.attack(target);
        sleep(1200);
        Microbot.log("Waiting for fight to complete...");

        // Sleep until the target is dead or the fight ends
        boolean fightEnded = sleepUntil(() -> {
            boolean targetIsDead = target.getHealthRatio() == 0 || target.isDead();
            boolean notInCombat = !Rs2Combat.inCombat();
            boolean brotherKilled = Microbot.getVarbitValue(brother.getKilledVarbit()) > 0;
            return targetIsDead || notInCombat || brotherKilled;
        }, 15000); // Timeout after 15 seconds

        if (fightEnded) {
            if(config.lootKeys()){
                if(Rs2GroundItem.exists("Brimstone key",5)){
                    Rs2GroundItem.loot(23083);
                }
                sleep(2000);
            }
            Microbot.log("Brother killed: " + brother.getName());
            state = STATE.LEAVING_CRYPT;
        } else {
            Microbot.log("Fight timeout. Checking conditions again...");
            sleep(1000, 2000); // Small delay before retrying
            state = STATE.FIGHTING; // Retry the fight if conditions are unclear
        }
    }


    private void leaveCrypt(AutoBarrowsPlugin plugin) {
        if(forceFirstCrypt){
            forceFirstCrypt = false;
        }
        String brotherName = getCurrentCryptBrother().getName();
        int objectId = plugin.getStaircaseId(brotherName);
        Rs2GameObject.interact(objectId, "Climb-up");
        sleepUntil(() -> !plugin.isInCrypt(), 5000);

        state = STATE.WALKING;
        return;
    }

    private void handleRestartingState() {
        Microbot.log("Restarting state: Checking inventory and health.");

        int freeSlots = Rs2Inventory.getEmptySlots();
        if (freeSlots < config.minSlots()) {
            Microbot.log("Not enough inventory space. Depositing items at bank...");
            depositAtBank();
        }

        double healthPercentage = (double) Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) * 100
                / Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        if (healthPercentage < config.minHP()) {
            Microbot.log("Health below threshold. Healing up...");
            healUp();
        }

        Microbot.log("Teleporting back to Barrows.");
        tpBackToBarrows();
        state = STATE.IDLE;
    }

    private void healUp() {
        sleep(3000);
        Rs2Inventory.interact("Clue Compass","Teleport");
        sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(12255235));
        sleep(5000);
        Widget bankTP = findWidgetByText("Draynor Village (Market)");
        if(bankTP != null) {
            Rs2Widget.clickWidget(bankTP);
        }
        sleep(5000);
        Rs2Walker.walkFastCanvas(new WorldPoint(3093,3245,0));
        sleepUntilTrue(() -> !Rs2Player.isMoving());
        sleep(5000);
        Rs2GameObject.interact(10355, "Bank");
        sleep(2200);

        double healthPercentage = (double) Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) * 100
                / Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        double missingHP = 100 - healthPercentage;
        int sharkAmount = (int) Math.ceil(missingHP / 15); // Round up to ensure enough sharks

        if (!Rs2Bank.isOpen()) {
            Microbot.log("Bank is not open. Attempting to open bank...");
            Rs2Npc.interact(1613, "Bank"); // NPC ID for a banker; replace with the correct one
            sleepUntilTrue(() -> Rs2Bank.isOpen());
        }

        if (Rs2Bank.isOpen()) {
            Rs2Bank.withdrawX(385, sharkAmount); // Withdraw sharks
            sleep(1200); // Small delay to ensure action completes
            Rs2Bank.closeBank();
            sleep(1200);
            while(Rs2Inventory.hasItem(385)){
                Rs2Player.eatAt(100);
                sleep(2000);
            }
        } else {
            Microbot.log("Failed to open bank. Aborting heal-up.");
        }
    }

    private void depositAtBank() {
        sleep(3000);
        Rs2Inventory.interact("Clue Compass","Teleport");
        sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(12255235));
        sleep(5000);
        Widget bankTP = findWidgetByText("Draynor Village (Market)");
        if(bankTP != null) {
            Rs2Widget.clickWidget(bankTP);
        }
        sleep(5000);
        Rs2Walker.walkFastCanvas(new WorldPoint(3093,3245,0));
        sleepUntilTrue(() -> !Rs2Player.isMoving());
        sleep(5000);
        Rs2GameObject.interact(10355, "Bank");
        sleep(2200);
        Rs2Bank.depositAllExcept(952,868,557,563,30363);
        sleep(1200);
        Rs2Bank.closeBank();

    }

    private void tpBackToBarrows() {
        Microbot.log("Teleporting to house...");
        Rs2Inventory.interact(1381, "Wield");
        sleep(900,1200);
        Rs2Magic.cast(MagicAction.TELEPORT_TO_HOUSE);
        sleepUntilTrue(() -> !Rs2Player.isAnimating());

        sleep(6500);

        if(Rs2GameObject.exists(15481)){
            Rs2GameObject.interact(15481, "Home");
            sleep(6500);
        }
        Microbot.log("Walking to Barrows portal...");
        Rs2Walker.walkFastCanvas(new WorldPoint(1869, 5731, 0));
        sleepUntilTrue(() -> !Rs2Player.isMoving());
        sleep(6500);

        Microbot.log("Entering Barrows portal...");
        Rs2GameObject.interact(37591, "Enter");
        sleep(6500);

        Microbot.log("Teleporting back to Barrows...");
        Rs2Inventory.interact(868, "Wield");
        Rs2Walker.walkTo(new WorldPoint(3565, 3300, 0));
        sleepUntilTrue(() -> !Rs2Player.isMoving());
        sleep(2500);
        if(!forceFirstCrypt){
            forceFirstCrypt = true;
        }
    }


    private void handleClaimingState() {
        Microbot.log("All brothers killed");
        Rs2Inventory.interact("Clue Compass","Teleport");
        sleepUntilTrue(() -> Rs2Widget.isWidgetVisible(12255235));
        sleep(600);
        Widget barrowsTP = findWidgetByText("Barrows Chest");
        if(barrowsTP != null) {
            Rs2Widget.clickWidget(barrowsTP);
        }
        sleepUntilTrue(() -> plugin.isInCrypt());
        sleep(1200);
        Rs2Walker.walkFastCanvas(new WorldPoint(3551,9694,0));
        sleepUntilTrue(() -> !Rs2Player.isMoving());
        sleep(1200);
        Microbot.log("Open chest by ID");
        Rs2GameObject.interact(20973,"Open");
        sleep(3000);
        Microbot.log("Wait for combat");
        NPC target = Microbot.getClient().getHintArrowNpc();
        Rs2Npc.attack(target);
        sleep(1200);
        Microbot.log("Waiting for fight to complete...");
        // Sleep until the target is dead or the fight ends
        boolean fightEnded = sleepUntil(() -> {
            boolean targetIsDead = target.getHealthRatio() == 0 || target.isDead();
            boolean notInCombat = !Rs2Combat.inCombat();
            boolean brotherKilled = Microbot.getVarbitValue(tunnelBrother.getKilledVarbit()) > 0;
            return targetIsDead || notInCombat || brotherKilled;
        }, 15000); // Timeout after 15 seconds
        if(fightEnded){
            Rs2GameObject.interact(20973,"Open");
        }else{
            sleep(8000);
            Rs2GameObject.interact(20973,"Open");
        }
        sleep(2000);
        Microbot.log("Teleporting to house...");
        Rs2Inventory.interact(1381, "Wield");
        sleep(900,1200);
        Rs2Magic.cast(MagicAction.TELEPORT_TO_HOUSE);
        sleepUntilTrue(() -> !Rs2Player.isAnimating());
        sleep(600,900);
        //widget to bank all
        //tp out, heal, go again
        state = STATE.RESTARTING;
        return;

    }

    public Widget findWidgetByText(String searchText) {
        Widget parentWidget = Rs2Widget.getWidget(12255235);

        if (parentWidget != null) {
            for (Widget child : parentWidget.getDynamicChildren()) {
                if (child != null && child.getText() != null && child.getText().contains(searchText)) {
                    Microbot.log("Found widget with text: " + child.getText());
                    return child; // Return the matching widget
                }
            }
            Microbot.log("No widget with text containing '" + searchText + "' found.");
        } else {
            Microbot.log("Parent widget not found");
        }

        return null; // Return null if no matching widget is found
    }

    private TheBarrowsBrothers pickRandomBrother() {
        List<TheBarrowsBrothers> allBrothers = Arrays.asList(TheBarrowsBrothers.values());
        int randomIndex = new Random().nextInt(allBrothers.size());
        return allBrothers.get(randomIndex);
    }


    private TheBarrowsBrothers getNextAliveBrother() {

        if (forceFirstCrypt) {
            Microbot.log("ForceFirstCrypt is enabled. Picking a random brother.");
            TheBarrowsBrothers randomBrother = pickRandomBrother();
            Microbot.log("Randomly selected brother: " + randomBrother.getName());
            forceFirstCrypt = false; // Disable the flag after picking the first crypt
            return randomBrother;
        }

        for (TheBarrowsBrothers brother : TheBarrowsBrothers.values()) {
            // Skip the tunnel brother
            if (brother == tunnelBrother) {
                continue;
            }

            // Check if the brother is alive using Varbits
            boolean isAlive = Microbot.getVarbitValue(brother.getKilledVarbit()) == 0;
            if (isAlive) {
                return brother;
            }
        }
        return null; // All brothers are dead or in the treasure room
    }

    private TheBarrowsBrothers getCurrentCryptBrother() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation(); // Get the player's current location

        if (playerLocation == null) {
            Microbot.log("Player location is null. Unable to determine crypt brother.");
            return null;
        }

        // Access the static getter from the plugin
        Map<TheBarrowsBrothers, WorldPoint> cryptLocations = AutoBarrowsPlugin.getCryptBelowLocations();

        for (Map.Entry<TheBarrowsBrothers, WorldPoint> entry : cryptLocations.entrySet()) {
            TheBarrowsBrothers brother = entry.getKey();
            WorldPoint cryptLocation = entry.getValue();

            // Check if the player's location is close to the crypt location
            if (cryptLocation.distanceTo(playerLocation) < 5) { // Adjust distance tolerance as needed
                return brother;
            }
        }

        Microbot.log("No matching crypt brother found for location: " + playerLocation);
        return null; // No matching brother found
    }





    @Override
    public void shutdown() {
        state = STATE.IDLE;
        super.shutdown();
        Microbot.log("Auto Barrows Script Stopped.");
    }
}
