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
// plug-in package name
package com.willwinder.universalgcodesender.test_plugin;
// UGS class instance look-up
import com.willwinder.ugs.nbp.lib.Mode;
import com.willwinder.ugs.nbp.lib.lookup.CentralLookup;
import com.willwinder.ugs.nbp.lib.services.LocalizingService;
import org.openide.util.Lookup;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;

// UGS access / event listening
import com.willwinder.universalgcodesender.listeners.UGSEventListener;
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.listeners.MessageType;
import com.willwinder.universalgcodesender.model.UGSEvent;

// event helper packages
//import com.willwinder.universalgcodesender.model.events.ControllerStateEvent;
//import com.willwinder.universalgcodesender.model.events.SettingChangedEvent;
// helper packages 
//import com.willwinder.universalgcodesender.utils.Settings;
//import com.willwinder.universalgcodesender.utils.SwingHelpers;

import org.openide.windows.TopComponent; // plugin entry point
import java.util.logging.Level;
import java.util.logging.Logger;

@TopComponent.Description(
        preferredID = "TestPluginTopComponent"
)
@TopComponent.Registration(
        mode = Mode.OUTPUT,  // or another valid mode
        openAtStartup = true
)

public final class TestPluginTopComponent extends TopComponent implements UGSEventListener {
    // plugin Top Component constants 
    public static final String WINDOW_PATH = LocalizingService.MENU_WINDOW_PLUGIN;
    public static final String CATEGORY = LocalizingService.CATEGORY_WINDOW;
    // another constant; formatted ID with the format: <package name><TopComponent Class Name>
    public static final String ACTION_ID = "com.willwinder.universalgcodesender.test_plugin.TestPluginTopComponent";
    // plugin UGS interface  
    private BackendAPI backend;
    private boolean initialized = false; 
    private boolean initialized_backend = false; 
    private static final Logger LOGGER = Logger.getLogger(TestPluginTopComponent.class.getName());
    
    public TestPluginTopComponent() {
        //System.out.println("INITIALIZED");
    }
    
    @Override 
    protected void componentClosed() {
       
    }
    
    @Override 
    protected void componentOpened() {
        LOGGER.log(Level.INFO, "OPENED");
        // initialize the plugin if necessary
        if(!initialized) {
            initialized = true;
            // retrieve supplier of found BackendAPI instances from NetBeans 
            Lookup.Result<BackendAPI> dynamic_result = CentralLookup.getDefault().lookupResult(BackendAPI.class);
            // implement listener to handle dynamic changes in set of BackendAPI instances 
            dynamic_result.addLookupListener(new LookupListener() {
                @Override // implement handler for updates to found BackendAPI instances 
                public void resultChanged(LookupEvent ev) {
                    // upon finding a new Backend API, 
                    // step through all found BackendAPI's (should be only one) 
                    for(BackendAPI b : dynamic_result.allInstances()) {
                        // add plugin to BackendAPI, define backedn reference and coomplete backend initialization
                        b.addUGSEventListener(TestPluginTopComponent.this);
                        backend = b;
                        initialized_backend = true;
                    }
                }
            });
            
            // check for pre-existing BackendAPI isntances (should be only one or zero)
            for(BackendAPI b : dynamic_result.allInstances()) {
                if(!initialized_backend) { 
                    // add plugin to backend while defining it if not already found/initialized
                    b.addUGSEventListener(this);
                    backend = b;
                    initialized_backend = true;
                }
            }
        }
    }
    
    @Override 
    public void UGSEvent(UGSEvent event) {
        if(backend == null)
            return; // ignore handling without backend
        // handle events 
        backend.dispatchMessage(MessageType.INFO, "UGS EVENT: " + event.toString());
    }
}