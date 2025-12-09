/*
 * Copyright (C) 2025 matthew-papesh
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
package com.willwinder.universalgcodesender.console_printer_plugin;

// willwinder plugin imports 
import com.willwinder.ugs.nbp.lib.lookup.CentralLookup;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.listeners.UGSEventListener;
import javax.swing.AbstractAction;
import java.util.logging.Logger;
// 3rd party plugin imports
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
        id = "com.willwinder.universalgcodesender.console_printer_plugin.CustomConsoleAction"
)
@ActionRegistration(
        displayName = "#CTL_CustomConsoleAction"
)
// This puts a menu item under the main "Tools" -> "UGS Plugins" menu
@ActionReference(path = "Menu/Tools/UGS Plugins", position = 300) 
@Messages("CTL_CustomConsoleAction=CustomConsoleActionName")
/**
 * @author Matthew Papesh 
 * @brief Represents a custom plugin command template that prints to the console / terminal running UGS Platform. The plugin is installed on UGS Platform, 
 * and then can be found from the drop-down GUI ribbon under "Tools" under "UGS Plugins". Upon clicking the action for this plug-in fro the drop-down menu, logged text is printed. 
 */
public final class CustomConsoleAction extends AbstractAction implements UGSEventListener, CommandProcessor {
    private static final Logger LOG = Logger.getLogger(CustomConsoleAction.class.getName());
    private final BackendAPI backend;
    
    /**
     * @brief Represents an instance of CustomConsoleAction. 
     */
    public CustomConsoleAction() {
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
        return null;
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
     * Called before a new file is processed to allow the processor to reset any state about the processed file.
     */
    @Override
    public void reset() {}
    
    /**
     * Adds a command processor and applies it to currently loaded program.
     *
     * @param commandProcessor a command processor.
     * @throws Exception
     */
    @Override
    public void actionPerformed(ActionEvent event) {
         // --- THIS PRINTS TO THE UGS CONSOLE/OUTPUT WINDOW ---
        LOG.info("Hello UGS Console! This message uses the UGS internal logging system.");
        // try to append a g-code pre-processor for (i.e. tool changer)
        try {
            // append this plugin instance as it implements CommandProcessor 
            backend.applyCommandProcessor(this);
        } catch(Exception e) {
            // do something if exception thrown
            LOG.warning(e.toString()); // print exception to UGS console as warning
        }
    }
}
