/*
 * Gitblit MCP Support Plugin
 * Provides REST API endpoints for Gitblit MCP Server integration
 */
package com.gitblit.plugin.mcp;

import com.gitblit.extensions.GitblitPlugin;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

public class MCPSupportPlugin extends GitblitPlugin {

    public MCPSupportPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("MCP Support Plugin started");
    }

    @Override
    public void stop() {
        log.info("MCP Support Plugin stopped");
    }

    @Override
    public void onInstall() {
        log.info("MCP Support Plugin installed");
    }

    @Override
    public void onUpgrade(Version oldVersion) {
        log.info("MCP Support Plugin upgraded from {}", oldVersion);
    }

    @Override
    public void onUninstall() {
        log.info("MCP Support Plugin uninstalled");
    }
}
