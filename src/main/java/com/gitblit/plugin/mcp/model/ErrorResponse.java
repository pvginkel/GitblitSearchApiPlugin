/*
 * Gitblit MCP Support Plugin
 */
package com.gitblit.plugin.mcp.model;

/**
 * Error response DTO.
 */
public class ErrorResponse {
    public String error;
    public int status;

    public ErrorResponse(String error, int status) {
        this.error = error;
        this.status = status;
    }
}
