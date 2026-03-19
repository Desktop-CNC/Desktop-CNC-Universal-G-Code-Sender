/*
 * Copyright (C) 2026 matthew-papesh
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
package com.willwinder.universalgcodesender.tool_changer_plugin;

import java.util.List;
import com.willwinder.universalgcodesender.types.GcodeCommand;
import com.willwinder.ugs.nbp.lib.lookup.CentralLookup;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.utils.GcodeStreamReader;
import com.willwinder.universalgcodesender.listeners.MessageType;

import com.willwinder.universalgcodesender.types.GcodeCommand;
import java.io.File;
import java.util.ArrayList;

/**
 * Represents a cache for the G-Code command stream. G-Code commands streaming is the process of 
 * sequentially sending commands from UGS to the CNC machine. 
 * @author matthew-papesh
 */
public class GcodeStreamCache {
    private final BackendAPI backend;
    private GcodeStreamReader gcodeStreamCache = null;
    private final ArrayList<GcodeCommand> commands = new ArrayList<>();
    private int commandsRetired = 0; // commands "currently" sent or skipped when milling 
    
    /**
     * @brief Creates a `GcodeStreamCache` instance. 
     */
    public GcodeStreamCache() {
         // retrieve backend from UGS Platform
        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
    } 
    
    /**
     * @brief Retrieves the i-th cached G-Code command reference cached for streaming.  
     * @param i The specified command index
     * @return G-Code command reference
     */
    public GcodeCommand getCommand(int i) {
        if(commands == null || commands.isEmpty() || i < 0 || i >= commands.size()) {
            return null;
        }
        return commands.get(i);
    }
    
    /**
     * @brief Retrieves the number of cached commands have been retired.
     * @return 
     */
    public int getCommandsRetired() {
        return commandsRetired;
    }
    
    /**
     * @brief Builds a cache of G-Code file and its commands
     */
    private void fetchGcodeStream() {
        try {
            // create stream cache 
            File processedGcode = backend.getProcessedGcodeFile();
            gcodeStreamCache = new GcodeStreamReader(processedGcode, backend.getCommandCreator());
            // memoize cache 
            GcodeCommand cmd = gcodeStreamCache.getNextCommand();
            while(cmd != null) {
                commands.add(cmd);
                cmd = gcodeStreamCache.getNextCommand();
            }     
        } catch(Exception e) {
            backend.dispatchMessage(MessageType.ERROR , "Warning: Failed to cache NC file for ATC Plugin!\n");
            gcodeStreamCache = null;
            commands.clear();
        }
    }
    
    /**
     * @brief Processes retired commands to reflect their states in the cache. Commands retired 
     * are recorded in the order the commands are cached! This function must be called in the order commands 
     * are sent/skipped by the G-Code stream controller! 
     * @param command The specified command observed for retirement
     */
    public void retireCommand(GcodeCommand command) {
        if(commandsRetired >= commands.size()) {
            return;
        }
        // record retired command state 
        commands.get(commandsRetired).setSkipped(command.isSkipped());
        commands.get(commandsRetired).setError(command.isError());
        commands.get(commandsRetired).setSent(command.isSent());
        commands.get(commandsRetired).setResponse(command.getResponse());
        commands.get(commandsRetired).setOk(command.isOk());
        commands.get(commandsRetired).setDone(command.isDone());
        // march/iterate commands retired 
        commandsRetired += 1;
    } 
    
    /**
     * @brief 
     */
    private void resetGcodeStream() {   
        if(gcodeStreamCache != null) {
            try {
                gcodeStreamCache.close();
                commands.clear();
                gcodeStreamCache = null;
                commandsRetired = 0;
            } catch(Exception e) {
                backend.dispatchMessage(MessageType.ERROR, String.format("Warning: Failed on GcodeStreamCache.java => %s\n", e.getMessage()));
                gcodeStreamCache = null;
            }
        }
    }
    
    /**
     * @brief Creates and opens the cache.
     */
    public void start() {
        resetGcodeStream();
        fetchGcodeStream();
    }
    
    /**
     * @brief Reset and close the cache 
     */
    public void stop() {
        resetGcodeStream();
    }
}
