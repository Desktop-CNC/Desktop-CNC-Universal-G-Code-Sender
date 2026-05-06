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
import java.util.ArrayList;

import com.willwinder.universalgcodesender.listeners.ControllerStatus;
import com.willwinder.universalgcodesender.listeners.ControllerListener;
import com.willwinder.universalgcodesender.listeners.MessageType;
import com.willwinder.universalgcodesender.model.Alarm;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.types.GcodeCommand;

/**
 * @brief Represents an Interrupt Service Routine (DHS) that interrupts G-Code streaming on evaluating a 
 * G-Code commands before it is sent and streamed. This dispatches all attached `GcodeStreamDHS` DHSs and tests 
 * for interrupts for every G-Code command streamed during a CNC machining process. All DHSs will be run when no
 * G-Code commands are actively running. 
 * 
 * @author matthew-papesh
 */
public class GcodeStreamDHSDispatcher implements ControllerListener {
    private static final Logger LOG = Logger.getLogger(UGSToolChangerMain.class.getName());
    private final BackendAPI backend; 
    private final GcodeStreamCache gcodeStreamCache;
    private final List<GcodeStreamDHS> DHSs = new ArrayList<GcodeStreamDHS>();
    
    // tracking parameters 
    private GcodeCommand nextCommand = null;
    private int DHSDwellCmdId = -1;
    private int DHSIterator = -1;
    private boolean isActive = false;
    
    public void toggle(boolean enable) {
        isActive = enable;
        if(!enable)
            this.stop();
    }
    
    /**
     * @brief Creates a `GcodeStreamDHSDispatcher` instance. 
     */
    public GcodeStreamDHSDispatcher() {
        // retrieve backend from UGS Platform
        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        gcodeStreamCache = new GcodeStreamCache();
        backend.getController().addListener(this);        
    }

    /**
     * @brief Polls untested DHSs to test them. Polling occurs on a G-Code command before it is streamed. 
     * An DHS is triggered if it meets conditions to be interrupted. The dispatcher state will be found to quiesce if it is enabled, 
     * otherwise it will be found to interrupt. The dispatcher state will be found to continue to poll should no DHS be triggered. 
     * Finally, the dispatcher only streams G-Code commands in the poll state. 
     * 
     * @note If quiesce is not enabled, the dispatcher will immediately run any triggered DHSs before ensuring there are no active G-Code commands 
     * @param enableQuiesce Specifies if machine should quiesce on DHS getting triggered.
     * @return 
     */
    private Boolean pollDHSs() {
        DHSIterator = getNextTriggeredDHS();
        if(DHSIterator != -1) { // check for DHS interrupts 
            setGcodeStream(false); // halt streaming
            return false;
        }
        // continue polling on no interrupts 
        DHSIterator = -1;  
        setGcodeStream(true); // continue streaming 
        return true;
    }
    
    /**
     * @brief Runs the interrupt logic of the current DHS.
     * @note This will pause the CNC machine should the DHS fail on running. 
     */
    private void interruptOnCurrentDHS() {
        if(DHSIterator >= 0) { // only interrupt on valid DHS
            
        
            try {
                String cmd = (this.nextCommand != null) ? this.nextCommand.getCommandString() : "";
                GcodeStreamDHS DHS = DHSs.get(DHSIterator);
                // run the interrupt 
                DHS.onBeforeInterrupt(cmd);
                boolean success = DHS.runInterruptBinary();
                DHS.onAfterInterrupt(cmd, success);
                // handle if interrupt fails 
                if(!success && !backend.isPaused()) {
                    backend.pauseResume(); 
                }        
            } catch(Exception e) {}
            this.setGcodeStream(true);
        }
    }
    
    
    /**
     * @brief Runs when a G-Code command has been completed by the CNC firmware (i.e., grblHAL). 
     * This method will quiesce the CNC machine and run all triggered DHSs until all have been run. 
     * This method will re-enable G-Code streaming once all DHS interrupts have been complete.
     * @param command 
     */
    @Override 
    public void commandComplete(GcodeCommand command) {
        if(command == null || !isActive)
            return;
        this.gcodeStreamCache.completeCommand(command); // MUST CALL THIS FIRST!
        this.nextCommand = this.gcodeStreamCache.getNextCommandToComplete();
        backend.dispatchMessage(MessageType.INFO, "completed: " + command.getCommandString() + ", next: " + ((this.nextCommand != null) ? this.nextCommand.getCommandString() : "NULL"));
        // evaluate DHSs for next command
        while(!pollDHSs()) {
           interruptOnCurrentDHS(); // run the interrupted DHS
        }
    }
    
