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

/**
 *
 * @author matthew-papesh
 */
public interface GcodeStreamISRBehavior {
     /**
     * @brief A hook that runs before running the ISR. 
     */
    public void onBeforeInterrupt();
    /**
     * @brief A hook that runs after having successfully run the ISR.
     * @param successfulInterrupt Whether or not the binary interrupt run successfully. 
     */
    public void onAfterInterrupt(boolean successfulInterrupt);
    /**
     * @brief A trigger that returns a conditional that initiates the ISR.
     * The interrupt condition can be implemented by considering the next G-Code command to run. 
     * Should this method return true, the ISR will interrupt before running the specified G-Code command.
     * @param gcodeCmd The specified G-Code command to interrupt
     * @return Whether or not the ISR can initiate. 
     */
    public boolean shouldInterrupt(String gcodeCmd);
}
