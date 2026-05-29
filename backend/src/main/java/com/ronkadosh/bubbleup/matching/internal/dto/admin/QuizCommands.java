package com.ronkadosh.bubbleup.matching.internal.dto.admin;

public final class QuizCommands {
    private QuizCommands() {}

    /**
     * Quiz text is stored per-language. {@code textEn} is required on create
     * (it's the fallback when {@code textHe} is missing or the requesting
     * locale isn't Hebrew). {@code textHe} is optional.
     */
    public record CreateQuestion(String textEn, String textHe, Integer orderIndex, Boolean active) {}

    public record UpdateQuestion(String textEn, String textHe, Integer orderIndex, Boolean active) {}

    /**
     * Per-axis role weights (Leader/Planner/Expert/Creative/Communicator/TeamPlayer/Challenger).
     * Each weight is double — defaults to 0 if null on create.
     */
    public record CreateOption(
            String textEn,
            String textHe,
            Double weightLeader,
            Double weightPlanner,
            Double weightExpert,
            Double weightCreative,
            Double weightCommunicator,
            Double weightTeamPlayer,
            Double weightChallenger
    ) {}

    public record UpdateOption(
            String textEn,
            String textHe,
            Double weightLeader,
            Double weightPlanner,
            Double weightExpert,
            Double weightCreative,
            Double weightCommunicator,
            Double weightTeamPlayer,
            Double weightChallenger
    ) {}
}
