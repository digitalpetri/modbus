package com.digitalpetri.modbus;

import java.time.Duration;

public class ModbusTimeoutException extends Exception {

    private final long durationMillis;

    public ModbusTimeoutException(Duration duration) {
        this(duration.toMillis());
    }

    public ModbusTimeoutException(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    @Override
    public String getMessage() {
        return String.format("request timed out after %sms milliseconds.", durationMillis);
    }

}
