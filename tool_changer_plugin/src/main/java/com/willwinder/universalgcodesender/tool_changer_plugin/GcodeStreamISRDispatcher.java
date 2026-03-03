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
import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import com.willwinder.universalgcodesender.listeners.ControllerStatus;
import com.willwinder.universalgcodesender.listeners.ControllerListener;
import com.willwinder.universalgcodesender.model.Alarm;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.types.GcodeCommand;

/**
 *
 * @author matthew-papesh
 */
public class GcodeStreamISRDispatcher extends FiniteStateMachine implements ControllerListener {
    private static final Logger LOG = Logger.getLogger(UGSToolChangerMain.class.getName());
    private final BackendAPI backend;
    private GcodeStreamCache gcodeStreamCache;
    private GcodeCommand nextCommand;
    private final List<GcodeStreamISR> isrs = new List<GcodeStreamISR>();
    private GcodeStreamISR triggeredISR = null;
    private int isrIterator = 0;
    
    private enum DispatcherState { POLL, QUIESCE, INTERRUPT }
    private final Map<DispatcherState, UnaryOperator<DispatcherState>> FSM = new EnumMap<>(DispatcherState.class);
    private DispatcherState currentState = null;
    
    
    /**
     * @brief Creates a `GcodeStreamISRDispatcher` instance. 
     */
    public GcodeStreamISRDispatcher() {
        super(DispatcherState.class, DispatcherState.POLL);
        // retrieve backend from UGS Platform
        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        gcodeStreamCache = new GcodeStreamCache();
        backend.getController().addListener(this);   
        nextCommand = null;
        
        // add dispatcher states 
        addState(DispatcherState.POLL, this::pollISRsState);
        addState(DispatcherState.QUIESCE, this::quiesceMachineState);
        addState(DispatcherState.INTERRUPT, this::interruptOnISRState);
    }

    /**
     * @brief 
     * @param stream 
     */
    private void setGcodeStream(boolean stream) {
        try {
            if(stream && !backend.getController().isStreaming()) {
                backend.getController().resumeStreaming();
            } else if(!stream && backend.getController().isStreaming()) {
                backend.getController().pauseStreaming();
            }
        } catch(Exception e) {}
    }
    
    /**
     * @brief 
     * @return 
     */
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
     
    private DispatcherState pollISRsState(FiniteStateMachine s) {
        triggeredISR = getNextTriggeredISR();
        // done polling ISRs if returns null
        if(triggeredISR != null) {
            // ISR interrupted; halt streaming 
            setGcodeStream(false); // this will pause commandSent() listener!
            return DispatcherState.QUIESCE;
        } else {
            // no ISR interrupts; continue streaming 
            setGcodeStream(true); // this will contnue calling commandSent()
        }
        // continue polling
        return DispatcherState.POLL;
    }
    
    /**
     * Represents the quiesce dispatcher state. Quiesce is the process 
     * of safely bringing the CNC machine to a point of stasis by completing all currently 
     * active G-Code commands. The machine will still be active, but in an idle state once 
     * quiesce is complete.  
     * @param s
     * @return 
     */
    private DispatcherState quiesceMachineState(DispatcherState s) {
        setGcodeStream(false); // ensure machine is not sending new active commands 
        
        return null;
    }
    
    private DispatcherState interruptOnISRState(DispatcherState s) {
        return null;
    }
    
    /**
     * @brief 
     */
    private void run() {
       // nextCommand = gcodeStreamCache.getNextGcodeCommand();
       runStates();
    }
    
    /**
     * @brief Stops the ISR dispatcher and its streaming cache. 
     */
    private void stop() {
        nextCommand = null;
        triggeredISR = null;
        isrIterator = 0;
        currentState = DispatcherState.POLL;
        gcodeStreamCache.close();
    }
    
    /**
     * @brief Starts the ISR dispatcher and its streaming cache
     */
    private void start() {
        nextCommand = null;
        triggeredISR = null;
        isrIterator = 0;
        currentState = DispatcherState.POLL;
        gcodeStreamCache.open();
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
    @Override public void commandSkipped(GcodeCommand command) { run(); }

    @Override public void streamPaused() {}
    @Override public void streamResumed() {}
    @Override public void receivedAlarm(Alarm alarm) {}
    @Override public void commandComplete(GcodeCommand command) {}
    @Override public void probeCoordinates(Position p) {}
    @Override public void statusStringListener(ControllerStatus status) {}
}
