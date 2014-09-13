package com.digitalpetri.modbus.examples.master;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.digitalpetri.modbus.codec.Modbus;
import com.digitalpetri.modbus.master.ModbusTcpMaster;
import com.digitalpetri.modbus.master.ModbusTcpMasterConfig;
import com.digitalpetri.modbus.requests.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.responses.ReadHoldingRegistersResponse;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MasterExample {

    private static final int N_MASTERS = 100;

    private static final int N_REQUESTS = 100;

    public static void main(String[] args) throws InterruptedException {
        new MasterExample().start();
    }

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final List<ModbusTcpMaster> masters = new CopyOnWriteArrayList<>();
    private volatile boolean started = false;

    public void start() {
        started = true;

        ModbusTcpMasterConfig config = new ModbusTcpMasterConfig.Builder("localhost")
                .setPort(50200)
                .build();

        new Thread(() -> {
            while (started) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                double mean = 0.0;
                double oneMinute = 0.0;

                for (ModbusTcpMaster master : masters) {
                    mean += master.getResponseTimer().getMeanRate();
                    oneMinute += master.getResponseTimer().getOneMinuteRate();
                }

                logger.info("Mean rate: {} 1m rate: {}", mean, oneMinute);
            }
        }).start();

        for (int i = 0; i < N_MASTERS; i++) {
            ModbusTcpMaster master = new ModbusTcpMaster(config);
            masters.add(master);

            for (int j = 0; j < N_REQUESTS; j++) {
                sendAndReceive(master);
            }
        }
    }

    private void sendAndReceive(ModbusTcpMaster master) {
        if (!started) return;

        CompletableFuture<ReadHoldingRegistersResponse> future =
                master.sendRequest(new ReadHoldingRegistersRequest(0, 10), 0);

        future.whenCompleteAsync((response, ex) -> {
            if (response != null) {
                ReferenceCountUtil.release(response);
            } else {
                logger.error("Error: {}", ex.getMessage(), ex);
            }
            scheduler.schedule(() -> sendAndReceive(master), 1, TimeUnit.SECONDS);
        }, Modbus.sharedExecutor());
    }

    public void stop() {
        started = false;
        masters.forEach(ModbusTcpMaster::disconnect);
        masters.clear();
    }

}
