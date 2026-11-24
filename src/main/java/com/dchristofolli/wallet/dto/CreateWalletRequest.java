package com.dchristofolli.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

public record CreateWalletRequest(@NotBlank @Size(min = 3, max = 100)
                                  String ownerName,

                                  @NotBlank @Size(min = 5, max = 255)
                                  @Email
                                  String ownerEmail,

                                  @DecimalMin("1")
                                  @DefaultValue("0")
                                  BigDecimal initialBalance) {
}
