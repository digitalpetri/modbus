package com.digitalpetri.modbus.codec;

import com.digitalpetri.modbus.ModbusPdu;

public class ModbusTcpPayload {

    private final short transactionId;
    private final short unitId;
    private final ModbusPdu modbusPdu;

    public ModbusTcpPayload(short transactionId, short unitId, ModbusPdu modbusPdu) {
        this.transactionId = transactionId;
        this.unitId = unitId;
        this.modbusPdu = modbusPdu;
    }

    public short getTransactionId() {
        return transactionId;
    }

    public short getUnitId() {
        return unitId;
    }

    public ModbusPdu getModbusPdu() {
        return modbusPdu;
    }

}
