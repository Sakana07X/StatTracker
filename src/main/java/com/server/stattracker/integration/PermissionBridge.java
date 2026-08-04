package com.server.stattracker.integration;

import java.util.UUID;

public interface PermissionBridge {

    boolean isAvailable();

    String getName();

    void grantPermission(UUID uuid, String permission);

    void revokePermission(UUID uuid, String permission);

    boolean hasPermission(UUID uuid, String permission);
}
