/*
 * Gitblit Search API Plugin
 * Exposes Lucene search via JSON API endpoint
 */
package com.gitblit.plugin.search;

import com.gitblit.extensions.GitblitPlugin;
import ro.fortsoft.pf4j.PluginWrapper;
import ro.fortsoft.pf4j.Version;

public class SearchPlugin extends GitblitPlugin {

    public SearchPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public void start() {
        log.info("Search API Plugin started");
    }

    @Override
    public void stop() {
        log.info("Search API Plugin stopped");
    }

    @Override
    public void onInstall() {
        log.info("Search API Plugin installed");
    }

    @Override
    public void onUpgrade(Version oldVersion) {
        log.info("Search API Plugin upgraded from {}", oldVersion);
    }

    @Override
    public void onUninstall() {
        log.info("Search API Plugin uninstalled");
    }
}
