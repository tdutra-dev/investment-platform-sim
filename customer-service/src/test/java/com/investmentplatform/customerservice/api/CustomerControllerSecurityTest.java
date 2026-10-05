package com.investmentplatform.customerservice.api;

import com.investmentplatform.customerservice.application.CustomerService;
import com.investmentplatform.customerservice.domain.Customer;
import com.investmentplatform.customerservice.infrastructure.config.SecurityConfig;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@Import(SecurityConfig.class)
class CustomerControllerSecurityTest {

    private static final String BODY = "{\"name\":\"Ada\",\"email\":\"ada@example.com\"}";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private CustomerService customerService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor scope(String scope) {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }

    @Test
    void register_withoutToken_returns401() throws Exception {
        mvc.perform(post("/api/customers").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_withoutWriteScope_returns403() throws Exception {
        mvc.perform(post("/api/customers").with(scope("customers:read"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_withWriteScope_returns201() throws Exception {
        when(customerService.registerCustomer(any(), any())).thenReturn(Customer.register("Ada", "ada@example.com"));

        mvc.perform(post("/api/customers").with(scope("customers:write"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/customers/{id}", java.util.UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_withoutReadScope_returns403() throws Exception {
        mvc.perform(get("/api/customers/{id}", java.util.UUID.randomUUID()).with(scope("customers:write")))
                .andExpect(status().isForbidden());
    }

    @Test
    void get_withReadScope_returns200() throws Exception {
        Customer customer = Customer.register("Ada", "ada@example.com");
        when(customerService.findById(any())).thenReturn(Optional.of(customer));

        mvc.perform(get("/api/customers/{id}", customer.getId().value()).with(scope("customers:read")))
                .andExpect(status().isOk());
    }
}
