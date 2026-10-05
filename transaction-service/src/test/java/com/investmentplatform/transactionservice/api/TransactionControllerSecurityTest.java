package com.investmentplatform.transactionservice.api;

import com.investmentplatform.transactionservice.application.TransactionService;
import com.investmentplatform.transactionservice.domain.Money;
import com.investmentplatform.transactionservice.domain.Transaction;
import com.investmentplatform.transactionservice.domain.TransactionType;
import com.investmentplatform.transactionservice.infrastructure.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@Import(SecurityConfig.class)
class TransactionControllerSecurityTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String BODY =
            "{\"customerId\":\"" + CUSTOMER_ID + "\",\"amount\":10.00,\"currency\":\"EUR\",\"type\":\"DEPOSIT\"}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private TransactionService transactionService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor scope(String scope) {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }

    @Test
    void create_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/transactions").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_withoutWriteScope_returns403() throws Exception {
        mvc.perform(post("/api/transactions").with(scope("transactions:read"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withWriteScope_returns201() throws Exception {
        when(transactionService.createTransaction(any(), any(), any(), any())).thenReturn(
                Transaction.create(CUSTOMER_ID, Money.of(new BigDecimal("10.00"), "EUR"), TransactionType.DEPOSIT));

        mvc.perform(post("/api/transactions").with(scope("transactions:write"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void getById_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/transactions/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void getById_withoutReadScope_returns403() throws Exception {
        mvc.perform(get("/api/transactions/{id}", UUID.randomUUID()).with(scope("transactions:write")))
                .andExpect(status().isForbidden());
    }

    @Test
    void getById_withReadScope_returns200() throws Exception {
        Transaction transaction = Transaction.create(CUSTOMER_ID, Money.of(new BigDecimal("10.00"), "EUR"), TransactionType.DEPOSIT);
        when(transactionService.findById(any())).thenReturn(Optional.of(transaction));

        mvc.perform(get("/api/transactions/{id}", transaction.getId().value()).with(scope("transactions:read")))
                .andExpect(status().isOk());
    }

    @Test
    void listByCustomer_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/transactions").param("customerId", CUSTOMER_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listByCustomer_withoutReadScope_returns403() throws Exception {
        mvc.perform(get("/api/transactions").param("customerId", CUSTOMER_ID.toString())
                        .with(scope("audit:read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listByCustomer_withReadScope_returns200() throws Exception {
        when(transactionService.findByCustomerId(any())).thenReturn(List.of());

        mvc.perform(get("/api/transactions").param("customerId", CUSTOMER_ID.toString())
                        .with(scope("transactions:read")))
                .andExpect(status().isOk());
    }
}
