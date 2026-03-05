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

import com.willwinder.ugs.nbp.lib.lookup.CentralLookup;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.utils.GcodeStreamReader;
import com.willwinder.universalgcodesender.listeners.MessageType;

import com.willwinder.universalgcodesender.types.GcodeCommand;
import java.io.File;

/**
 * Represents a cache for the G-Code command stream. G-Code commands streaming is the process of 
 * sequentially sending commands from UGS to the CNC machine. 
 * @author matthew-papesh
 */
public class GcodeStreamCache {
    private final BackendAPI backend;
    private GcodeStreamReader gcodeStreamCache;
    private GcodeCommand nextCommand;
    
    public GcodeStreamCache() {
         // retrieve backend from UGS Platform
        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        gcodeStreamCache = null;
        nextCommand = null;
    } 
    
    /**
     * @brief Retrieves and pops the next G-Code command from the fetched G-Code stream cache. 
     * @returns The next command
     */
    public GcodeCommand getNextGcodeCommand() {
        try {
           nextCommand = gcodeStreamCache.getNextCommand();
        } catch(Exception e) {
            backend.dispatchMessage(MessageType.ERROR, String.format("Warning: Failed Failed on GcodeStreamCache.java => %s\n", e.getMessage()));
            nextCommand = null;
        }
        
        return nextCommand;
    }
    
    /**
     * @brief Builds a cache of G-Code commands that will be streamed when running the CNC machine. 
     * Computes a cache==null if caching fails. 
     */
    private void fetchGcodeStream() {
        try {
            File processedGcode = backend.getProcessedGcodeFile();
            gcodeStreamCache = new GcodeStreamReader(processedGcode, backend.getCommandCreator());
        } catch(Exception e) {
            backend.dispatchMessage(MessageType.ERROR , "Warning: Failed to cache NC file for ATC Plugin!\n");
            gcodeStreamCache = null;
            nextCommand = null;
        }
    }
    
    /**
     * @brief 
     */
    private void resetGcodeStream() {
        if(gcodeStreamCache == null) {
            return;
        }
        
        try {
            gcodeStreamCache.close();
            gcodeStreamCache = null;
            nextCommand = null;
        } catch(Exception e) {
            backend.dispatchMessage(MessageType.ERROR, String.format("Warning: Failed on GcodeStreamCache.java => %s\n", e.getMessage()));
            gcodeStreamCache = null;
            nextCommand = null;
        }
    }
    
    /**
     * @brief Creates and opens the cache.
     */
    public void open() {
        resetGcodeStream();
        fetchGcodeStream();
    }
    
    /**
     * @brief Reset and close the cache 
     */
    public void close() {
        resetGcodeStream();
    }
}
