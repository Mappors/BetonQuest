/**
 * BetonQuest - advanced quests for Bukkit
 * Copyright (C) 2015  Jakub "Co0sh" Sapalski
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package pl.betoncraft.betonquest.api;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

import org.bukkit.Bukkit;

import pl.betoncraft.betonquest.BetonQuest;
import pl.betoncraft.betonquest.InstructionParseException;
import pl.betoncraft.betonquest.config.Config;
import pl.betoncraft.betonquest.config.ConfigPackage;
import pl.betoncraft.betonquest.utils.Debug;
import pl.betoncraft.betonquest.utils.PlayerConverter;

/**
 * Superclass for all objectives. You need to extend it in order to create new
 * custom objectives.
 * <p/>
 * Registering your objectives is done through {@link
 * pl.betoncraft.betonquest.BetonQuest#registerObjectives(String, Class<?
 * extends Objective>) registerObjectives} method.
 * 
 * @author Jakub Sapalski
 */
public abstract class Objective {
    
    protected final String instructions;
    protected final ConfigPackage pack;
    protected final String[] conditions;
    protected final String[] events;
    protected final String label;
    
    /**
     * Contains all data objects of the players with this objective active
     */
    protected HashMap<String, ObjectiveData> dataMap = new HashMap<>();
    /**
     * Should be set with the data class used to hold players' information
     */
    protected Class<? extends ObjectiveData> template;

    /**
     * Creates new instance of the objective. The objective should parse
     * instruction string at this point and extract all the data from it.
     * </p>
     * <b>Do not register listeners here!</b> There is a {@link #start()}
     * method for it.
     * 
     * @param instructions
     *            instruction string passed at runtime. You need to extract all
     *            required data from it and display errors if there is anything
     *            wrong.
     * @throws InstructionParseException if the syntax is wrong
     */
    public Objective(String packName, String label, String instructions)
            throws InstructionParseException {
        this.instructions = instructions;
        this.pack = Config.getPackage(packName);
        this.label = label;
        // extract events and conditions
        String[] tempEvents     = new String[]{},
                 tempConditions = new String[]{};
        for (String part : instructions.split(" ")) {
            if (part.startsWith("events:")) {
                tempEvents = part.substring(7).split(",");
                for (int i = 0; i < tempEvents.length; i++) {
                    if (!tempEvents[i].contains(".")) {
                        tempEvents[i] = packName + "." + tempEvents[i];
                    }
                }
            }
            if (part.startsWith("conditions:")) {
                tempConditions = part.substring(11).split(",");
                for (int i = 0; i < tempConditions.length; i++) {
                    if (!tempConditions[i].contains(".")) {
                        tempConditions[i] = packName + "." + tempConditions[i];
                    }
                }
            }
        }
        // make them final
        events     = tempEvents;
        conditions = tempConditions;
    }
    
    /**
     * This method is called by the plugin when the objective needs to
     * start listening for events. Register your Listeners here!
     */
    public abstract void start();

    /**
     * This method is called by the plugin when the objective needs to be
     * stopped. You have to unregister all Listeners here.
     */
    public abstract void stop();
    
    /**
     * This method should return the default data instruction for the objective,
     * ready to be parsed by the ObjectiveData class.
     * 
     * @return the default data instruction string
     */
    public abstract String getDefaultDataInstruction();
    
    /**
     * This method should return various properties of the objective, formatted
     * as readable Strings. An example would be "5h 5min" for "time_left" keyword
     * in "delay" objective or "12" for keyword "mobs_killed" in "mobkill" objective.
     * The method is not abstract since not all objectives need to have properties,
     * i.e. "die" objective.
     * 
     * @return the property with given name
     */
    public String getProperty(String name) {
        return null;
    }
    
    /**
     * This method fires events for the objective and removes it from player's
     * list of active objectives. Use it when you detect that the objective has
     * been completed. It deletes the objective using delete() method.
     */
    public final void completeObjective(final String playerID) {
        // remove the objective from player's list
        BetonQuest.getInstance().getDBHandler(playerID).deleteObjective(label);
        Debug.info("Objective \"" + label + "\" has been completed for player "
                + PlayerConverter.getName(playerID) + ", firing final events.");
        // fire all events
        for (String event : events) {
            BetonQuest.event(playerID, event);
        }
        Debug.info("Firing events in objective \"" + label + "\" for player "
                + PlayerConverter.getName(playerID) + " finished");
    }

