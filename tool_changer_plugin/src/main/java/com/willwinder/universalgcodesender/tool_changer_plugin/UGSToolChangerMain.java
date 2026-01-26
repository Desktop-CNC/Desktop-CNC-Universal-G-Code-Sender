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

// willwinder plugin imports 
import com.willwinder.ugs.nbp.lib.lookup.CentralLookup;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.model.UGSEvent;
import com.willwinder.universalgcodesender.services.MessageService;
import com.willwinder.universalgcodesender.listeners.MessageType;
import com.willwinder.universalgcodesender.listeners.UGSEventListener;
import javax.swing.AbstractAction;
import java.util.logging.Logger;
// 3rd party plugin imports
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
    
    /**
     * @brief Creates a UGSToolChangerMain class instance
     */
    public UGSToolChangerMain() {
        // retrieve backend from UGS Platform and attach plugin as a listener to UGS
        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        backend.addUGSEventListener(this);
    }
    
    /**
     * Given a command and the current state of a program returns a replacement
     * list of commands.
     * @param command Input gcode.
     * @param state State of the gcode parser when the command will run.
     * @return One or more gcode commands to replace the original command with.
     */
    @Override
    public List<String> processCommand(String command, GcodeState state) {
        // write a parser that, given a gcode command, edit it and inject to commands. These edits can reflect tool changer logic
        List<String> post_cmds = new ArrayList<>();
        post_cmds.add(command);
        String s = "PLUGIN: " + command;
        LOG.info(s);
        backend.dispatchMessage(MessageType.INFO, "PLUGIN: " + command);
        return post_cmds;
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
