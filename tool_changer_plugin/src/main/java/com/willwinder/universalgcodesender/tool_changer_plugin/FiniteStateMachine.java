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
import java.util.EnumMap;

/**
 * @brief Represents a finite state machine where states are functions 
 * and edges are defined by enumeration. 
 * @author matthew-papesh
 */
public class FiniteStateMachine<S extends Enum<S>> {
    private FSMContext CTX_IN = new FSMContext(); // used for input variable args across states
    private FSMContext CTX_OUT = new FSMContext(); // used for output variable args across states
    private final Map<S, StateHandler<S>> FSM; // fsm data 
    private S currentState = null; // the current state as enumerator 

    @FunctionalInterface
    protected interface StateHandler<S> {
        S handle(FSMContext cxtIn, FSMContext cxtOut);
    }
    
    /**
     * @brief Creates a `FiniteStateMachine` instance. 
     * @param stateType The specified state enumeration for the system
     * @param initialState The specified initial state 
     */
    public FiniteStateMachine(Class<S> stateType, S initialState) {
        FSM = new EnumMap<>(stateType);
        currentState = initialState;
    }

    /**
     * @brief Adds a state to the FSM. 
     * @param state The specified state status that points to the specified state 
     * @param handler The specified state handler function 
     */
    protected void addState(S state, StateHandler<S> handler) {
        FSM.put(state, handler);
    }

    /**
     * @brief The current state status of the FSM. 
     * @return The current state. 
     */
    protected S getState() {
        return currentState;
    }

    protected <T> void inputToState(String key, T value) {
        CTX_IN.put(key, value);
    }
    
    /**
     * @brief Runs the FSM when iteratively called. 
     */
    protected void runStates() {
        StateHandler<S> handler = FSM.get(currentState);
        if(handler != null) {
            currentState = handler.handle(CTX_IN, CTX_OUT);
            CTX_IN.swap(CTX_OUT); // swap IO contexts 
            CTX_OUT.clear(); // clear output for next state   
        }
    }
}