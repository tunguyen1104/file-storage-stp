package com.filestorage.domain.model.share;

import com.filestorage.domain.model.enums.MyPermission;
import com.filestorage.domain.model.enums.PermissionSource;

public record PermissionResult(
        MyPermission permission,
        PermissionSource source
) {
}
