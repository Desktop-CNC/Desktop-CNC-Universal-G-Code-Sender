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
import com.willwinder.universalgcodesender.model.BackendAPI;
import com.willwinder.universalgcodesender.utils.GcodeStreamReader;
import java.io.File;
import java.util.logging.Logger;
import com.willwinder.universalgcodesender.listeners.ControllerStatus;
import com.willwinder.universalgcodesender.listeners.ControllerListener;
import com.willwinder.universalgcodesender.model.Alarm;
import com.willwinder.universalgcodesender.model.Position;
import com.willwinder.universalgcodesender.model.UGSEvent;

import com.willwinder.universalgcodesender.tool_changer_plugin.GcodeStreamCache;
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
    
    public GcodeStreamISRDispatcher() {
        // retrieve backend from UGS Platform
        backend = CentralLookup.getDefault().lookup(BackendAPI.class);
        // CREATE CACHE BEFORE ADDING LISTENER! => This ensure the cache updates before this dispatcher on ControllerListener
        gcodeStreamCache = new GcodeStreamCache();
        backend.getController().addListener(this);   
        nextCommand = null;
    }
    
    /**
     * An event triggered when a stream is stopped
     */
    @Override
    public void streamCanceled() {
        nextCommand = null;
    }

    /**
     * The file streaming has completed.
     */
    @Override
    public void streamComplete() {
        nextCommand = null;
    }
    
    /**
     * An event triggered when a stream is started
     */
    @Override
    public void streamStarted() {
        nextCommand = gcodeStreamCache.getNextGcodeCommand();
    }
    
    /**
     * A command in the stream has been skipped.
     */
    @Override
    public void commandSkipped(GcodeCommand command) {
        nextCommand = gcodeStreamCache.getNextGcodeCommand();
    }

    /**
     * A command has successfully been sent to the controller.
     */
    @Override
    public void commandSent(GcodeCommand command) {
        nextCommand = gcodeStreamCache.getNextGcodeCommand();
    }

    @Override public void streamPaused() {}
    @Override public void streamResumed() {}
    @Override public void receivedAlarm(Alarm alarm) {}
    @Override public void commandComplete(GcodeCommand command) {}
    @Override public void probeCoordinates(Position p) {}
    @Override public void statusStringListener(ControllerStatus status) {}
}
