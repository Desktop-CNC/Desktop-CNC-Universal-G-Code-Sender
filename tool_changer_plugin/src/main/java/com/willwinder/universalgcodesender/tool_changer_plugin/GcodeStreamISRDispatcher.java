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
import java.util.logging.Logger;
import java.util.List;

import com.willwinder.universalgcodesender.listeners.ControllerStatus;
import com.willwinder.universalgcodesender.listeners.ControllerListener;
import com.willwinder.universalgcodesender.model.Alarm;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.types.GcodeCommand;

/**
 *
 * @author matthew-papesh
 */
public class GcodeStreamISRDispatcher implements ControllerListener {
    private static final Logger LOG = Logger.getLogger(UGSToolChangerMain.class.getName());
    private final BackendAPI backend;
    private GcodeStreamCache gcodeStreamCache;
    private GcodeCommand nextCommand;
    private List<GcodeStreamISR> isrs = new List<GcodeStreamISR>();
    private int isrIterator = 0;
    
    /**
     * @brief Creates a `GcodeStreamISRDispatcher` instance. 
     */
    public GcodeStreamISRDispatcher() {
        // retrieve backend from UGS Platform
        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        // CREATE CACHE BEFORE ADDING LISTENER! => This ensure the cache updates before this dispatcher on ControllerListener
        gcodeStreamCache = new GcodeStreamCache();
        backend.getController().addListener(this);   
        nextCommand = null;
    }
    
    /**
     * @brief Stops the ISR dispatcher and its streaming cache. 
     */
    private void stop() {
        nextCommand = null;
        isrIterator = 0;
        gcodeStreamCache.close();
    }
    
    /**
     * @brief Starts the ISR dispatcher and its streaming cache
     */
    private void start() {
        nextCommand = null;
        isrIterator = 0;
        gcodeStreamCache.open();
    }
    
    private GcodeStreamISR getNextTriggeredISR() {
        for(int i = isrIterator; i < isrs.size(); i++) {
            
            if(isrs.get(i).shouldInterrupt(nextCommand.getCommandString())) {
                isrIterator = i + 1;
                return isrs.get(i);
            }
        }
        
        isrIterator = 0;
        return null;
    }
    

    /**
     * @brief 
     */
    private void run() {
        nextCommand = gcodeStreamCache.getNextGcodeCommand();
        
    }
    
    /**
     * @brief 
     * @param isr 
     */
    public void attachISR(GcodeStreamISR isr) {
        isrs.add(isr);
    }
    
    /** An event triggered when a stream is stopped */
    @Override public void streamCanceled() { stop(); }
    /** The file streaming has completed. */
    @Override public void streamComplete() { stop(); }
    /** An event triggered when a stream is started */
    @Override public void streamStarted() { start(); }
    /** A command has successfully been sent to the controller. */
    @Override public void commandSent(GcodeCommand command) { run(); }
    
    /** A command in the stream has been skipped. */
    @Override
    public void commandSkipped(GcodeCommand command) { 
        nextCommand = gcodeStreamCache.getNextGcodeCommand(); 
    }

    @Override public void streamPaused() {}
    @Override public void streamResumed() {}
    @Override public void receivedAlarm(Alarm alarm) {}
    @Override public void commandComplete(GcodeCommand command) {}
    @Override public void probeCoordinates(Position p) {}
    @Override public void statusStringListener(ControllerStatus status) {}
}
