/*
 * Gitblit Search API Plugin
 * HTTP filter that exposes Lucene search via JSON API
 */
package com.gitblit.plugin.search;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.Extension;

import com.gitblit.Constants.SearchObjectType;
import com.gitblit.extensions.HttpRequestFilter;
import com.gitblit.manager.IAuthenticationManager;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SearchResult;
import com.gitblit.models.UserModel;
import com.gitblit.servlet.GitblitContext;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Extension
public class SearchApiFilter extends HttpRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SearchApiFilter.class);
    private static final String API_PATH = "/api/search";
    private static final int DEFAULT_CONTEXT_LINES = 100;
    private static final int MAX_CONTEXT_LINES = 500;

    private final Gson gson;

    public SearchApiFilter() {
        this.gson = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .setPrettyPrinting()
            .create();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String uri = httpRequest.getRequestURI();

        // Only handle requests to our API endpoint
        if (!uri.startsWith(API_PATH)) {
            chain.doFilter(request, response);
            return;
        }

        // Set CORS headers for API access
        httpResponse.setHeader("Access-Control-Allow-Origin", "*");
        httpResponse.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        httpResponse.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");

        // Handle preflight requests
        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            httpResponse.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        try {
            handleSearchRequest(httpRequest, httpResponse);
        } catch (Exception e) {
            log.error("Error processing search request", e);
            sendError(httpResponse, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Internal server error: " + e.getMessage());
        }
    }

    private void handleSearchRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // Get managers
        IGitblit gitblit = GitblitContext.getManager(IGitblit.class);
        IAuthenticationManager authManager = GitblitContext.getManager(IAuthenticationManager.class);

        // Authenticate user (supports Basic auth, API tokens, etc.)
        UserModel user = authManager.authenticate(request);
        if (user == null) {
            user = UserModel.ANONYMOUS;
        }

        // Parse query parameters
        String query = request.getParameter("q");
        if (StringUtils.isEmpty(query)) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required parameter: q (search query)");
            return;
        }

        // Parse pagination parameters
        int page = parseIntParam(request, "page", 1);
        int pageSize = parseIntParam(request, "pageSize", 50);

        // Parse context parameters
        int contextLines = parseIntParam(request, "contextLines", DEFAULT_CONTEXT_LINES);
        if (contextLines < 0) {
            contextLines = 0;
        }
        if (contextLines > MAX_CONTEXT_LINES) {
            contextLines = MAX_CONTEXT_LINES;
        }

        // Parse line numbering format: "verbose" (default) or "none"
        String lineNumbering = request.getParameter("lineNumbering");
        boolean verboseLineNumbers = !"none".equalsIgnoreCase(lineNumbering);

        // Limit page size to prevent abuse
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageSize < 1) {
            pageSize = 50;
        }
        if (page < 1) {
            page = 1;
        }

        // Get requested repositories
        String reposParam = request.getParameter("repositories");
        boolean allRepos = "true".equalsIgnoreCase(request.getParameter("allRepos"));

        // Get list of repositories the user can access
        List<String> availableRepositories = new ArrayList<String>();
        for (RepositoryModel model : gitblit.getRepositoryModels(user)) {
            if (model.hasCommits && !ArrayUtils.isEmpty(model.indexedBranches)) {
                availableRepositories.add(model.name);
            }
        }

        // Determine which repositories to search
        List<String> searchRepositories = new ArrayList<String>();
        if (allRepos || StringUtils.isEmpty(reposParam)) {
            // Search all accessible repositories
            searchRepositories.addAll(availableRepositories);
        } else {
            // Parse comma-separated repository list and filter to accessible ones
            List<String> requestedRepos = Arrays.asList(reposParam.split(","));
            for (String repo : requestedRepos) {
                String trimmed = repo.trim();
                if (availableRepositories.contains(trimmed)) {
                    searchRepositories.add(trimmed);
                }
            }
        }

        if (searchRepositories.isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "No accessible indexed repositories found");
            return;
        }

        // Execute search
        log.info("Search API: user={}, query='{}', repos={}, page={}, pageSize={}, contextLines={}",
                user.username, query, searchRepositories.size(), page, pageSize, contextLines);

        List<SearchResult> results = gitblit.search(query, page, pageSize, searchRepositories);

        // Build response with context
        SearchResponse searchResponse = new SearchResponse();
        searchResponse.query = query;
        searchResponse.page = page;
        searchResponse.pageSize = pageSize;
        searchResponse.contextLines = contextLines;
        searchResponse.totalHits = results.isEmpty() ? 0 : results.get(0).totalHits;
        searchResponse.results = new ArrayList<SearchResultWithContext>();

        // Fetch context for each result
        for (SearchResult result : results) {
            SearchResultWithContext resultWithContext = new SearchResultWithContext(result);

            // Only fetch context for blob (file) results, not commits
            if (contextLines > 0 && result.type == SearchObjectType.blob && result.path != null) {
                try {
                    populateFileContext(gitblit, result, resultWithContext, contextLines, verboseLineNumbers);
                } catch (Exception e) {
                    log.warn("Failed to fetch context for {}:{}: {}",
                            result.repository, result.path, e.getMessage());
                    resultWithContext.context = null;
                    resultWithContext.contextError = e.getMessage();
                }
            }

            searchResponse.results.add(resultWithContext);
        }

        // Send JSON response
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);

        PrintWriter writer = response.getWriter();
        writer.write(gson.toJson(searchResponse));
        writer.flush();
    }

    private void populateFileContext(IGitblit gitblit, SearchResult result,
            SearchResultWithContext resultWithContext, int contextLines, boolean verboseLineNumbers) {
        Repository repository = null;
        try {
            repository = gitblit.getRepository(result.repository);
            if (repository == null) {
                return;
            }

            // Get the commit
            RevCommit commit = JGitUtils.getCommit(repository, result.commitId);
            if (commit == null) {
                return;
            }

            // Get the file content at this commit
            String content = JGitUtils.getStringContent(repository, commit.getTree(), result.path);
            if (content == null) {
                return;
            }

            // Split into lines
            String[] lines = content.split("\n", -1);

            // Try to find the match location using the fragment
            int matchLine = findMatchLine(lines, result.fragment);

            // Calculate context range
            int startLine = Math.max(0, matchLine - contextLines);
            int endLine = Math.min(lines.length, matchLine + contextLines + 1);

            // Populate line metadata (1-indexed for human readability)
            resultWithContext.matchLine = matchLine + 1;
            resultWithContext.contextStartLine = startLine + 1;
            resultWithContext.contextEndLine = endLine;  // already exclusive, so just convert to 1-indexed

            // Build context
            StringBuilder contextBuilder = new StringBuilder();
            for (int i = startLine; i < endLine; i++) {
                if (verboseLineNumbers) {
                    // Verbose format: marker + line_number | content
                    String marker = (i == matchLine) ? ">>> " : "    ";
                    contextBuilder.append(String.format("%s%5d | %s\n", marker, i + 1, lines[i]));
                } else {
                    // None format: just raw content
                    contextBuilder.append(lines[i]).append("\n");
                }
            }

            resultWithContext.context = contextBuilder.toString();

        } finally {
            if (repository != null) {
                repository.close();
            }
        }
    }

    private int findMatchLine(String[] lines, String fragment) {
        if (fragment == null || fragment.isEmpty()) {
            return 0;
        }

        // Clean up fragment - remove HTML highlighting tags
        String cleanFragment = fragment
            .replaceAll("<[^>]+>", "")  // Remove HTML tags
            .trim();

        // Try to find a line containing the fragment
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(cleanFragment)) {
                return i;
            }
        }

        // If fragment contains multiple words, try matching each word
        String[] words = cleanFragment.split("\\s+");
        for (String word : words) {
            if (word.length() > 3) {  // Skip short words
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].toLowerCase().contains(word.toLowerCase())) {
                        return i;
                    }
                }
            }
        }

        // Default to start of file
        return 0;
    }

    private int parseIntParam(HttpServletRequest request, String name, int defaultValue) {
        String value = request.getParameter(name);
        if (StringUtils.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void sendError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(status);

        ErrorResponse error = new ErrorResponse();
        error.error = message;
        error.status = status;

        PrintWriter writer = response.getWriter();
        writer.write(gson.toJson(error));
        writer.flush();
    }

    // Response wrapper classes for JSON serialization
    public static class SearchResponse {
        public String query;
        public int page;
        public int pageSize;
        public int contextLines;
        public int totalHits;
        public List<SearchResultWithContext> results;
    }

    public static class SearchResultWithContext {
        // Original SearchResult fields
        public int hitId;
        public int totalHits;
        public float score;
        public String date;
        public String author;
        public String committer;
        public String summary;
        public String fragment;
        public String repository;
        public String branch;
        public String commitId;
        public String path;
        public List<String> tags;
        public String type;

        // Additional context fields
        public String context;
        public String contextError;
        public Integer matchLine;      // 1-indexed line where match was found
        public Integer contextStartLine;  // 1-indexed start of context
        public Integer contextEndLine;    // 1-indexed end of context (exclusive)

        public SearchResultWithContext(SearchResult sr) {
            this.hitId = sr.hitId;
            this.totalHits = sr.totalHits;
            this.score = sr.score;
            this.date = sr.date != null ? sr.date.toString() : null;
            this.author = sr.author;
            this.committer = sr.committer;
            this.summary = sr.summary;
            this.fragment = sr.fragment;
            this.repository = sr.repository;
            this.branch = sr.branch;
            this.commitId = sr.commitId;
            this.path = sr.path;
            this.tags = sr.tags;
            this.type = sr.type != null ? sr.type.name() : null;
        }
    }

    public static class ErrorResponse {
        public String error;
        public int status;
    }
}
