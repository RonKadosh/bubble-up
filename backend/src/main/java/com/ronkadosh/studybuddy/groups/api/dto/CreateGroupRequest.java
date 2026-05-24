package com.ronkadosh.studybuddy.groups.api.dto;

import com.ronkadosh.studybuddy.groups.model.GroupVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        GroupVisibility visibility
) {}
