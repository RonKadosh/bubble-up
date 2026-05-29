package com.ronkadosh.bubbleup.admin.api.dto;

import com.ronkadosh.bubbleup.groups.internal.dto.admin.GroupAdminDetail;
import com.ronkadosh.bubbleup.groups.internal.dto.admin.GroupAdminDto;
import com.ronkadosh.bubbleup.groups.model.GroupVisibility;
import com.ronkadosh.bubbleup.groups.model.MembershipRole;
import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AdminGroupDtos {
    private AdminGroupDtos() {}

    public record GroupResponse(
            UUID id,
            String name,
            String description,
            GroupVisibility visibility,
            UUID offeringId,
            UUID courseId,
            UUID createdBy,
            Instant createdAt,
            int memberCount
    ) {
        public static GroupResponse from(GroupAdminDto d) {
            return new GroupResponse(
                    d.id(), d.name(), d.description(), d.visibility(),
                    d.offeringId(), d.courseId(), d.createdBy(), d.createdAt(), d.memberCount()
            );
        }
    }

    public record GroupDetailResponse(
            GroupResponse group,
            List<MemberRow> members
    ) {
        public record MemberRow(
                UUID userId,
                String email,
                String displayName,
                MembershipRole role,
                Instant joinedAt
        ) {
            static MemberRow from(GroupAdminDetail.Member m) {
                return new MemberRow(m.userId(), m.email(), m.displayName(), m.role(), m.joinedAt());
            }
        }

        public static GroupDetailResponse from(GroupAdminDetail d) {
            return new GroupDetailResponse(
                    GroupResponse.from(d.group()),
                    d.members().stream().map(MemberRow::from).toList()
            );
        }
    }

    public record DeleteGroupRequest(@NotBlank String reason) {}
}
