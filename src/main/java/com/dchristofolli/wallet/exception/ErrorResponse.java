package com.dchristofolli.wallet.exception;

public record ErrorResponse(Integer status, String error, String message, String timestamp) {
}
