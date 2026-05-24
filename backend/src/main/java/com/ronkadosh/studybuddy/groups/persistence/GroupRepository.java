package com.ronkadosh.studybuddy.groups.persistence;

import com.ronkadosh.studybuddy.groups.model.StudyGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface GroupRepository extends JpaRepository<StudyGroup, UUID> {
}
