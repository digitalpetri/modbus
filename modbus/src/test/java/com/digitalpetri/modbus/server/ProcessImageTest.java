package com.digitalpetri.modbus.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class ProcessImageTest {

  @Test
  void coils() {
    var processImage = new ProcessImage();

    processImage.with(
        tx -> {
          tx.readCoils(
              coils -> {
                for (int i = 0; i < 65536; i++) {
                  assertFalse(coils.containsKey(i));
                }
                return null;
              });
          tx.writeCoils(
              coils -> {
                for (int i = 0; i < 65536; i++) {
                  coils.put(i, i % 2 == 0);
                }
              });
          tx.readCoils(
              coils -> {
                for (int i = 0; i < 65536; i++) {
                  assertEquals(i % 2 == 0, coils.get(i));
                }
                return null;
              });
          tx.writeCoils(
              coils -> {
                for (int i = 0; i < 65536; i++) {
                  coils.remove(i);
                }
              });
          tx.readCoils(
              coils -> {
                for (int i = 0; i < 65536; i++) {
                  assertFalse(coils.containsKey(i));
                }
                return null;
              });
        });
  }

  @Test
  void discreteInputs() {
    var processImage = new ProcessImage();

    processImage.with(
        tx -> {
          tx.readDiscreteInputs(
              inputs -> {
                for (int i = 0; i < 65536; i++) {
                  assertFalse(inputs.containsKey(i));
                }
                return null;
              });
          tx.writeDiscreteInputs(
              inputs -> {
                for (int i = 0; i < 65536; i++) {
                  inputs.put(i, i % 2 == 0);
                }
              });
          tx.readDiscreteInputs(
              inputs -> {
                for (int i = 0; i < 65536; i++) {
                  assertEquals(i % 2 == 0, inputs.get(i));
                }
                return null;
              });
          tx.writeDiscreteInputs(
              inputs -> {
                for (int i = 0; i < 65536; i++) {
                  inputs.remove(i);
                }
              });
          tx.readDiscreteInputs(
              inputs -> {
                for (int i = 0; i < 65536; i++) {
                  assertFalse(inputs.containsKey(i));
                }
                return null;
              });
        });
  }

  @Test
  void holdingRegisters() {
    var processImage = new ProcessImage();

    processImage.with(
        tx -> {
          tx.readHoldingRegisters(
              registers -> {
                for (int i = 0; i < 65536; i++) {
                  assertFalse(registers.containsKey(i));
                }
                return null;
              });
          tx.writeHoldingRegisters(
              registers -> {
                for (int i = 0; i < 65536; i++) {
                  var bs = new byte[2];
                  bs[0] = (byte) ((i >> 8) & 0xFF);
                  bs[1] = (byte) (i & 0xFF);
                  registers.put(i, bs);
                }
              });
          tx.readHoldingRegisters(
              registers -> {
                for (int i = 0; i < 65536; i++) {
                  byte[] bs = registers.get(i);
                  int value = (bs[0] & 0xFF) << 8 | bs[1] & 0xFF;
                  assertEquals(i, value);
                }
                return null;
              });
          tx.writeHoldingRegisters(
              registers -> {
                for (int i = 0; i < 65536; i++) {
                  registers.remove(i);
                }
              });
          tx.readHoldingRegisters(
              registers -> {
                for (int i = 0; i < 65536; i++) {
                  assertFalse(registers.containsKey(i));
                }
                return null;
              });
        });
  }

  @Test
  void inputRegisters() {
    var processImage = new ProcessImage();

    processImage.with(
        tx -> {
          tx.readInputRegisters(
              registers -> {
                for (int i = 0; i < 65536; i++) {
                  assertFalse(registers.containsKey(i));
                }
                return null;
              });
          tx.writeInputRegisters(
              registers -> {
                for (int i = 0; i < 65536; i++) {
                  var bs = new byte[2];
                  bs[0] = (byte) ((i >> 8) & 0xFF);
                  bs[1] = (byte) (i & 0xFF);
                  registers.put(i, bs);
                }
              });
          tx.readInputRegisters(
              registers -> {
                for (int i = 0; i < 65536; i++) {
                  byte[] bs = registers.get(i);
                  int value = (bs[0] & 0xFF) << 8 | bs[1] & 0xFF;
                  assertEquals(i, value);
                }
                return null;
              });
          tx.writeInputRegisters(
              registers -> {
                for (int i = 0; i < 65536; i++) {
                  registers.remove(i);
                }
              });
          tx.readInputRegisters(
              registers -> {
                for (int i = 0; i < 65536; i++) {
                  assertFalse(registers.containsKey(i));
                }
                return null;
              });
        });
  }
}
