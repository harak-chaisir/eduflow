package com.eduflow.document.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for verifying a document.
 *
 * <p>Modelled as a mutable bean (no-arg constructor + setters) so Jackson can deserialize
 * it directly on this stack; {@code remarks} is required for REJECT / REQUEST_REVISION,
 * enforced in the service.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyDocumentRequest {

    /** The officer's decision. Required. */
    @NotNull(message = "A decision is required")
    private VerificationDecision decision;

    /** Reason / remarks; required for REJECT and REQUEST_REVISION. */
    @Size(max = 500, message = "Remarks must not exceed 500 characters")
    private String remarks;
}
