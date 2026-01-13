/*
 * Gitblit MCP Support Plugin
 */
package com.gitblit.plugin.mcp.model;

import java.util.List;

/**
 * Response DTO for /repos endpoint.
 */
public class RepoListResponse {
    public List<RepoInfo> repositories;
    public Pagination pagination;

    public static class RepoInfo {
        public String name;
        public String description;
        public String lastChange;
        public boolean hasCommits;

        public RepoInfo(String name, String description, String lastChange, boolean hasCommits) {
            this.name = name;
            this.description = description;
            this.lastChange = lastChange;
            this.hasCommits = hasCommits;
        }
    }

    public static class Pagination {
        public int totalCount;
        public boolean hasNextPage;
        public String endCursor;

        public Pagination(int totalCount, boolean hasNextPage, String endCursor) {
            this.totalCount = totalCount;
            this.hasNextPage = hasNextPage;
            this.endCursor = endCursor;
        }
    }
}
