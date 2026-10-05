package com.investmentplatform.auditlogservice.api;

import com.investmentplatform.auditlogservice.infrastructure.config.SecurityConfig;
import com.investmentplatform.auditlogservice.infrastructure.persistence.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditLogController.class)
@Import(SecurityConfig.class)
class AuditLogControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AuditLogRepository auditLogRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static RequestPostProcessor scope(String scope) {
        return jwt().authorities(new SimpleGrantedAuthority("SCOPE_" + scope));
    }

    @Test
    void query_withoutToken_returns401() throws Exception {
        mvc.perform(get("/api/audit-log").param("aggregateId", "agg-1")).andExpect(status().isUnauthorized());
    }

    @Test
    void query_withoutAuditScope_returns403() throws Exception {
        mvc.perform(get("/api/audit-log").param("aggregateId", "agg-1").with(scope("customers:read")))
                .andExpect(status().isForbidden());
    }

    @Test
    void query_withAuditScope_returns200() throws Exception {
        when(auditLogRepository.findByAggregateIdOrderByReceivedAtDesc("agg-1")).thenReturn(List.of());

        mvc.perform(get("/api/audit-log").param("aggregateId", "agg-1").with(scope("audit:read")))
                .andExpect(status().isOk());
    }

    @Test
    void query_withAuditScopeButNoAggregateId_returns400() throws Exception {
        mvc.perform(get("/api/audit-log").with(scope("audit:read"))).andExpect(status().isBadRequest());
    }
}
