package com.eduflow.web;

import com.eduflow.exception.GlobalExceptionHandler;
import com.eduflow.security.EduFlowUserDetails;
import com.eduflow.tenant.TenantPlan;
import com.eduflow.tenant.TenantService;
import com.eduflow.tenant.TenantStatus;
import com.eduflow.tenant.dto.TenantResponse;
import com.eduflow.tenant.dto.TenantSettingsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Web-slice tests for {@link TenantWebController} using standalone MockMvc + Mockito.
 * {@code @PreAuthorize} is not enforced here (covered by integration tests). These tests
 * assert the HTMX contract: requests carrying the {@code HX-Request} header receive a
 * Thymeleaf fragment view, while plain requests keep the full-page redirect behaviour.
 */
@ExtendWith(MockitoExtension.class)
class TenantWebControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    @Mock TenantService tenantService;
    @InjectMocks TenantWebController controller;

    private MockMvc mockMvc;
    private Authentication auth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // A principal is needed for the workspace endpoints (tenant resolved from it).
        EduFlowUserDetails principal = mock(EduFlowUserDetails.class);
        lenient().when(principal.getTenantId()).thenReturn(TENANT_ID);
        auth = mock(Authentication.class);
        lenient().when(auth.getPrincipal()).thenReturn(principal);
    }

    private void stubDetailLookups() {
        TenantResponse tenant = TenantResponse.builder()
                .id(TENANT_ID).name("Acme").slug("acme")
                .status(TenantStatus.ACTIVE).plan(TenantPlan.STARTER).build();
        when(tenantService.getById(any())).thenReturn(tenant);
        when(tenantService.getSettings(any())).thenReturn(mock(TenantSettingsResponse.class));
    }

    // ── /tenants/search ──────────────────────────────────────────────────────────

    @Test
    void searchTenants_returnsResultsFragment() throws Exception {
        Page<TenantResponse> empty = new PageImpl<>(List.of());
        when(tenantService.search(any(), any())).thenReturn(empty);

        mockMvc.perform(get("/tenants/search").param("q", "ac"))
                .andExpect(status().isOk())
                .andExpect(view().name("tenant/list :: tenantResults"));
    }

    // ── /tenants/{id}/status ───────────────────────────────────────────────────────

    @Test
    void changeStatus_whenHtmxRequest_returnsTenantPanelsFragment() throws Exception {
        stubDetailLookups();

        mockMvc.perform(post("/tenants/{id}/status", TENANT_ID)
                        .header("HX-Request", "true")
                        .param("status", "SUSPENDED")
                        .param("reason", "invoice overdue"))
                .andExpect(status().isOk())
                .andExpect(view().name("tenant/detail :: tenantPanels"));
    }

    @Test
    void changeStatus_whenNotHtmx_redirectsToTenant() throws Exception {
        mockMvc.perform(post("/tenants/{id}/status", TENANT_ID)
                        .param("status", "SUSPENDED"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/tenants/" + TENANT_ID));
    }

    // ── /tenants/{id}/plan ─────────────────────────────────────────────────────────

    @Test
    void changePlan_whenHtmxRequest_returnsTenantPanelsFragment() throws Exception {
        stubDetailLookups();

        mockMvc.perform(post("/tenants/{id}/plan", TENANT_ID)
                        .header("HX-Request", "true")
                        .param("plan", "PROFESSIONAL"))
                .andExpect(status().isOk())
                .andExpect(view().name("tenant/detail :: tenantPanels"));
    }

    // ── /workspace/profile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_whenHtmxRequest_returnsProfileCardFragment() throws Exception {
        stubDetailLookups();

        mockMvc.perform(post("/workspace/profile")
                        .principal(auth)
                        .header("HX-Request", "true")
                        .param("name", "Acme Renamed"))
                .andExpect(status().isOk())
                .andExpect(view().name("tenant/workspace :: profileCard"));
    }

    @Test
    void updateProfile_whenNotHtmx_redirectsToWorkspace() throws Exception {
        mockMvc.perform(post("/workspace/profile")
                        .principal(auth)
                        .param("name", "Acme Renamed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/workspace"));
    }

    // ── /workspace/settings ────────────────────────────────────────────────────────

    @Test
    void updateSettings_whenHtmxRequest_returnsSettingsCardFragment() throws Exception {
        stubDetailLookups();

        mockMvc.perform(post("/workspace/settings")
                        .principal(auth)
                        .header("HX-Request", "true")
                        .param("brandColor", "#0f766e"))
                .andExpect(status().isOk())
                .andExpect(view().name("tenant/workspace :: settingsCard"));
    }
}
