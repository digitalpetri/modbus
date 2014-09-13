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
