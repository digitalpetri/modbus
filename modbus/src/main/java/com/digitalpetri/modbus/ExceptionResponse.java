package com.digitalpetri.modbus;

/**
 * Modbus Exception Response.
 *
 * @param functionCode the {@link FunctionCode} that elicited this response.
 * @param exceptionCode the {@link ExceptionCode} indicated by the outstation.
 */
public record ExceptionResponse(FunctionCode functionCode, ExceptionCode exceptionCode) {}