    /** 
     * @brief A command in the stream has been skipped. 
     */
    @Override 
    public void commandSkipped(GcodeCommand command) {
        if(command == null || !isActive)
            return;
        this.gcodeStreamCache.retireCommand(command);
    } 
    
    /**
     * @brief Runs when a G-Code command has successfully been sent to the controller. 
     * @param command The command that was sent
     */
    @Override 
    public void commandSent(GcodeCommand command) {
        if(command == null || !isActive)
            return;
        this.gcodeStreamCache.retireCommand(command);
    }
    
    /**
     * @brief Stops the DHS dispatcher and its streaming cache. 
     */
    private void stop() {
        nextCommand = null;
        DHSIterator = -1;
        gcodeStreamCache.stop();
    }
    
    /**
     * @brief Starts the DHS dispatcher and its streaming cache
     */
    private void start() {
        DHSIterator = -1;
        gcodeStreamCache.start();
    }
    
    /**
     * @brief Attaches an DHS to the dispatcher that will run during G-Code streaming. 
     * @param DHS The specified DHS
     */
    public void attachDHS(GcodeStreamDHS DHS) {
        DHSs.add(DHS);
    }
    
    /**
     * @brief Toggles the G-Code stream to be active or inactive. An inactive stream 
     * will receive completed commands but will not send new commands. 
     * @param stream Specifies whether or not to stream
     */
    private void setGcodeStream(boolean stream) {
        try {
            if(stream) {
                backend.getController().resumeStreaming();
                // TODO: handle communicator based on firmware type (i.e., grblHAL, etc.)
                backend.getController().getCommunicator().sendByteImmediately((byte)'~');
            } else if(!stream && backend.getController().isStreaming()) {
                backend.getController().pauseStreaming();
            }
        } catch(Exception e) {}
    }
 
    /**
     * @brief All DHSs are evaluated by stepping through the list of attached DHSs. 
     * This method tracks the next DHS (starting at index zero in list) and returns the 
     * next DHS index to be triggered for interrupt. If no DHSs are triggered, or all have been, 
     * the returned index is -1. 
     * @return Index of triggered DHS; otherwise returns -1.  
     */
    private int getNextTriggeredDHS() {
        // step through DHSs until one is triggered
        for(int i = DHSIterator+1; i < DHSs.size() && isActive; i++) {
            String cmd = (this.nextCommand != null) ? this.nextCommand.getCommandString() : "";
            if(DHSs.get(i).shouldInterrupt(cmd)) {
                return i; // return triggered DHS
            }
        }
        // no DHS triggered; return nothing 
        return -1;
    }
    
    /** An event triggered when a stream is stopped */
    @Override public void streamCanceled() { stop(); }
    /** The file streaming has completed. */
    @Override public void streamComplete() { stop(); }
    /** An event triggered when a stream is started */
    @Override public void streamStarted() { start(); }
    
    @Override public void streamPaused() {}
    @Override public void streamResumed() {}
    @Override public void receivedAlarm(Alarm alarm) {}
    @Override public void probeCoordinates(Position p) {}
    @Override public void statusStringListener(ControllerStatus status) {}
}
