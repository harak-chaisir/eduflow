package com.eduflow.application;

import com.eduflow.application.dto.ApplicationResponse;
import com.eduflow.exception.GlobalExceptionHandler;
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
 * Web-slice tests for {@link ApplicationController} using standalone MockMvc.
 * {@link GlobalExceptionHandler} is wired in to verify status mapping (201/404/409).
 */
@ExtendWith(MockitoExtension.class)
class ApplicationControllerTest {

    private static final UUID STUDENT_ID     = UUID.randomUUID();
    private static final UUID COURSE_ID      = UUID.randomUUID();
    private static final UUID APPLICATION_ID = UUID.randomUUID();

    @Mock ApplicationService applicationService;
    @InjectMocks ApplicationController applicationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(applicationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void create_withValidBody_returns201() throws Exception {
        ApplicationResponse response = ApplicationResponse.builder()
                .id(APPLICATION_ID).status(ApplicationStatus.DRAFT)
                .studentId(STUDENT_ID).courseId(COURSE_ID).courseName("MSc CS").build();
        when(applicationService.createApplication(eq(STUDENT_ID), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/students/{sid}/applications", STUDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + COURSE_ID + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void create_whenCourseMissingFromBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/students/{sid}/applications", STUDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_whenNotFound_returns404() throws Exception {
        when(applicationService.get(APPLICATION_ID))
                .thenThrow(new ApplicationNotFoundException(APPLICATION_ID));

        mockMvc.perform(get("/api/v1/applications/{id}", APPLICATION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void updateStatus_whenIllegalTransition_returns409() throws Exception {
        when(applicationService.updateStatus(APPLICATION_ID, ApplicationStatus.UNCONDITIONAL_OFFER))
                .thenThrow(new InvalidApplicationStatusTransitionException(
                        ApplicationStatus.DRAFT, ApplicationStatus.UNCONDITIONAL_OFFER));

        mockMvc.perform(patch("/api/v1/applications/{id}/status", APPLICATION_ID)
                        .param("newStatus", "UNCONDITIONAL_OFFER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void create_whenDuplicate_returns409() throws Exception {
        when(applicationService.createApplication(eq(STUDENT_ID), any()))
                .thenThrow(new DuplicateApplicationException(STUDENT_ID, COURSE_ID));

        mockMvc.perform(post("/api/v1/students/{sid}/applications", STUDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + COURSE_ID + "\"}"))
                .andExpect(status().isConflict());
    }
}
