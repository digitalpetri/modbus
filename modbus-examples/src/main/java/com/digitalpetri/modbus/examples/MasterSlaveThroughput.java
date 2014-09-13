package com.digitalpetri.modbus.examples;

import java.util.concurrent.ExecutionException;

import com.digitalpetri.modbus.examples.master.MasterExample;
import com.digitalpetri.modbus.examples.slave.SlaveExample;

public class MasterSlaveThroughput {

    public static final int N_MASTERS = 100;
    public static final int N_REQUESTS = 100;

    public static void main(String[] args) throws ExecutionException, InterruptedException {
        new SlaveExample().start();
        new MasterExample(N_MASTERS, N_REQUESTS).start();
    }

}
