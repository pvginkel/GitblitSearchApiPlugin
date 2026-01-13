/*
 * Gitblit MCP Support Plugin
 */
package com.gitblit.plugin.mcp.model;

/**
 * Response DTO for /file endpoint.
 */
public class FileContentResponse {
    public String content;

    public FileContentResponse(String content) {
        this.content = content;
    }
}
