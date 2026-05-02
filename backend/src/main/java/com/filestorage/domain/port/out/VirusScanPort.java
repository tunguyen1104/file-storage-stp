package com.filestorage.domain.port.out;

import java.io.InputStream;

/**
 * Outbound port for virus scanning.
 * The domain defines the interface; adapters provide the implementation (e.g. ClamAV REST).
 */
public interface VirusScanPort {

    /**
     * Scans the given file stream for viruses.
     *
     * @param fileStream input stream of file bytes to scan
     * @return {@code true} if the file is infected, {@code false} if clean
     */
    boolean scan(InputStream fileStream);
}
