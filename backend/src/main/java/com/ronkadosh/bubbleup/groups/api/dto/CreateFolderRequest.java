package com.ronkadosh.bubbleup.groups.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateFolderRequest(
        @NotBlank @Size(max = 120) String name,
        UUID parentId
) {}
