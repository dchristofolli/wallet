package com.dchristofolli.wallet.controller;

import com.dchristofolli.wallet.dto.CreateWalletRequest;
import com.dchristofolli.wallet.dto.WalletResponse;
import com.dchristofolli.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(path = "wallets")
public class WalletController {
    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public WalletResponse createWallet(@Valid @RequestBody CreateWalletRequest createWalletRequest) {
        return walletService.createWallet(createWalletRequest);
    }

    @GetMapping("{walletId}")
    public WalletResponse getWallet(@PathVariable UUID walletId) {
        return walletService.getWallet(walletId);
    }
}
