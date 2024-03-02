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

package com.digitalpetri.modbus.slave;

import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.codahale.metrics.Counter;
import com.digitalpetri.modbus.ExceptionCode;
import com.digitalpetri.modbus.codec.ModbusRequestDecoder;
import com.digitalpetri.modbus.codec.ModbusResponseEncoder;
import com.digitalpetri.modbus.codec.ModbusTcpCodec;
import com.digitalpetri.modbus.codec.ModbusTcpPayload;
import com.digitalpetri.modbus.requests.ModbusRequest;
import com.digitalpetri.modbus.responses.ExceptionResponse;
import com.digitalpetri.modbus.responses.ModbusResponse;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModbusTcpSlave {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final AtomicReference<ServiceRequestHandler> requestHandler =
        new AtomicReference<>(new ServiceRequestHandler() {});

    private final Map<SocketAddress, Channel> serverChannels = new ConcurrentHashMap<>();

    private final Counter channelCounter = new Counter();
    private final ModbusTcpSlaveConfig config;

    public ModbusTcpSlave(ModbusTcpSlaveConfig config) {
        this.config = config;
    }

    public CompletableFuture<ModbusTcpSlave> bind(String host, int port) {
        CompletableFuture<ModbusTcpSlave> bindFuture = new CompletableFuture<>();

        ServerBootstrap bootstrap = new ServerBootstrap();

        ChannelInitializer<SocketChannel> initializer = new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel channel) {
                channelCounter.inc();
                logger.info("channel initialized: {}", channel);

                channel.pipeline().addLast(new LoggingHandler(LogLevel.TRACE));
                channel.pipeline().addLast(new ModbusTcpCodec(new ModbusResponseEncoder(), new ModbusRequestDecoder()));
                channel.pipeline().addLast(newModbusTcpSlaveHandler());

                channel.closeFuture().addListener(future -> channelCounter.dec());
            }
        };

        config.getBootstrapConsumer().accept(bootstrap);

        bootstrap.group(config.getEventLoop())
            .channel(NioServerSocketChannel.class)
            .handler(new LoggingHandler(LogLevel.DEBUG))
            .childHandler(initializer)
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);

        bootstrap.bind(host, port).addListener((ChannelFuture future) -> {
            if (future.isSuccess()) {
                Channel channel = future.channel();
                serverChannels.put(channel.localAddress(), channel);
                bindFuture.complete(ModbusTcpSlave.this);
            } else {
                bindFuture.completeExceptionally(future.cause());
            }
        });

        return bindFuture;
    }

    public void setRequestHandler(ServiceRequestHandler requestHandler) {
        this.requestHandler.set(requestHandler);
    }

    public void shutdown() {
        serverChannels.values().forEach(Channel::close);
        serverChannels.clear();
    }

    private void onChannelRead(ChannelHandlerContext ctx, ModbusTcpPayload payload) {
        ServiceRequestHandler handler = requestHandler.get();
        if (handler == null) return;

        switch (payload.getModbusPdu().getFunctionCode()) {
            case ReadCoils:
                handler.onReadCoils(ModbusTcpServiceRequest.of(payload, ctx.channel()));
                break;

            case ReadDiscreteInputs:
                handler.onReadDiscreteInputs(ModbusTcpServiceRequest.of(payload, ctx.channel()));
                break;

            case ReadHoldingRegisters:
                handler.onReadHoldingRegisters(ModbusTcpServiceRequest.of(payload, ctx.channel()));
                break;

            case ReadInputRegisters:
                handler.onReadInputRegisters(ModbusTcpServiceRequest.of(payload, ctx.channel()));
                break;

            case WriteSingleCoil:
                handler.onWriteSingleCoil(ModbusTcpServiceRequest.of(payload, ctx.channel()));
                break;

            case WriteSingleRegister:
                handler.onWriteSingleRegister(ModbusTcpServiceRequest.of(payload, ctx.channel()));
                break;

            case WriteMultipleCoils:
                handler.onWriteMultipleCoils(ModbusTcpServiceRequest.of(payload, ctx.channel()));
                break;

            case WriteMultipleRegisters:
                handler.onWriteMultipleRegisters(ModbusTcpServiceRequest.of(payload, ctx.channel()));
                break;

            case MaskWriteRegister:
                handler.onMaskWriteRegister(ModbusTcpServiceRequest.of(payload, ctx.channel()));
                break;

            case ReadWriteMultipleRegisters:
                handler.onReadWriteMultipleRegisters(ModbusTcpServiceRequest.of(payload, ctx.channel()));
                break;

            default:
                /* Function code not currently supported */
                ExceptionResponse response = new ExceptionResponse(
                    payload.getModbusPdu().getFunctionCode(),
                    ExceptionCode.IllegalFunction);

                ctx.writeAndFlush(new ModbusTcpPayload(payload.getTransactionId(), payload.getUnitId(), response));
                break;
        }
    }

    protected void onChannelInactive(ChannelHandlerContext ctx) {
        logger.debug("Master/client channel closed: {}", ctx.channel());
    }

    protected void onExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception caught on channel: {}", ctx.channel(), cause);
        ctx.close();
    }

    protected ModbusTcpSlaveHandler newModbusTcpSlaveHandler() {
        return new ModbusTcpSlaveHandler(this);
    }

    protected static class ModbusTcpSlaveHandler extends SimpleChannelInboundHandler<ModbusTcpPayload> {

        private final ModbusTcpSlave slave;

        protected ModbusTcpSlaveHandler(ModbusTcpSlave slave) {
            this.slave = slave;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ModbusTcpPayload msg) {
            slave.onChannelRead(ctx, msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            slave.onChannelInactive(ctx);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            slave.onExceptionCaught(ctx, cause);
        }

    }

    private static class ModbusTcpServiceRequest<Request extends ModbusRequest, Response extends ModbusResponse>
        implements ServiceRequestHandler.ServiceRequest<Request, Response> {

        private final short transactionId;
        private final short unitId;
        private final Request request;
        private final Channel channel;

        private ModbusTcpServiceRequest(short transactionId, short unitId, Request request, Channel channel) {
            this.transactionId = transactionId;
            this.unitId = unitId;
            this.request = request;
            this.channel = channel;
        }

        @Override
        public short getTransactionId() {
            return transactionId;
        }

        @Override
        public short getUnitId() {
            return unitId;
        }

        @Override
        public Request getRequest() {
            return request;
        }

        @Override
        public Channel getChannel() {
            return channel;
        }

        @Override
        public void sendResponse(Response response) {
            channel.writeAndFlush(new ModbusTcpPayload(transactionId, unitId, response));
        }

        @Override
        public void sendException(ExceptionCode exceptionCode) {
            ExceptionResponse response = new ExceptionResponse(request.getFunctionCode(), exceptionCode);

            channel.writeAndFlush(new ModbusTcpPayload(transactionId, unitId, response));
        }

        @SuppressWarnings("unchecked")
        public static <Request extends ModbusRequest, Response extends ModbusResponse>
        ModbusTcpServiceRequest<Request, Response> of(ModbusTcpPayload payload, Channel channel) {

            return new ModbusTcpServiceRequest<>(
                payload.getTransactionId(),
                payload.getUnitId(),
                (Request) payload.getModbusPdu(),
                channel
            );
        }

    }

}

