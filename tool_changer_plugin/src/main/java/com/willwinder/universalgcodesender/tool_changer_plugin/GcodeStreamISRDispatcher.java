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

/*
    Template Servo C++ Code:
#include <iostream>
#include <lgpio.h>
#include <unistd.h>

int main() {
    int pin = 18;     
    int chip = 4;     // Pi 5 RP1 controller is usually chip 4
    
    // 1. Open the chip
    int handle = lgGpiochipOpen(chip);
    if (handle < 0) {
        std::cerr << "Could not open gpiochip 4. Try: sudo ./pi_main" << std::endl;
        return 1;
    }

    // 2. Claim the line for output (This wakes up the pin)
    if (lgGpioClaimOutput(handle, 0, pin, 0) < 0) {
        std::cerr << "Could not claim BCM 18. It might be in use." << std::endl;
        lgGpiochipClose(handle);
        return 1;
    }

    std::cout << "Success! Moving Servo on BCM 18..." << std::endl;

    // 3. Servo Commands (handle, gpio, pulseWidth, frequency, offset, cycles)
    lgTxServo(handle, pin, 1000, 50, 0, 0); 
    sleep(2);
    
    lgTxServo(handle, pin, 2000, 50, 0, 0); 
    sleep(2);

    // 4. Cleanup
    lgTxServo(handle, pin, 0, 50, 0, 0); 
    lgGpiochipClose(handle);
    
    return 0;
}
*/

import com.willwinder.ugs.nbp.lib.lookup.CentralLookup;
import com.willwinder.universalgcodesender.model.BackendAPI;
import java.util.logging.Logger;
import java.util.List;
import java.util.ArrayList;

import com.willwinder.universalgcodesender.listeners.ControllerStatus;
import com.willwinder.universalgcodesender.listeners.ControllerListener;
import com.willwinder.universalgcodesender.model.Alarm;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.types.GcodeCommand;

/**
 * @brief Represents an Interrupt Service Routine (ISR) that interrupts G-Code streaming on evaluating a 
 * G-Code commands before it is sent and streamed. This dispatches all attached `GcodeStreamISR` ISRs and tests 
 * for interrupts for every G-Code command streamed during a CNC machining process. All ISRs will be run when no
 * G-Code commands are actively running. 
 * 
 * @author matthew-papesh
 */
public class GcodeStreamISRDispatcher implements ControllerListener {
    private static final Logger LOG = Logger.getLogger(UGSToolChangerMain.class.getName());
    private final BackendAPI backend; 
    private final GcodeStreamCache gcodeStreamCache;
    private final List<GcodeStreamISR> ISRs = new ArrayList<GcodeStreamISR>();
    // FSM states 
    private enum DispatcherState { POLL, QUIESCE, INTERRUPT }
    
    // tracking parameters 
    private GcodeCommand nextCommand = null;
    private int ISRDwellCmdId = -1;
    private int ISRIterator = -1;
    private DispatcherState currentState = DispatcherState.POLL;
    
    /**
     * @brief Creates a `GcodeStreamISRDispatcher` instance. 
     */
    public GcodeStreamISRDispatcher() {
        // retrieve backend from UGS Platform
        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        gcodeStreamCache = new GcodeStreamCache();
        backend.getController().addListener(this);        
    }

    /**
     * @brief Polls untested ISRs to test them. Polling occurs on a G-Code command before it is streamed. 
     * An ISR is triggered if it meets conditions to be interrupted. The dispatcher state will be found to quiesce if it is enabled, 
     * otherwise it will be found to interrupt. The dispatcher state will be found to continue to poll should no ISR be triggered. 
     * Finally, the dispatcher only streams G-Code commands in the poll state. 
     * 
     * @note If quiesce is not enabled, the dispatcher will immediately run any triggered ISRs before ensuring there are no active G-Code commands 
     * @param enableQuiesce Specifies if machine should quiesce on ISR getting triggered.
     * @return 
     */
    private DispatcherState pollISRs(boolean enableQuiesce) {
        ISRIterator = getNextTriggeredISR();
        if(ISRIterator != -1) { // check for ISR interrupts 
            if(enableQuiesce) {
                try { // found a triggered ISR; begin quiesce with dwell cmd
                    GcodeCommand dwellCmd = backend.getController().createCommand("G4 P0");
                    ISRDwellCmdId = dwellCmd.getId(); // record and send dwell cmd 
                    backend.sendGcodeCommand(dwellCmd);
                    setGcodeStream(false); // halt streamming 
                } catch(Exception e) {}
                return DispatcherState.QUIESCE;
            }
            setGcodeStream(false); // halt streaming
            return DispatcherState.INTERRUPT;
        } else {
            // continue polling on no interrupts 
            ISRIterator = -1;  
            setGcodeStream(true); // continue streaming 
            return DispatcherState.POLL;
        }
    }
    
