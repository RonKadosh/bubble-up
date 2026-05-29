package com.ronkadosh.bubbleup.groups.internal;

import java.util.UUID;

public interface GroupFileInternalService {
    /** Deletes all files (storage bytes + metadata rows) belonging to a group. */
    void deleteFilesForGroup(UUID groupId);

    /** Deletes all folder rows belonging to a group. Must be called AFTER {@link #deleteFilesForGroup(UUID)}. */
    void deleteFoldersForGroup(UUID groupId);
}
