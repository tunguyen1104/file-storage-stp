package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.share.ShareView;

public interface ShareFolderUseCase {

    ShareView shareFolder(ShareFolderCommand command);
}
