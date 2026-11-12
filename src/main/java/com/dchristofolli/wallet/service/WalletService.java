package com.dchristofolli.wallet.service;

import com.dchristofolli.wallet.dto.CreateWalletRequest;
import com.dchristofolli.wallet.dto.WalletResponse;
import com.dchristofolli.wallet.entity.Wallet;
import com.dchristofolli.wallet.exception.DuplicateEntityException;
import com.dchristofolli.wallet.exception.ResourceNotFoundException;
import com.dchristofolli.wallet.repository.WalletRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class WalletService {
    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    public WalletResponse createWallet(CreateWalletRequest createWalletRequest) {
        var wallet = new Wallet();
        wallet.setOwnerName(createWalletRequest.ownerName());
        wallet.setOwnerEmail(createWalletRequest.ownerEmail());
        try {
            return mapToResponse(walletRepository.save(wallet));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateEntityException(e.getMessage());
        }
    }

    public WalletResponse getWallet(UUID id) {
        return walletRepository.findById(id)
                .map(WalletService::mapToResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Wallet", id.toString()));
    }

    private static @NonNull WalletResponse mapToResponse(Wallet saved) {
        return new WalletResponse(saved.getId(), saved.getOwnerName(), saved.getOwnerEmail(), saved.getBalance(), saved.getCreatedAt(), saved.getUpdatedAt());
    }
}
