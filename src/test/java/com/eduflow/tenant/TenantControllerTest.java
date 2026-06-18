package com.eduflow.tenant;

import com.eduflow.exception.GlobalExceptionHandler;
import com.eduflow.tenant.dto.TenantResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-slice tests for {@link TenantController} using standalone MockMvc + Mockito.
 * {@code @PreAuthorize} is not enforced here (covered by integration tests); the
 * {@link GlobalExceptionHandler} is wired in to assert status mapping.
 */
@ExtendWith(MockitoExtension.class)
class TenantControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock TenantService tenantService;
    @InjectMocks TenantController tenantController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(tenantController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createTenant_withValidBody_returns201WithLocation() throws Exception {
        TenantResponse response = TenantResponse.builder()
                .id(TENANT_ID).name("Acme").slug("acme")
                .status(TenantStatus.ACTIVE).plan(TenantPlan.STARTER).build();
        when(tenantService.provision(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Acme",
                                  "slug": "acme",
                                  "plan": "STARTER",
                                  "primaryContactName": "Ann",
                                  "primaryContactEmail": "ann@acme.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.slug").value("acme"));
    }

    @Test
    void createTenant_withDuplicateSlug_returns409() throws Exception {
        when(tenantService.provision(any())).thenThrow(new DuplicateSlugException("acme"));

        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Acme",
                                  "slug": "acme",
                                  "plan": "STARTER",
                                  "primaryContactName": "Ann",
                                  "primaryContactEmail": "ann@acme.com"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void createTenant_withBlankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/tenants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "slug": "acme",
                                  "plan": "STARTER",
                                  "primaryContactName": "Ann",
                                  "primaryContactEmail": "ann@acme.com"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changeStatus_invalidTransition_returns422() throws Exception {
        when(tenantService.changeStatus(eq(TENANT_ID), any()))
                .thenThrow(new InvalidTenantStatusTransitionException(
                        TenantStatus.SUSPENDED, TenantStatus.INACTIVE));

        mockMvc.perform(patch("/api/v1/tenants/{id}/status", TENANT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "status": "INACTIVE" }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("UNPROCESSABLE_ENTITY"));
    }

    @Test
    void getTenant_whenNotFound_returns404() throws Exception {
        when(tenantService.getById(TENANT_ID)).thenThrow(new TenantNotFoundException(TENANT_ID));

        mockMvc.perform(get("/api/v1/tenants/{id}", TENANT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
