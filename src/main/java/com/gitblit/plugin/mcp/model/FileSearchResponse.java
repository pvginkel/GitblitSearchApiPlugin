/*
 * Gitblit MCP Support Plugin
 */
package com.gitblit.plugin.mcp.model;

import java.util.List;

/**
 * Response DTO for /search/files endpoint.
 */
public class FileSearchResponse {
    public String query;
    public int totalCount;
    public boolean limitHit;
    public List<FileSearchResult> results;

    public static class FileSearchResult {
        public String repository;
        public String path;
        public String branch;
        public String commitId;
        public List<Chunk> chunks;
    }

    public static class Chunk {
        public int startLine;
        public int endLine;
        public String content;

        public Chunk(int startLine, int endLine, String content) {
            this.startLine = startLine;
            this.endLine = endLine;
            this.content = content;
        }
    }
}
