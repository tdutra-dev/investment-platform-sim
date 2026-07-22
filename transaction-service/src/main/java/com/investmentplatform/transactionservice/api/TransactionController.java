package com.investmentplatform.transactionservice.api;

import com.investmentplatform.transactionservice.api.dto.CreateTransactionRequest;
import com.investmentplatform.transactionservice.api.dto.TransactionResponse;
import com.investmentplatform.transactionservice.application.TransactionService;
import com.investmentplatform.transactionservice.domain.TransactionId;
import com.investmentplatform.transactionservice.domain.TransactionType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(@RequestBody CreateTransactionRequest request) {
        UUID customerId;
        try {
            customerId = UUID.fromString(request.customerId());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().build();
        }

        TransactionType type;
        try {
            type = TransactionType.valueOf(request.type());
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest().build();
        }

        var transaction = transactionService.createTransaction(customerId, request.amount(), request.currency(), type);
        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponse.from(transaction));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable String id) {
        UUID uuid;
        try {
            uuid = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return transactionService.findById(TransactionId.of(uuid))
                .map(TransactionResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getByCustomerId(@RequestParam String customerId) {
        UUID uuid;
        try {
            uuid = UUID.fromString(customerId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        List<TransactionResponse> responses = transactionService.findByCustomerId(uuid)
                .stream()
                .map(TransactionResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }
}
