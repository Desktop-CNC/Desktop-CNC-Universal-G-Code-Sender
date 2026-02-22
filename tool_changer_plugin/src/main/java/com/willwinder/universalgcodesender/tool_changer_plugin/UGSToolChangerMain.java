/*
 * Copyright (C) 2026 cncteachinglab
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

import org.openide.modules.InstalledFileLocator;
import java.io.File;
// willwinder plugin imports 
import com.willwinder.ugs.nbp.lib.lookup.CentralLookup;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.model.UGSEvent;
import com.willwinder.universalgcodesender.model.UnitUtils;
import com.willwinder.universalgcodesender.model.UnitUtils.*;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.listeners.MessageType;
import com.willwinder.universalgcodesender.listeners.UGSEventListener;
import com.willwinder.universalgcodesender.listeners.ControllerState;

import javax.swing.AbstractAction;
import java.util.logging.Logger;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionID;
import org.openide.awt.ActionRegistration;
import org.openide.util.NbBundle.Messages;

// g-code pre-processing imports
import com.willwinder.universalgcodesender.gcode.processors.CommandProcessor;
import com.willwinder.universalgcodesender.gcode.GcodeState;

@ActionID(
        category = "Tools",
        id = "com.willwinder.universalgcodesender.tool_changer_plugin.UGSToolChangerMain"
)
@ActionRegistration(
        displayName = "#CTL_UGSToolChanger"
)
// This puts a menu item under the main "Tools" -> "UGS Plugins" menu
@ActionReference(path = "Menu/Tools/UGS Plugins", position = 300) 
@Messages("CTL_UGSToolChanger=Toggle Tool Changer")
/**
 * @author Matthew Papesh
 * @brief <TODO>
 */
public final class UGSToolChangerMain extends AbstractAction implements UGSEventListener, CommandProcessor {
    private static final Logger LOG = Logger.getLogger(UGSToolChangerMain.class.getName());
    private final BackendAPI backend;
    private boolean is_active = false; // flag for plugin activity
    
    private ControllerState ctrlStatus = ControllerState.IDLE;
    private boolean is_initialized = false;
    // units the processor are defined by 
    private Units ATCUnits = Units.INCH;
    // units currently read by the processor
    private Units currentUnits = null;

    // pre ATC-op machine states 
    private GcodeState preATCMachineState = null;
    private int preATCToolId = 1;

    // hard-coded absolution ATC tool bit positions in INCHES
    final private Position ATC_POS_1 = new Position(0, 0, 0, ATCUnits);
    final private Position ATC_POS_2 = new Position(0, 0, 0, ATCUnits);
    final private Position ATC_POS_3 = new Position(0, 0, 0, ATCUnits);
    // tavel height for spindle for ATC in INCHES
    final private double TRAVEL_Z = -2;
    
    /**
     * @brief ATC travel z height in current units.
     * @return The set height to rapid-move spindle for auto-tool-change.
     */
    private double getTravelZ() {
        return TRAVEL_Z * UnitUtils.scaleUnits(ATCUnits, currentUnits);
    }
    /**
     * @brief ATC tool bit 1 position in current units. 
     * @return The ATC tool bit 1 position.
     */
    private Position getATCPos1() {
        Position pos = new Position(
            ATC_POS_1.x * UnitUtils.scaleUnits(ATCUnits, currentUnits), 
            ATC_POS_1.y * UnitUtils.scaleUnits(ATCUnits, currentUnits), 
            ATC_POS_1.z * UnitUtils.scaleUnits(ATCUnits, currentUnits), 
            currentUnits);
        return pos;
    }
    /**
     * @brief ATC tool bit 2 position in current units. 
     * @return The ATC tool bit 1 position.
     */
    private Position getATCPos2() {
        Position pos = new Position(
            ATC_POS_2.x * UnitUtils.scaleUnits(ATCUnits, currentUnits), 
            ATC_POS_2.y * UnitUtils.scaleUnits(ATCUnits, currentUnits), 
            ATC_POS_2.z * UnitUtils.scaleUnits(ATCUnits, currentUnits), 
            currentUnits);
        return pos;
    }
    /**
     * @brief ATC tool bit 3 position in current units. 
     * @return The ATC tool bit 1 position.
     */
    private Position getATCPos3() {
        Position pos = new Position(
            ATC_POS_3.x * UnitUtils.scaleUnits(ATCUnits, currentUnits), 
            ATC_POS_3.y * UnitUtils.scaleUnits(ATCUnits, currentUnits), 
            ATC_POS_3.z * UnitUtils.scaleUnits(ATCUnits, currentUnits), 
            currentUnits);
        return pos;
    }
    /**
     * @brief 
     * @param toolId
     * @return
     */
    private Position getATCPos(int toolId) {
        switch (toolId) {
            case 1: return getATCPos1();
            case 2: return getATCPos2();
            case 3: return getATCPos3();
            default: return null;
        }
    }
    
