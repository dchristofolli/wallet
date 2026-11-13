package com.dchristofolli.wallet.service;

import com.dchristofolli.wallet.dto.CreateWalletRequest;
import com.dchristofolli.wallet.dto.WalletResponse;
import com.dchristofolli.wallet.entity.Wallet;
import com.dchristofolli.wallet.exception.DuplicateEntityException;
import com.dchristofolli.wallet.exception.ResourceNotFoundException;
import com.dchristofolli.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @InjectMocks
    private WalletService walletService;

    private UUID walletId;
    private CreateWalletRequest request;
    private Wallet wallet;

    private static final LocalDateTime TEST_DATE = LocalDateTime.of(2026, 11, 13, 12, 0);

    @BeforeEach
    void setUp() {
        walletId = UUID.randomUUID();
        request = new CreateWalletRequest("João Silva", "joao@email.com");

        wallet = new Wallet(walletId, 0L, "João Silva", "joao@email.com",
                BigDecimal.ZERO, TEST_DATE, TEST_DATE);
    }

    @Test
    void createWallet_shouldCreateWalletSuccessfully() {
        when(walletRepository.save(any(Wallet.class))).thenReturn(wallet);

        WalletResponse response = walletService.createWallet(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(walletId);
        assertThat(response.ownerName()).isEqualTo("João Silva");
        assertThat(response.ownerEmail()).isEqualTo("joao@email.com");
        assertThat(response.balance()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void createWallet_shouldThrowDuplicateEntityExceptionWhenEmailDuplicate() {
        when(walletRepository.save(any(Wallet.class)))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate entry"));

        assertThatThrownBy(() -> walletService.createWallet(request))
                .isInstanceOf(DuplicateEntityException.class);
    }

    @Test
    void getWallet_shouldReturnWalletWhenExists() {
        when(walletRepository.findById(walletId)).thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.getWallet(walletId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(walletId);
        assertThat(response.ownerName()).isEqualTo("João Silva");
        assertThat(response.ownerEmail()).isEqualTo("joao@email.com");
    }

    @Test
    void getWallet_shouldThrowResourceNotFoundExceptionWhenNotFound() {
        when(walletRepository.findById(walletId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getWallet(walletId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Wallet");
    }
}
