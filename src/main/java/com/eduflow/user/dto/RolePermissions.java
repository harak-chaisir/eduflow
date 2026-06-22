package com.eduflow.user.dto;

import java.util.List;

/**
 * The capabilities a staff member's roles grant and deny, for the "Role & access" card.
 *
 * @param allowed    capabilities the user's roles permit
 * @param restricted capabilities none of the user's roles permit
 */
public record RolePermissions(List<String> allowed, List<String> restricted) {
}
