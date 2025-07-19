package com.digitalpetri.modbus;

import com.digitalpetri.modbus.server.ModbusTcpServer;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.digitalpetri.modbus.tcp.server.NettyTcpServerTransport;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusServer {

  private static final Logger LOGGER = LoggerFactory.getLogger(ModbusServer.class);

  public static void main(String[] args) throws ExecutionException, InterruptedException {
    var transport =
        NettyTcpServerTransport.create(
            cfg -> {
              cfg.setBindAddress("0.0.0.0");
              cfg.setPort(502);
            });

    var modbusServices =
        new ReadWriteModbusServices() {

          private final ProcessImage processImage = new ProcessImage();

          @Override
          protected Optional<ProcessImage> getProcessImage(int i) {
            return Optional.of(processImage);
          }
        };

    ModbusTcpServer server = ModbusTcpServer.create(transport, modbusServices);

    server.start();

    LOGGER.info("Listening on 0.0.0.0:502");

    logInetAddresses();

    waitForShutdownHook(server);
  }

  private static void logInetAddresses() {
    try {
      for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
        for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
          if (addr.getHostAddress().indexOf(':') == -1) {
            LOGGER.info(
                "└─ {}: {} ({})", ni.getDisplayName(), addr.getHostAddress(), addr.getHostName());
          }
        }
      }
    } catch (SocketException e) {
      LOGGER.error("Failed to get network interfaces", e);
    }
  }

  private static void waitForShutdownHook(ModbusTcpServer server) throws InterruptedException {
    var shutdownLatch = new CountDownLatch(1);

    var shutdownHook =
        new Thread(
            () -> {
              LOGGER.info("Stopping server...");
              try {
                server.stop();

                LOGGER.info("Server stopped.");
              } catch (ExecutionException | InterruptedException ignored) {
              } finally {
                shutdownLatch.countDown();
              }
            });

    Runtime.getRuntime().addShutdownHook(shutdownHook);

    shutdownLatch.await();
  }
}
