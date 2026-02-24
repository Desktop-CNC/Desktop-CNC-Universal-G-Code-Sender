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
import com.willwinder.universalgcodesender.listeners.ControllerListener;
import com.willwinder.universalgcodesender.listeners.ControllerState;
import com.willwinder.universalgcodesender.listeners.ControllerStatus;
import com.willwinder.universalgcodesender.listeners.MessageType;

import com.willwinder.universalgcodesender.model.Alarm;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.types.GcodeCommand;
import java.io.File;

/**
 * Represents a live-cache for the G-Code command stream. G-Code commands streaming is the process of 
 * sequentially sending commands from UGS to the CNC machine. The cache is live, as it listens and tracks the
 * next upcoming command. The cache only exists when G-Code is being streamed. 
 * @author matthew-papesh
 */
public class GcodeStreamCache implements ControllerListener {
    private final BackendAPI backend;
    private GcodeStreamReader gcodeStreamCache;
    private GcodeCommand nextCommand;
    
    public GcodeStreamCache() {
         // retrieve backend from UGS Platform
        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        backend.getController().addListener(this);
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
            backend.dispatchMessage(MessageType.ERROR, "Warning: Failed Failed on GcodeStreamCache.java => " + e.getMessage() + "\n");
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
    
    private void resetGcodeStream() {
        if(gcodeStreamCache == null) {
            return;
        }
        
        try {
            gcodeStreamCache.close();
            gcodeStreamCache = null;
            nextCommand = null;
        } catch(Exception e) {
            backend.dispatchMessage(MessageType.ERROR, "Warning: Failed on GcodeStreamCache.java => " + e.getMessage() + "\n");
            gcodeStreamCache = null;
            nextCommand = null;
        }
    }
    
    /**
     * Reset the cache 
     */
    @Override
    public void streamCanceled() {
        resetGcodeStream();
    }

    /**
     * The file streaming has completed.
     */
    @Override
    public void streamComplete() {
        resetGcodeStream();
    }
    
    /**
     * An event triggered when a stream is started
     */
    @Override
    public void streamStarted() {
       resetGcodeStream();
       fetchGcodeStream(); // cache the G-Code stream
    }
    
    @Override public void commandSkipped(GcodeCommand command) {}
    @Override public void commandSent(GcodeCommand command) {}
    @Override public void commandComplete(GcodeCommand command) {}
    @Override public void streamPaused() {}
    @Override public void streamResumed() {}
    @Override public void receivedAlarm(Alarm alarm) {}
    @Override public void probeCoordinates(Position p) {}
    @Override public void statusStringListener(ControllerStatus status) {}
}
