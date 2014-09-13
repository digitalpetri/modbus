package com.digitalpetri.modbus.master.fsm;

public enum ConnectionEvent {
    ConnectRequested,
    DisconnectRequested,
    ChannelOpenSuccess,
    ChannelOpenFailure,
    ChannelClosed
}
