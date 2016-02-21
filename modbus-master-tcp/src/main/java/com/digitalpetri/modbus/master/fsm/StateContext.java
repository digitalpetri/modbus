/*
 * Copyright 2016 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.digitalpetri.modbus.master.fsm;

import java.util.concurrent.atomic.AtomicReference;

import com.digitalpetri.modbus.master.ModbusTcpMaster;
import com.digitalpetri.modbus.master.ModbusTcpMasterConfig;

public class StateContext {

    private final AtomicReference<ConnectionState> state = new AtomicReference<>(new Disconnected());

    private final ModbusTcpMaster master;
    private final ModbusTcpMasterConfig config;

    public StateContext(ModbusTcpMaster master, ModbusTcpMasterConfig config) {
        this.master = master;
        this.config = config;
    }

    public ModbusTcpMaster getMaster() {
        return master;
    }

    public ModbusTcpMasterConfig getConfig() {
        return config;
    }

    public ConnectionState getState() {
        return state.get();
    }

    public synchronized ConnectionState handleEvent(ConnectionEvent event) {
        ConnectionState currState = state.get();
        ConnectionState nextState = currState.transition(event, this);

        if (state.compareAndSet(currState, nextState)) {
            return nextState;
        } else {
            return handleEvent(event);
        }
    }

}
