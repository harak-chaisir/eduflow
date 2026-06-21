package com.eduflow.web;

import com.eduflow.exception.GlobalExceptionHandler;
import com.eduflow.user.LastTenantAdminException;
import com.eduflow.user.StaffService;
import com.eduflow.user.UserStatus;
import com.eduflow.user.dto.StaffResponse;
import com.eduflow.user.dto.StaffRosterRow;
import com.eduflow.user.dto.StaffStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Web-slice tests for {@link StaffWebController} using standalone MockMvc + Mockito.
 * {@code @PreAuthorize} is not enforced here (covered by integration tests). These tests
 * assert the HTMX contract: requests carrying {@code HX-Request} receive a Thymeleaf
 * fragment view, while plain requests keep the full-page redirect behaviour.
 */
@ExtendWith(MockitoExtension.class)
class StaffWebControllerTest {

    private static final UUID STAFF_ID = UUID.randomUUID();

    @Mock StaffService staffService;
    @InjectMocks StaffWebController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private StaffResponse staff(UserStatus statusValue) {
        return StaffResponse.builder()
                .id(STAFF_ID).email("jane@test.com").firstName("Jane").lastName("Doe")
                .fullName("Jane Doe").status(statusValue).roleNames(List.of("ROLE_COUNSELOR"))
                .build();
    }

    private StaffRosterRow rosterRow() {
        return new StaffRosterRow(STAFF_ID, "Jane Doe", "Jane", "jane@test.com",
                List.of("ROLE_COUNSELOR"), UserStatus.ACTIVE, true, 5L, "—");
    }

    private StaffStats stats() {
        return new StaffStats(3, 2, 4, 3, 25, 22, 12, "Professional");
    }

    @Test
    void listStaff_returnsFullListView() throws Exception {
        when(staffService.getStaffStats()).thenReturn(stats());
        when(staffService.searchStaffRoster(any(), any())).thenReturn(new PageImpl<>(List.of(rosterRow())));

        mockMvc.perform(get("/staff"))
                .andExpect(status().isOk())
                .andExpect(view().name("staff/list"));
    }

    @Test
    void searchStaff_returnsResultsFragment() throws Exception {
        when(staffService.searchStaffRoster(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/staff/search").param("name", "ja"))
                .andExpect(status().isOk())
                .andExpect(view().name("staff/list :: staffResults"));
    }

    @Test
    void updateStatus_withHxRequest_returnsStatusCardFragment() throws Exception {
        when(staffService.setStatus(eq(STAFF_ID), eq(UserStatus.INACTIVE)))
                .thenReturn(staff(UserStatus.INACTIVE));

        mockMvc.perform(post("/staff/{id}/status", STAFF_ID)
                        .param("newStatus", "INACTIVE")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("staff/detail :: statusCard"));
    }

    @Test
    void updateStatus_withoutHxRequest_redirectsToDetail() throws Exception {
        when(staffService.setStatus(eq(STAFF_ID), eq(UserStatus.ACTIVE)))
                .thenReturn(staff(UserStatus.ACTIVE));

        mockMvc.perform(post("/staff/{id}/status", STAFF_ID).param("newStatus", "ACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/staff/*"));
    }

    @Test
    void updateStatus_whenLastAdminBlockedOverHtmx_returnsStatusCardWithError() throws Exception {
        when(staffService.setStatus(eq(STAFF_ID), eq(UserStatus.INACTIVE)))
                .thenThrow(new LastTenantAdminException());
        when(staffService.getStaff(STAFF_ID)).thenReturn(staff(UserStatus.ACTIVE));

        mockMvc.perform(post("/staff/{id}/status", STAFF_ID)
                        .param("newStatus", "INACTIVE")
                        .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("staff/detail :: statusCard"));
    }
}
