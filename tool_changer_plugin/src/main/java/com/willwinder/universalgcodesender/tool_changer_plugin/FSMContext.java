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

import java.util.HashMap;
import java.util.Map;

/**
 * @brief Represents the context to a `Finite State Machine` (FSM) instance. 
 * @author matthew-papesh
 */
public class FSMContext {
    // the context data 
    private Map<String, Object> CONTEXT = new HashMap<>();
    
    /**
     * @brief Pushes a data value of type `T` onto the context with `key` as the identifier. 
     * @param <T> The specified value type 
     * @param key The specified String identifier of the value 
     * @param value The specified value 
     */
    public <T> void put(String key, T value) {
        CONTEXT.put(key, value);
    }

    /**
     * @brief Peeks the data of type `T` on the context for the specified key. 
     * @param <T> The specified peeked data type 
     * @param key The specified identifier for the data
     * @param type The specified peeked data class type 
     * @return The generic data peeked
     */
    public <T> T get(String key, Class<T> type) {
        return type.cast(CONTEXT.get(key));
    }
    
    /**
     * @brief Swaps the context data as a deep swap by pointer references. 
     * @param other 
     */
    public void swap(FSMContext other) {
        Map<String, Object> temp = this.CONTEXT;
        this.CONTEXT = other.CONTEXT;
        other.CONTEXT = temp;
    }

    /**
     * @brief Clears all data from the context. 
     */
    public void clear() {
        CONTEXT.clear();
    }
    
    /**
     * @brief Determines if the context is empty. 
     * @return Whether or not the context is empty. 
     */
    public boolean empty() {
        return CONTEXT.isEmpty();
    }
}
