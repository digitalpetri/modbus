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

import com.digitalpetri.modbus.ExceptionCode;
import com.digitalpetri.modbus.requests.MaskWriteRegisterRequest;
import com.digitalpetri.modbus.requests.ModbusRequest;
import com.digitalpetri.modbus.requests.ReadCoilsRequest;
import com.digitalpetri.modbus.requests.ReadDiscreteInputsRequest;
import com.digitalpetri.modbus.requests.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.requests.ReadInputRegistersRequest;
import com.digitalpetri.modbus.requests.WriteMultipleCoilsRequest;
import com.digitalpetri.modbus.requests.WriteMultipleRegistersRequest;
import com.digitalpetri.modbus.requests.WriteSingleCoilRequest;
import com.digitalpetri.modbus.requests.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.responses.MaskWriteRegisterResponse;
import com.digitalpetri.modbus.responses.ModbusResponse;
import com.digitalpetri.modbus.responses.ReadCoilsResponse;
import com.digitalpetri.modbus.responses.ReadDiscreteInputsResponse;
import com.digitalpetri.modbus.responses.ReadHoldingRegistersResponse;
import com.digitalpetri.modbus.responses.ReadInputRegistersResponse;
import com.digitalpetri.modbus.responses.WriteMultipleCoilsResponse;
import com.digitalpetri.modbus.responses.WriteMultipleRegistersResponse;
import com.digitalpetri.modbus.responses.WriteSingleCoilResponse;
import com.digitalpetri.modbus.responses.WriteSingleRegisterResponse;
import io.netty.util.ReferenceCountUtil;

public interface ServiceRequestHandler {

    default void onReadHoldingRegisters(ServiceRequest<ReadHoldingRegistersRequest, ReadHoldingRegistersResponse> service) {
        service.sendException(ExceptionCode.IllegalFunction);
        ReferenceCountUtil.release(service.getRequest());
    }

    default void onReadInputRegisters(ServiceRequest<ReadInputRegistersRequest, ReadInputRegistersResponse> service) {
        service.sendException(ExceptionCode.IllegalFunction);
        ReferenceCountUtil.release(service.getRequest());
    }

    default void onReadCoils(ServiceRequest<ReadCoilsRequest, ReadCoilsResponse> service) {
        service.sendException(ExceptionCode.IllegalFunction);
        ReferenceCountUtil.release(service.getRequest());
    }

    default void onReadDiscreteInputs(ServiceRequest<ReadDiscreteInputsRequest, ReadDiscreteInputsResponse> service) {
        service.sendException(ExceptionCode.IllegalFunction);
        ReferenceCountUtil.release(service.getRequest());
    }

    default void onWriteSingleCoil(ServiceRequest<WriteSingleCoilRequest, WriteSingleCoilResponse> service) {
        service.sendException(ExceptionCode.IllegalFunction);
        ReferenceCountUtil.release(service.getRequest());
    }

    default void onWriteSingleRegister(ServiceRequest<WriteSingleRegisterRequest, WriteSingleRegisterResponse> service) {
        service.sendException(ExceptionCode.IllegalFunction);
        ReferenceCountUtil.release(service.getRequest());
    }

    default void onWriteMultipleCoils(ServiceRequest<WriteMultipleCoilsRequest, WriteMultipleCoilsResponse> service) {
        service.sendException(ExceptionCode.IllegalFunction);
        ReferenceCountUtil.release(service.getRequest());
    }

    default void onWriteMultipleRegisters(ServiceRequest<WriteMultipleRegistersRequest, WriteMultipleRegistersResponse> service) {
        service.sendException(ExceptionCode.IllegalFunction);
        ReferenceCountUtil.release(service.getRequest());
    }

    default void onMaskWriteRegister(ServiceRequest<MaskWriteRegisterRequest, MaskWriteRegisterResponse> service) {
        service.sendException(ExceptionCode.IllegalFunction);
        ReferenceCountUtil.release(service.getRequest());
    }

    public static interface ServiceRequest<Request extends ModbusRequest, Response extends ModbusResponse> {

        /**
         * @return the transaction id associated with this request.
         */
        short getTransactionId();

        /**
         * @return the unit/slave id this request is directed to.
         */
        short getUnitId();

        /**
         * @return the request to service.
         */
        Request getRequest();

        /**
         * Send a normal response.
         *
         * @param response the service response
         */
        void sendResponse(Response response);

        /**
         * Send an exception response.
         *
         * @param exceptionCode the {@link ExceptionCode}
         */
        void sendException(ExceptionCode exceptionCode);

    }

}
