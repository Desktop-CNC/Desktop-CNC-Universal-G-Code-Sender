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
import com.willwinder.universalgcodesender.listeners.ControllerState;
import com.willwinder.universalgcodesender.listeners.MessageType;
import com.willwinder.universalgcodesender.model.BackendAPI;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.Modules;
import org.openide.modules.ModuleInfo;
import java.io.File;

/**
 * Represents an Interrupt Service Routine (ISR) for G-Code Streaming. This will run 
 * when specific conditions are meant while steaming G-Code commands. This serves as a G-Code Stream ISR 
 * strategy interface to the `GcodeStreamISRDispatcher` as a strategy design pattern. 
 * @author matthew-papesh
 */
public class GcodeStreamISR {
    private final BackendAPI backend;
    private File interruptBinary = null;
    private Process interruptProcess = null;
    private final String ISR_ID;
    private final String NBM_PKG_NAME;
    private final GcodeStreamISRBehavior TRANSITIONS_BEHAVIOR;
    
    public GcodeStreamISR(String ISR_ID, GcodeStreamISRBehavior behavior) {
        // retrieve backend from UGS Platform
        this.backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        this.ISR_ID = ISR_ID; // id of ISR
        ModuleInfo nbm_pkg_info = Modules.getDefault().ownerOf(this.getClass());
        this.NBM_PKG_NAME = nbm_pkg_info.getCodeNameBase();
        this.TRANSITIONS_BEHAVIOR = behavior;
    }
    
    public GcodeStreamISR(GcodeStreamISRBehavior behavior) {
        // retrieve backend from UGS Platform
        this.backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        this.ISR_ID = "no_id"; // id of ISR
        ModuleInfo nbm_pkg_info = Modules.getDefault().ownerOf(this.getClass());
        this.NBM_PKG_NAME = nbm_pkg_info.getCodeNameBase();
        this.TRANSITIONS_BEHAVIOR = behavior;
    }
    
    /**
     * @brief The ISR unique identifier
     * @return The ISR ID
     */
    public String getId() {
        return ISR_ID;
    }
    
    /**
     * @brief A hook that runs before running the ISR. 
     */
    public void onBeforeInterrupt() {
        TRANSITIONS_BEHAVIOR.onBeforeInterrupt();
    }
    /**
     * @brief A hook that runs after having successfully run the ISR.
     * @param successfulInterrupt Whether or not the binary interrupt run successfully. 
     */
    public void onAfterInterrupt(boolean successfulInterrupt) {
        TRANSITIONS_BEHAVIOR.onAfterInterrupt(successfulInterrupt);
    }
    
    /**
     * @brief Attaches a binary executable to run during the interrupt of the ISR. 
     * On successful completion of running the binary, the interrupt will end. 
     * @note The binary path root directory is at `/src/main/release`
     * @note The binary path must be relative to this root; the binary will be packaged as part of the plugin NBM when built. 
     * @param binaryPath The specified binary path
     */
    public void attachInterruptBinary(String binaryPath) {
        interruptBinary = InstalledFileLocator.getDefault().locate(
            binaryPath,
            NBM_PKG_NAME,
            false
        );
    }  
    
    /**
     * @brief 
     */
    public void forceKillInterruptBinary() {
        if (interruptProcess != null && interruptProcess.isAlive()) {
            interruptProcess.destroyForcibly();
        }
    }
    
    /**
     * @brief Executes the attached binary executable. The binary is attached to this ISR by calling the `attachInterruptBinary` method. 
     * @note This method is designed as a blocking method. 
     * @return Wether or not the interrupt binary run successfully; no binary run is still a successful outcome.
     */
    public boolean runInterruptBinary() {
        // run binary if exists/valid
        if(interruptBinary != null && interruptBinary.exists()) {
            if(!interruptBinary.canExecute()) { // make binary executable 
                interruptBinary.setExecutable(true);
            }
            // try to run binary as a blocking program
            int binaryExitCode = -1;
            try {
                // create builder process to run executable binary
                ProcessBuilder builder = new ProcessBuilder(interruptBinary.getAbsolutePath(), "--arg1");
                builder.directory(interruptBinary.getParentFile());
                builder.redirectErrorStream(true); // merge stderr into stdout
                interruptProcess = builder.start();
                           
                // blocking-process that runs binary and gives its return code:
                binaryExitCode = interruptProcess.waitFor(); // waits for binary to complete before continuing here.              
                if(binaryExitCode != 0) {
                    throw new Exception(); // failed to complete binary successfully; throw exception
                }
               
            } catch(Exception e) {
                // send exception warning message 
                String errHeader = String.format("GcodeStreamISR: %s, failed.", getId());
                String errBinaryHeader = (interruptBinary == null || !interruptBinary.exists()) ? "No ISR Binary Found." : "Binary exit code: " + String.valueOf(binaryExitCode);
                String errExceptionHeader = String.format("Found Execption: %s", e.getMessage());
                String exceptionMsg = String.format("Machine HOLD => [ %s %s => %s ]\n", errHeader, errBinaryHeader, errExceptionHeader);
                backend.dispatchMessage(MessageType.ERROR, exceptionMsg);
                // pause machining and set to HOLD state
                if(backend.getControllerState() != ControllerState.HOLD) {
                    try { backend.pauseResume(); }
                    catch(Exception ePauseStreaming) {}
                }
                interruptProcess = null; // reset process
                // failed to run any binary
                return false;
            }
        }
        // succeeded to run any binary
        return true;
    }
    
    /**
     * @brief A trigger that returns a conditional that initiates the ISR.
     * The interrupt condition can be implemented by considering the next G-Code command to run. 
     * Should this method return true, the ISR will interrupt before running the specified G-Code command.
     * @param gcodeCmd The specified G-Code command to interrupt
     * @return Whether or not the ISR can initiate. 
     */
    public boolean shouldInterrupt(String gcodeCmd) {
        return TRANSITIONS_BEHAVIOR.shouldInterrupt(gcodeCmd);
    }
}
