/*
 * Gitblit MCP Support Plugin
 */
package com.gitblit.plugin.mcp.model;

import java.util.List;

/**
 * Response DTO for /files endpoint.
 */
public class FileListResponse {
    public List<FileInfo> files;

    public static class FileInfo {
        public String path;
        public boolean isDirectory;
        public Long size;  // null for directories

        public FileInfo(String path, boolean isDirectory, Long size) {
            this.path = path;
            this.isDirectory = isDirectory;
            this.size = size;
        }
    }
}