    private void runExec() {
        // executable for native systems (i.e. servo contorl for ATC)
        File exec = InstalledFileLocator.getDefault().locate(
            "bin/pi_main",
            "com.willwinder.universalgcodesender.tool_changer_plugin",
            false
        );
        
        if(exec != null) {
            backend.dispatchMessage(MessageType.INFO,"exec not null\n");
        } else {
            backend.dispatchMessage(MessageType.INFO,"exec==null\n");
        }
        if(exec != null && exec.exists()) {
            backend.dispatchMessage(MessageType.INFO,"exec exists\n");
        } else {
            backend.dispatchMessage(MessageType.INFO,"exec does not exist\n");
        }
        if(exec != null && exec.exists() && exec.canExecute()) {
            backend.dispatchMessage(MessageType.INFO,"exec can run +X\n");
        } else if (exec != null && exec.exists() && !exec.canExecute()) {
            backend.dispatchMessage(MessageType.INFO,"exec cannot run -X\ntrying to fix: ");
            exec.setExecutable(true);
            if (exec.canExecute()) {
                backend.dispatchMessage(MessageType.INFO, "Fixed!\n");
            } else {
                backend.dispatchMessage(MessageType.INFO, "Failed to fix...\n");
            }
        }
        
        if(exec != null && exec.exists()) {
            try {
                ProcessBuilder builder = new ProcessBuilder(exec.getAbsolutePath(), "--arg1");
                builder.directory(exec.getParentFile());
                Process process = builder.start();
                
                // blocking-process and return code:
                int exitCode = process.waitFor();
                backend.dispatchMessage(MessageType.INFO, "Return Code: " + String.valueOf(exitCode) + "\n");
            } catch(Exception e) {
                LOG.warning(e.toString());
            }
        } else {
            backend.dispatchMessage(MessageType.INFO, "No Exec Found!\n");
        }
    }
    
    /**
     * @brief Creates a UGSToolChangerMain class instance
     */
    public UGSToolChangerMain() {
        // retrieve backend from UGS Platform and attach plugin as a listener to UGS
        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        backend.addUGSEventListener(this);
    }
    
