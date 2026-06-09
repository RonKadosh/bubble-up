package com.ronkadosh.bubbleup.help.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HelpAskRequest(
        @NotBlank @Size(max = 500) String question,
        @Size(max = 16) String locale,
        @Size(max = 128) String currentPath
) {}
