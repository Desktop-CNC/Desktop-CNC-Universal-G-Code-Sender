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
import com.willwinder.universalgcodesender.listeners.ControllerListener;
import com.willwinder.universalgcodesender.listeners.MessageType;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.types.GcodeCommand;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.Modules;
import org.openide.modules.ModuleInfo;
import java.io.File;
import java.util.Set;

/**
 * Represents an Interrupt Service Routine (ISR) for G-Code Streaming. This will run 
 * when specific conditions are meant while steaming G-Code commands. This serves as a G-Code Stream ISR 
 * strategy interface to the `GcodeStreamISRDispatcher` as a strategy design pattern. 
 * @author matthew-papesh
 */
public abstract class GcodeStreamISR {
    private final BackendAPI backend;
    private File interruptBinary = null;
    private final String NBM_PKG_NAME;
    
    public GcodeStreamISR() {
        // retrieve backend from UGS Platform
        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        ModuleInfo nbm_pkg_info = Modules.getDefault().ownerOf(this.getClass());
        NBM_PKG_NAME = nbm_pkg_info.getCodeNameBase();
    }
    
    /**
     * @brief A hook that runs before running the ISR. 
     */
    public abstract void onBeforeInterrupt();
    /**
     * @brief A hook that runs after having successfully run the ISR.
     */
    public abstract void onAfterInterrupt();
    
    /**
     * @brief Attaches a binary executable to run during the interrupt of the ISR. 
     * On successful completion of running the binary, the interrupt will end. 
     * @note The binary path root directory is at `/src/main/resources`
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
    
    public void runInterruptBinary() {
        if(interruptBinary != null && interruptBinary.exists()) {
            if(!interruptBinary.canExecute()) {
                interruptBinary.setExecutable(true);
            }
            
            int binaryExitCode = -1;
            try {
                ProcessBuilder builder = new ProcessBuilder(interruptBinary.getAbsolutePath(), "--arg1");
                builder.directory(interruptBinary.getParentFile());
                Process interruptProcess = builder.start();
                
                // blocking-process and return code:
                binaryExitCode = interruptProcess.waitFor();
               
            } catch(Exception e) {
                backend.dispatchMessage(MessageType.INFO, "ISR Binary Failed; Return Code: " + String.valueOf(binaryExitCode) + "\n");
                
            }
        }
        
        
    }
    
    
    /**
     * @brief A trigger that returns a conditional that initiates the ISR.
     * The interrupt condition can be implemented by considering the next G-Code command to run. 
     * Should this method return true, the ISR will interrupt before running the specified G-Code command.
     * @param gcodeCmd The specified G-Code command to interrupt
     * @return Whether or not the ISR can initiate. 
     */
    public abstract boolean shouldInterrupt(String gcodeCmd);
}