    /**
     * @brief Handles generating processed G-Code commands for a auto-tool-change 
     * to a specified tool. 
     * @param toolId The specified new tool by ID 
     * @return
     */
    private List<String> handleAutoToolChange(int toolId) {
        List<String> result = new ArrayList<String>();
        
        // positions of the tool slots on the tool changer in the mill workspace
        Position prevATCPos = getATCPos(preATCToolId);
        Position newATCPos = getATCPos(toolId);
        Position resumePos = new Position(preATCMachineState.currentPoint);

        // set codes for gcode config
        result.add("G90");
        result.add("G17");
        result.add("G94");

        // spindle travels to old ATC tool's slot
        result.add(String.format("G0 X%.3f Y%.3f Z%.3f", resumePos.x, resumePos.y, getTravelZ()));
        result.add(String.format("G0 X%.3f Y%.3f Z%.3f", prevATCPos.x, prevATCPos.y, getTravelZ()));
        // spindle plunges on old ATC tool's slot
        result.add("M4 S3500");
        result.add("G4 P1");
        result.add(String.format("G1 Z%.3f F200", prevATCPos.z));
        result.add(String.format("G0 Z%.3f", getTravelZ()));

        // spindle speed zero and dwell
        result.add("M4 S0");
        result.add("G4 P1");

        // spindle travels to new ATC tool's slot 
        result.add(String.format("G0 X%.3f Y%.3f Z%.3f", newATCPos.x, newATCPos.y, getTravelZ()));
        // spindle plunges on new ATC tool's slot 
        result.add("M3 S3500");
        result.add("G4 P1");
        result.add(String.format("G1 Z%.3f F200", newATCPos.z));
        result.add(String.format("G0 Z%.3f", getTravelZ()));

        // spindle speed zero and dwell
        result.add("M5 S0");
        result.add("G4 P1");

        // spindle travels to resume position
        result.add(String.format("G0 X%.3f Y%.3f Z%.3f", resumePos.x, resumePos.y, getTravelZ()));
        result.add(preATCMachineState.toAccessoriesCode()); // feeds and speeds
        result.add(String.format("G0 X%.3f Y%.3f Z%.3f", resumePos.x, resumePos.y, resumePos.z));
        // restore machine config codes
        result.add(preATCMachineState.machineStateCode()); // config codes 
        // return results
        return result;
    }
    
    @Override
    public List<String> processCommand(String command, GcodeState state) {
        // save all machine states 
        preATCMachineState = state.copy();
        System.out.println(preATCMachineState.currentPoint.getUnits());
        currentUnits = preATCMachineState.currentPoint.getUnits();
        // processed commands from incoming `command` args
        List<String> procCmds = new ArrayList<String>();
        List<String> autoToolChangeCmds = new ArrayList<String>();

        String preATCTokens = ""; // record unchanged tokens before/after ATC
        String postATCTokens = "";
        boolean foundTokenT = false;

        // step through raw command tokens
        String[] tokens = command.split("(?=[A-Z])");
        for(String token : tokens) {
            if(!foundTokenT && token.startsWith("T")) {
                try {
                    // run tool-change upon identifying its CAM command
                    int newToolID =  Integer.parseInt(token.replace("T", ""));
                    autoToolChangeCmds = handleAutoToolChange(newToolID);
                    foundTokenT = true;
                } catch(NumberFormatException e) {
                    System.out.println(e.toString());
                }
            } else if(!foundTokenT && !token.startsWith("T")) {
                preATCTokens = preATCTokens + token;
            } else if(foundTokenT) {
                postATCTokens = postATCTokens + token;
            }
        }

        // add processed commands 
        procCmds.add(preATCTokens);
        procCmds.addAll(autoToolChangeCmds);
        procCmds.add(postATCTokens);
        // return processed commands
        return procCmds;
    }
    
    /**
     * Returns information about the current command and its configuration.
     * @return 
     */
    @Override 
    public String getHelp() {
        return "This is the help string";
    }
     
    /**
     * @brief Called before a new file is processed to allow the processor to reset any state about the processed file.
     */
    @Override
    public void reset() {}
    
    /**
     * @brief Adds a command processor and applies it to currently loaded program.
     * @param commandProcessor a command processor.
     * @throws Exception
     */
    @Override
    public void actionPerformed(ActionEvent event) {
        is_active = !is_active; // toggle plugin activity upon initiatiing from performing action
        // try to append a g-code pre-processor for (i.e. tool changer)
        try {
            // toggle adding this plugin to G-Code pre-processor pipeline 
            String status_msg = "";
            if(is_active) {
                status_msg = "*** UGS Tool Changer Plugin Enabled!\n";
                backend.applyCommandProcessor(this);
                runExec();
            } else {
                status_msg = "*** UGS Tool Changer Plugin Disabled!\n";
                backend.removeCommandProcessor(this);
            }
            // successful plugin toggle; send notifying event to UGS
            backend.dispatchMessage(MessageType.INFO, status_msg);
        } catch(Exception e) {
            // do something if exception thrown
            LOG.warning(e.toString()); // print exception to UGS console as warning
        }
    }
    
    @Override
    public void UGSEvent(UGSEvent event) {
        LOG.info(event.toString());
    }
}
