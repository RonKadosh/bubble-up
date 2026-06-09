package com.ronkadosh.bubbleup.help.api.dto;

import java.util.List;

public record HelpAskResponse(
        String answer,
        String source,
        List<HelpTopicResponse> topics,
        List<HelpActionResponse> actions
) {}
