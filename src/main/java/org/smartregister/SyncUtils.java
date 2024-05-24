package org.smartregister;

import kotlin.Pair;

import java.util.List;

public class SyncUtils {

    protected static Pair<Long, Long> getMinMaxServerVersions(List<Event> events) {
        long maxServerVersion = Long.MIN_VALUE;
        long minServerVersion = Long.MAX_VALUE;
        Event event;

        for (int i = 0; i < events.size(); i++) {
            event = events.get(i);

            long serverVersion = event.getServerVersion();
            if (serverVersion > maxServerVersion) {
                maxServerVersion = serverVersion;
            }

            if (serverVersion < minServerVersion) {
                minServerVersion = serverVersion;
            }

        }

        return new Pair(minServerVersion, maxServerVersion);
    }
}
