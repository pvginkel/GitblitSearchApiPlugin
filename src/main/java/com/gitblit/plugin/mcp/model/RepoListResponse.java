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
    public int totalCount;
    public boolean limitHit;

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
}
