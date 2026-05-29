package com.ronkadosh.bubbleup.groups.api.dto;

import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(
        @Size(min = 1, max = 100) String name,
        @Size(max = 500) String description,
        GroupVisibility visibility
) {}
