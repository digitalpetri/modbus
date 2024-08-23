package modbus;

import com.digitalpetri.modbus.client.ModbusClient;
import com.digitalpetri.modbus.client.ModbusRtuClient;
import com.digitalpetri.modbus.client.NettyRtuClientTransport;
import com.digitalpetri.modbus.server.ModbusRtuServer;
import com.digitalpetri.modbus.server.ModbusServer;
import com.digitalpetri.modbus.server.NettyRtuServerTransport;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class ModbusRtuTcpClientServerIT extends ClientServerIT {

  ModbusRtuClient client;
  ModbusRtuServer server;

  @BeforeEach
  void setup() throws Exception {
    var processImage = new ProcessImage();
    var modbusServices = new ReadWriteModbusServices() {
      @Override
      protected Optional<ProcessImage> getProcessImage(int unitId) {
        return Optional.of(processImage);
      }
    };

    int serverPort = -1;

    for (int i = 50200; i < 65536; i++) {
      try {
        final var port = i;
        var serverTransport = NettyRtuServerTransport.create(cfg -> {
          cfg.bindAddress = "localhost";
          cfg.port = port;
        });

        System.out.println("trying port " + port);
        server = ModbusRtuServer.create(serverTransport, modbusServices);
        server.start();
        serverPort = port;
        break;
      } catch (Exception e) {
        server = null;
      }
    }

    if (server == null) {
      throw new Exception("Failed to start server");
    }

    final var port = serverPort;

    client = ModbusRtuClient.create(
        NettyRtuClientTransport.create(
            cfg -> {
              cfg.hostname = "localhost";
              cfg.port = port;
              cfg.connectPersistent = false;
            }
        )
    );
    client.connect();
  }

  @AfterEach
  void teardown() throws Exception {
    if (client != null) {
      client.disconnect();
    }
    if (server != null) {
      server.stop();
    }
  }

  @Override
  ModbusClient getClient() {
    return client;
  }

  @Override
  ModbusServer getServer() {
    return server;
  }

}
