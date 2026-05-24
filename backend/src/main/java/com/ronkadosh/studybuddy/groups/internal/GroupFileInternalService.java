package com.ronkadosh.studybuddy.groups.internal;

import java.util.UUID;

public interface GroupFileInternalService {
    /** Deletes all files (storage bytes + metadata rows) belonging to a group. */
    void deleteFilesForGroup(UUID groupId);
}
