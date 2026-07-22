package com.investmentplatform.transactionservice.application;

import com.investmentplatform.transactionservice.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    @InjectMocks
    private TransactionService transactionService;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String EUR = "EUR";

    // -----------------------------------------------------------------------
    // DEPOSIT — happy path
    // -----------------------------------------------------------------------

    @Test
    void createTransaction_deposit_shouldCreateWithPendingStatus() {
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(
                CUSTOMER_ID, new BigDecimal("1000.00"), EUR, TransactionType.DEPOSIT);

        assertThat(result.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        assertThat(result.getMoney().getAmount()).isEqualByComparingTo("1000.00");
        assertThat(result.getMoney().getCurrency()).isEqualTo(EUR);
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo(CUSTOMER_ID);
        verify(transactionRepository).save(any(Transaction.class));
        // balance check NOT invoked for DEPOSIT
        verify(transactionRepository, never()).findByCustomerId(any());
        // Outbox event written in same transaction
        verify(outboxEventWriter).writeTransactionCreatedEvent(any(Transaction.class));
    }

    // -----------------------------------------------------------------------
    // WITHDRAWAL — business rule
    // -----------------------------------------------------------------------

    @Test
    void createTransaction_withdrawal_sufficientBalance_shouldCreate() {
        // given: existing DEPOSIT of 1000€ (PENDING counts toward balance)
        Transaction deposit = Transaction.create(CUSTOMER_ID, Money.of(new BigDecimal("1000"), EUR), TransactionType.DEPOSIT);
        when(transactionRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(deposit));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(
                CUSTOMER_ID, new BigDecimal("500.00"), EUR, TransactionType.WITHDRAWAL);

        assertThat(result.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.PENDING);
        verify(outboxEventWriter).writeTransactionCreatedEvent(any(Transaction.class));
    }

    @Test
    void createTransaction_withdrawal_exactBalance_shouldSucceed() {
        Transaction deposit = Transaction.create(CUSTOMER_ID, Money.of(new BigDecimal("500"), EUR), TransactionType.DEPOSIT);
        when(transactionRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(deposit));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // withdraw exact balance — must succeed (balance > 0 == 0)
        Transaction result = transactionService.createTransaction(
                CUSTOMER_ID, new BigDecimal("500.00"), EUR, TransactionType.WITHDRAWAL);

        assertThat(result.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        verify(outboxEventWriter).writeTransactionCreatedEvent(any(Transaction.class));
    }

    @Test
    void createTransaction_withdrawal_insufficientBalance_shouldThrow() {
        // given: no existing transactions → balance = 0
        when(transactionRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> transactionService.createTransaction(
                CUSTOMER_ID, new BigDecimal("500.00"), EUR, TransactionType.WITHDRAWAL))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("Insufficient balance");

        verify(transactionRepository, never()).save(any());
        verify(outboxEventWriter, never()).writeTransactionCreatedEvent(any());
    }

    @Test
    void createTransaction_withdrawal_failedTransactionsNotCountedInBalance_shouldThrow() {
        // given: deposit was FAILED → should NOT count toward balance
        Transaction failedDeposit = Transaction.create(CUSTOMER_ID, Money.of(new BigDecimal("1000"), EUR), TransactionType.DEPOSIT);
        failedDeposit.fail();
        when(transactionRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(failedDeposit));

        assertThatThrownBy(() -> transactionService.createTransaction(
                CUSTOMER_ID, new BigDecimal("500.00"), EUR, TransactionType.WITHDRAWAL))
                .isInstanceOf(InsufficientBalanceException.class);
        verify(outboxEventWriter, never()).writeTransactionCreatedEvent(any());
    }

    @Test
    void createTransaction_withdrawal_completedDepositCountsAsBalance() {
        Transaction deposit = Transaction.create(CUSTOMER_ID, Money.of(new BigDecimal("1000"), EUR), TransactionType.DEPOSIT);
        deposit.complete();
        when(transactionRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(deposit));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction result = transactionService.createTransaction(
                CUSTOMER_ID, new BigDecimal("300.00"), EUR, TransactionType.WITHDRAWAL);

        assertThat(result.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        verify(outboxEventWriter).writeTransactionCreatedEvent(any(Transaction.class));
    }

    // -----------------------------------------------------------------------
    // Input validation
    // -----------------------------------------------------------------------

    @Test
    void createTransaction_shouldThrow_whenCustomerIdIsNull() {
        assertThatThrownBy(() -> transactionService.createTransaction(
                null, new BigDecimal("100"), EUR, TransactionType.DEPOSIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer ID");

        verifyNoInteractions(transactionRepository, outboxEventWriter);
    }

    @Test
    void createTransaction_shouldThrow_whenAmountIsZero() {
        assertThatThrownBy(() -> transactionService.createTransaction(
                CUSTOMER_ID, BigDecimal.ZERO, EUR, TransactionType.DEPOSIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be positive");

        verifyNoInteractions(transactionRepository, outboxEventWriter);
    }

    @Test
    void createTransaction_shouldThrow_whenAmountIsNegative() {
        assertThatThrownBy(() -> transactionService.createTransaction(
                CUSTOMER_ID, new BigDecimal("-50"), EUR, TransactionType.DEPOSIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amount must be positive");
    }

    @Test
    void createTransaction_shouldThrow_whenCurrencyIsBlank() {
        assertThatThrownBy(() -> transactionService.createTransaction(
                CUSTOMER_ID, new BigDecimal("100"), "  ", TransactionType.DEPOSIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency");
    }

    // -----------------------------------------------------------------------
    // Query methods
    // -----------------------------------------------------------------------

    @Test
    void findById_shouldReturnTransactionWhenFound() {
        Transaction tx = Transaction.create(CUSTOMER_ID, Money.of(new BigDecimal("100"), EUR), TransactionType.DEPOSIT);
        when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));

        Optional<Transaction> result = transactionService.findById(tx.getId());

        assertThat(result).isPresent().contains(tx);
    }

    @Test
    void findById_shouldReturnEmptyWhenNotFound() {
        TransactionId id = TransactionId.generate();
        when(transactionRepository.findById(id)).thenReturn(Optional.empty());

        assertThat(transactionService.findById(id)).isEmpty();
    }

    @Test
    void findByCustomerId_shouldReturnAllTransactions() {
        Transaction tx1 = Transaction.create(CUSTOMER_ID, Money.of(new BigDecimal("100"), EUR), TransactionType.DEPOSIT);
        Transaction tx2 = Transaction.create(CUSTOMER_ID, Money.of(new BigDecimal("200"), EUR), TransactionType.INVESTMENT);
        when(transactionRepository.findByCustomerId(CUSTOMER_ID)).thenReturn(List.of(tx1, tx2));

        List<Transaction> result = transactionService.findByCustomerId(CUSTOMER_ID);

        assertThat(result).hasSize(2).containsExactly(tx1, tx2);
    }
}
