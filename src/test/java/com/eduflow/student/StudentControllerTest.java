package com.eduflow.student;

import com.eduflow.exception.GlobalExceptionHandler;
import com.eduflow.student.dto.StudentResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer slice tests for {@link StudentController}.
 *
 * <p>Uses a standalone MockMvc setup with Mockito — no Spring context is loaded.
 * {@link GlobalExceptionHandler} is wired in to verify that domain exceptions are
 * correctly mapped to HTTP status codes.</p>
 *
 * <p>Note: {@code @PreAuthorize} annotations are <em>not</em> enforced in standalone
 * mode; access-control logic is verified in integration tests.</p>
 */
@ExtendWith(MockitoExtension.class)
class StudentControllerTest {

    private static final UUID STUDENT_ID = UUID.randomUUID();

    @Mock StudentService studentService;
    @InjectMocks StudentController studentController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(studentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ── POST /api/v1/students ─────────────────────────────────────────────────

    @Test
    void registerStudent_withValidBody_returns201WithLocationHeader() throws Exception {
        StudentResponse response = StudentResponse.builder()
                .id(STUDENT_ID)
                .firstName("Alice")
                .lastName("Smith")
                .email("alice@test.com")
                .status(StudentStatus.LEAD)
                .build();

        when(studentService.registerStudent(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Alice",
                                  "lastName": "Smith",
                                  "email": "alice@test.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.email").value("alice@test.com"))
                .andExpect(jsonPath("$.status").value("LEAD"));
    }

    @Test
    void registerStudent_withMissingFirstName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "lastName": "Smith",
                                  "email": "missing@test.com"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void registerStudent_withInvalidEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Alice",
                                  "lastName": "Smith",
                                  "email": "not-an-email"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerStudent_withDuplicateEmail_returns409() throws Exception {
        when(studentService.registerStudent(any()))
                .thenThrow(new DuplicateStudentException("alice@test.com"));

        mockMvc.perform(post("/api/v1/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName": "Alice",
                                  "lastName": "Smith",
                                  "email": "alice@test.com"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    // ── GET /api/v1/students/{id} ─────────────────────────────────────────────

    @Test
    void getStudent_whenExists_returns200() throws Exception {
        StudentResponse response = StudentResponse.builder()
                .id(STUDENT_ID)
                .firstName("Bob")
                .lastName("Jones")
                .email("bob@test.com")
                .status(StudentStatus.ACTIVE)
                .build();

        when(studentService.getStudent(STUDENT_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/students/{id}", STUDENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(STUDENT_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void getStudent_whenNotFound_returns404() throws Exception {
        when(studentService.getStudent(STUDENT_ID))
                .thenThrow(new StudentNotFoundException(STUDENT_ID));

        mockMvc.perform(get("/api/v1/students/{id}", STUDENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(STUDENT_ID.toString())));
    }

    // ── PATCH /api/v1/students/{id}/status ───────────────────────────────────

    @Test
    void updateStatus_withValidTransition_returns200() throws Exception {
        StudentResponse response = StudentResponse.builder()
                .id(STUDENT_ID)
                .firstName("Carol")
                .lastName("Davis")
                .email("carol@test.com")
                .status(StudentStatus.QUALIFIED)
                .build();

        when(studentService.updateStatus(STUDENT_ID, StudentStatus.QUALIFIED)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/students/{id}/status", STUDENT_ID)
                        .param("newStatus", "QUALIFIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUALIFIED"));
    }

    @Test
    void updateStatus_withInvalidTransition_returns422() throws Exception {
        when(studentService.updateStatus(STUDENT_ID, StudentStatus.ENROLLED))
                .thenThrow(new InvalidStudentStatusTransitionException(
                        StudentStatus.LEAD, StudentStatus.ENROLLED));

        mockMvc.perform(patch("/api/v1/students/{id}/status", STUDENT_ID)
                        .param("newStatus", "ENROLLED"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.error").value("UNPROCESSABLE_ENTITY"));
    }

    // ── PATCH /api/v1/students/{id} ───────────────────────────────────────────

    @Test
    void updateStudent_withValidBody_returns200() throws Exception {
        StudentResponse response = StudentResponse.builder()
                .id(STUDENT_ID)
                .firstName("Dan")
                .lastName("Evans")
                .email("dan@test.com")
                .status(StudentStatus.ACTIVE)
                .build();

        when(studentService.updateStudent(any(), any())).thenReturn(response);

        mockMvc.perform(patch("/api/v1/students/{id}", STUDENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "phone": "+44 7700 900000" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(STUDENT_ID.toString()));
    }

    // ── DELETE /api/v1/students/{id} ──────────────────────────────────────────

    @Test
    void deleteStudent_whenExists_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/students/{id}", STUDENT_ID))
                .andExpect(status().isNoContent());

        verify(studentService).deleteStudent(STUDENT_ID);
    }

    @Test
    void deleteStudent_whenNotFound_returns404() throws Exception {
        org.mockito.Mockito.doThrow(new StudentNotFoundException(STUDENT_ID))
                .when(studentService).deleteStudent(STUDENT_ID);

        mockMvc.perform(delete("/api/v1/students/{id}", STUDENT_ID))
                .andExpect(status().isNotFound());
    }
}
