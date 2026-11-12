package com.dchristofolli.wallet.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateWalletRequest(@NotBlank @Size(min = 3, max = 100) String ownerName,
                                  @NotBlank @Size(min = 5, max = 255) @Email String ownerEmail) {
}