    /**
     * Checks if all conditions has been met. Use it when the player has done
     * something that modifies data (e.g. killing zombies). If conditions are
     * met, you can safely modify the data.
     * 
     * @return if all conditions of this objective has been met
     */
    public final boolean checkConditions(final String playerID) {
        Debug.info("Condition check in \"" + label + "\" objective for player "
                + PlayerConverter.getName(playerID));
        for (String condition : conditions) {
            if (!BetonQuest.condition(playerID, condition)) {
                return false;
            }
        }
        // if there are no conditions or all of them are met return true
        return true;
    }
    
    /**
     * Adds this new objective to the player. Also updates the database
     * with the objective.
     * 
     * @param playerID
     */
    public final void newPlayer(String playerID) {
        String def = getDefaultDataInstruction();
        addPlayer(playerID, def);
        BetonQuest.getInstance().getDBHandler(playerID).addObjToDB(label, def);
    }
    
    /**
     * Adds this objective to the player.
     * 
     * @param playerID
     *          ID of the player
     * @param instruction
     *          instruction string for player's data
     */
    public final void addPlayer(String playerID, String instruction) {
        final String ERROR = "There was some error. Please send it to the"
                + " developer: <coosheck@gmail.com>";
        ObjectiveData data = null;
        try {
            data = template.getConstructor(String.class, String.class, String.class)
                    .newInstance(instruction, playerID, label);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof InstructionParseException) {
                Debug.error("Error while loading " + label +
                        " objective data for player " + PlayerConverter
                        .getName(playerID) +": " + e.getCause().getMessage());
            } else {
                e.printStackTrace();
                Debug.error(ERROR);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Debug.error(ERROR);
        }
        if (dataMap.isEmpty()) {
            start();
        }
        dataMap.put(playerID, data);
    }
    
    /**
     * Removes the objective from the player. It does not complete it nor
     * update the database. In order to complete it, use completeObjective()
     * instead. In order to remove it from database use 
     * DatabaseHandler.deleteObjective() instead.
     * 
     * @param playerID
     *          ID of the player
     */
    public final void removePlayer(String playerID) {
        dataMap.remove(playerID);
        if (dataMap.isEmpty()) {
            stop();
        }
    }
    
    /**
     * Checks if the player has this objective
     * 
     * @param playerID
     *          ID of the player
     * @return true if the player has this objective
     */
    public final boolean containsPlayer(String playerID) {
        return dataMap.containsKey(playerID);
    }
    
    /**
     * Returns the data of the specified player
     * 
     * @param playerID
     *          ID of the player
     * @return the data string for this objective
     */
    public final String getData(String playerID) {
        ObjectiveData data = dataMap.get(playerID);
        if (data == null) {
            return null;
        }
        return dataMap.get(playerID).toString();
    }

    /**
     * Returns the label of this objective. Don't worry about it, it's only used
     * by the rest of BetonQuest's logic.
     * 
     * @return the label of the objective
     */
    public final String getLabel() {
        return label;
    }
    
    /**
     * Should be called at the end of the use of this objective, for example
     * when reloading the plugin. It will unregister listeners and save all
     * player's data to their "inactive" map.
     */
    public void close() {
        stop();
        for (String playerID : dataMap.keySet()) {
            BetonQuest.getInstance().getDBHandler(playerID)
                    .addRawObjective(label, dataMap.get(playerID).toString());
        }
    }
    
    /**
     * Stores player's data for the objective
     * 
     * @author Jakub Sapalski
     */
    protected static class ObjectiveData {
        
        protected String instruction;
        protected String playerID;
        protected String objID;
        
        /**
         * The constructor needs to parse the data in instruction string
         * and place it the fields.
         * 
         * @param instruction
         */
        public ObjectiveData(String instruction, String playerID, String objID) {
            this.instruction = instruction;
            this.playerID = playerID;
            this.objID = objID;
        }

        /**
         * This method should return the whole instruction string, which can be
         * successfully parsed by the constructor.
         * 
         * @return the instruction string
         */
        public String toString() {
            return instruction;
        }
        
        /**
         * Should be called when the data inside ObjectiveData changes. It will
         * update the database with the changes.
         */
        protected void update() {
            QuestDataUpdateEvent event = new QuestDataUpdateEvent(playerID, objID,
                    toString());
            Bukkit.getPluginManager().callEvent(event);
        }
        
    }

}