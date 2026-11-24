package com.dchristofolli.wallet.dto;

import org.springframework.format.annotation.NumberFormat;

import java.time.LocalDateTime;
import java.util.UUID;

public record WalletResponse(UUID id,
                             String ownerName,
                             String ownerEmail,
                             @NumberFormat(style = NumberFormat.Style.CURRENCY, pattern = "#,##0.00")
                             String balance,
                             LocalDateTime createdAt,
                             LocalDateTime updatedAt) {
}