    /**
     * @brief Runs the interrupt logic of the current ISR.
     * @note This will pause the CNC machine should the ISR fail on running. 
     */
    private void interruptOnCurrentISR() {
        if(ISRIterator >= 0) { // only interrupt on valid ISR
            try {
                GcodeStreamISR ISR = ISRs.get(ISRIterator);
                // run the interrupt 
                ISR.onBeforeInterrupt();
                boolean success = ISR.runInterruptBinary();
                ISR.onAfterInterrupt(success);
                // handle if interrupt fails 
                if(!success && !backend.isPaused()) {
                    //backend.pauseResume(); 
                }        
            } catch(Exception e) {}
            this.setGcodeStream(true);
        }
    }
    
    /**
     * @brief Runs when a G-Code command has successfully been sent to the controller. 
     * @param command The command that was sent
     */
    @Override 
    public void commandSent(GcodeCommand command) {
        
    }
    
    /**
     * @brief Runs when a G-Code command has been completed by the CNC firmware (i.e., grblHAL). 
     * This method will quiesce the CNC machine and run all triggered ISRs until all have been run. 
     * This method will re-enable G-Code streaming once all ISR interrupts have been complete.
     * @param command 
     */
    @Override 
    public void commandComplete(GcodeCommand command) {
        
        currentState = pollISRs(false); // check for interrupted ISR; get state 
        while(currentState != DispatcherState.POLL) {
           interruptOnCurrentISR(); // run the interrupted ISR
           currentState = pollISRs(false); // check remaining ISRs and state 
            
        }
        this.nextCommand = this.gcodeStreamCache.getNextGcodeCommand();
        this.setGcodeStream(true);
    }
    
    /** 
     * @brief A command in the stream has been skipped. 
     */
    @Override 
    public void commandSkipped(GcodeCommand command) {
        
    } 
    
    /**
     * @brief Stops the ISR dispatcher and its streaming cache. 
     */
    private void stop() {
        nextCommand = null;
        ISRIterator = -1;
        currentState = DispatcherState.POLL;
        gcodeStreamCache.stop();
    }
    
    /**
     * @brief Starts the ISR dispatcher and its streaming cache
     */
    private void start() {
        ISRIterator = -1;
        currentState = DispatcherState.POLL;
        gcodeStreamCache.start();
        // get the first gcode cmd on start (it is the next cmd until it is called by gcode stream)
        nextCommand = gcodeStreamCache.getNextGcodeCommand();
    }
    
    /**
     * @brief Attaches an ISR to the dispatcher that will run during G-Code streaming. 
     * @param isr The specified ISR
     */
    public void attachISR(GcodeStreamISR isr) {
        ISRs.add(isr);
    }
    
    /**
     * @brief Toggles the G-Code stream to be active or inactive. An inactive stream 
     * will receive completed commands but will not send new commands. 
     * @param stream Specifies whether or not to stream
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
     * @brief All ISRs are evaluated by stepping through the list of attached ISRs. 
     * This method tracks the next ISR (starting at index zero in list) and returns the 
     * next ISR index to be triggered for interrupt. If no ISRs are triggered, or all have been, 
     * the returned index is -1. 
     * @return Index of triggered ISR; otherwise returns -1.  
     */
    private int getNextTriggeredISR() {
        // step through ISRs until one is triggered
        for(int i = ISRIterator+1; i < ISRs.size(); i++) { 
            if(ISRs.get(i).shouldInterrupt(nextCommand.getCommandString())) {
                return i; // return triggered ISR
            }
        }
        // no ISR triggered; return nothing 
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
