package com.filestorage.adapter.in.rest.mapper;

import com.filestorage.domain.model.audit.AuditLogView;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Writer;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class AuditLogCsvWriter {

    private static final String[] HEADERS = {
            "id", "createdAt", "action", "severity", "tenantId",
            "userId", "userEmail", "actorRole", "fileId",
            "ipAddress", "userAgent", "requestId", "extraData"
    };

    public void write(List<AuditLogView> rows, Writer out) throws IOException {
        out.write('﻿'); // UTF-8 BOM so Excel opens the file with correct encoding
        writeRow(out, HEADERS);
        for (AuditLogView row : rows) {
            writeRow(out, new String[]{
                    nullSafe(row.id()),
                    row.createdAt() == null ? "" : DateTimeFormatter.ISO_INSTANT.format(row.createdAt()),
                    nullSafe(row.action()),
                    row.action() == null ? "" : row.action().severity().name(),
                    nullSafe(row.tenantId()),
                    nullSafe(row.userId()),
                    nullSafe(row.userEmail()),
                    nullSafe(row.actorRole()),
                    nullSafe(row.fileId()),
                    nullSafe(row.ipAddress()),
                    nullSafe(row.userAgent()),
                    nullSafe(row.requestId()),
                    row.extraData() == null ? "" : row.extraData().toString()
            });
        }
        out.flush();
    }

    private void writeRow(Writer out, String[] cells) throws IOException {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(escape(cells[i]));
        }
        out.write("\r\n");
    }

    private String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (!needsQuote) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private String nullSafe(Object value) {
        return value == null ? "" : value.toString();
    }
}
