/*
 * Gitblit MCP Support Plugin
 */
package com.gitblit.plugin.mcp.handlers;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.gitblit.manager.IGitblit;
import com.gitblit.models.UserModel;

/**
 * Interface for MCP API request handlers.
 */
public interface RequestHandler {

    /**
     * Handle an HTTP request.
     *
     * @param request the HTTP request
     * @param response the HTTP response
     * @param gitblit the Gitblit manager
     * @param user the authenticated user
     * @throws IOException if an I/O error occurs
     */
    void handle(HttpServletRequest request, HttpServletResponse response,
                IGitblit gitblit, UserModel user) throws IOException;
}
