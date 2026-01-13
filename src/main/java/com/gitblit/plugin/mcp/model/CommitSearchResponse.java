/*
 * Gitblit MCP Support Plugin
 */
package com.gitblit.plugin.mcp.model;

import java.util.List;

/**
 * Response DTO for /search/commits endpoint.
 */
public class CommitSearchResponse {
    public String query;
    public int totalCount;
    public boolean limitHit;
    public List<CommitInfo> commits;

    public static class CommitInfo {
        public String repository;
        public String commit;
        public String author;
        public String committer;
        public String date;
        public String title;
        public String message;
        public String branch;
    }
}
