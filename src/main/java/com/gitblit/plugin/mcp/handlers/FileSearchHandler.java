/*
 * Gitblit MCP Support Plugin
 */
package com.gitblit.plugin.mcp.handlers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.gitblit.Constants.SearchObjectType;
import com.gitblit.manager.IGitblit;
import com.gitblit.models.RepositoryModel;
import com.gitblit.models.SearchResult;
import com.gitblit.models.UserModel;
import com.gitblit.plugin.mcp.model.FileSearchResponse;
import com.gitblit.plugin.mcp.util.ResponseWriter;
import com.gitblit.utils.ArrayUtils;
import com.gitblit.utils.JGitUtils;
import com.gitblit.utils.StringUtils;

/**
 * Handler for GET /api/mcp-server/search/files
 * Searches file contents using Lucene index.
 */
public class FileSearchHandler implements RequestHandler {

    private static final Logger log = LoggerFactory.getLogger(FileSearchHandler.class);

    private static final int DEFAULT_COUNT = 25;
    private static final int MAX_COUNT = 100;
    private static final int CONTEXT_LINES = 100;  // Lines of context around match

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       IGitblit gitblit, UserModel user) throws IOException {

        // Parse required parameters
        String query = request.getParameter("query");
        if (StringUtils.isEmpty(query)) {
            ResponseWriter.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                "Missing required parameter: query");
            return;
        }

        // Parse optional parameters
        String reposParam = request.getParameter("repos");
        String pathPattern = request.getParameter("pathPattern");
        String branch = request.getParameter("branch");
        int count = parseIntParam(request, "count", DEFAULT_COUNT);

        // Cap count
        if (count < 1) count = DEFAULT_COUNT;
        if (count > MAX_COUNT) count = MAX_COUNT;

        // Build Lucene query
        StringBuilder luceneQuery = new StringBuilder();
        luceneQuery.append("type:blob");

        // Add the user's query
        luceneQuery.append(" AND (").append(query).append(")");

        // Add path pattern if provided
        if (!StringUtils.isEmpty(pathPattern)) {
            luceneQuery.append(" AND path:").append(pathPattern);
        }

        // Add branch filter if provided
        if (!StringUtils.isEmpty(branch)) {
            luceneQuery.append(" AND branch:\"").append(branch).append("\"");
        }

        // Determine repositories to search
        List<String> searchRepos = getSearchRepositories(gitblit, user, reposParam);

        if (searchRepos.isEmpty()) {
            ResponseWriter.writeError(response, HttpServletResponse.SC_BAD_REQUEST,
                "No accessible indexed repositories found");
            return;
        }

        // Execute search
        String finalQuery = luceneQuery.toString();
        log.info("File search: user={}, query='{}', repos={}", user.username, finalQuery, searchRepos.size());

        List<SearchResult> results = gitblit.search(finalQuery, 1, count, searchRepos);

        // Build response
        FileSearchResponse searchResponse = new FileSearchResponse();
        searchResponse.query = finalQuery;
        searchResponse.totalCount = results.isEmpty() ? 0 : results.get(0).totalHits;
        searchResponse.limitHit = searchResponse.totalCount > count;
        searchResponse.results = new ArrayList<>();

        // Process each result
        for (SearchResult sr : results) {
            // Only include blob results
            if (sr.type != SearchObjectType.blob) {
                continue;
            }

            FileSearchResponse.FileSearchResult fileResult = new FileSearchResponse.FileSearchResult();
            fileResult.repository = sr.repository;
            fileResult.path = sr.path;
            fileResult.branch = sr.branch;
            fileResult.commitId = sr.commitId;
            fileResult.chunks = new ArrayList<>();

            // Fetch context chunk
            try {
                FileSearchResponse.Chunk chunk = fetchChunk(gitblit, sr, CONTEXT_LINES);
                if (chunk != null) {
                    fileResult.chunks.add(chunk);
                }
            } catch (Exception e) {
                log.warn("Failed to fetch context for {}:{}: {}", sr.repository, sr.path, e.getMessage());
            }

            searchResponse.results.add(fileResult);
        }

        ResponseWriter.writeJson(response, searchResponse);
    }

    /**
     * Get list of repositories to search.
     */
    private List<String> getSearchRepositories(IGitblit gitblit, UserModel user, String reposParam) {
        // Get all accessible repositories with indexing enabled
        List<String> available = new ArrayList<>();
        for (RepositoryModel model : gitblit.getRepositoryModels(user)) {
            if (model.hasCommits && !ArrayUtils.isEmpty(model.indexedBranches)) {
                available.add(model.name);
            }
        }

        if (StringUtils.isEmpty(reposParam)) {
            return available;
        }

        // Filter to requested repositories
        List<String> requested = Arrays.asList(reposParam.split(","));
        List<String> result = new ArrayList<>();
        for (String repo : requested) {
            String trimmed = repo.trim();
            if (available.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Fetch a chunk of context around the match.
     */
    private FileSearchResponse.Chunk fetchChunk(IGitblit gitblit, SearchResult sr, int contextLines) {
        Repository repository = null;
        try {
            repository = gitblit.getRepository(sr.repository);
            if (repository == null) {
                return null;
            }

            RevCommit commit = JGitUtils.getCommit(repository, sr.commitId);
            if (commit == null) {
                return null;
            }

            String content = JGitUtils.getStringContent(repository, commit.getTree(), sr.path);
            if (content == null) {
                return null;
            }

            String[] lines = content.split("\n", -1);

            // Find the match line using the fragment
            int matchLine = findMatchLine(lines, sr.fragment);

            // Calculate context range
            int halfContext = contextLines / 2;
            int startLine = Math.max(0, matchLine - halfContext);
            int endLine = Math.min(lines.length, matchLine + halfContext + 1);

            // Build chunk content with line numbers
            StringBuilder chunkContent = new StringBuilder();
            for (int i = startLine; i < endLine; i++) {
                chunkContent.append(i + 1).append(": ").append(lines[i]).append("\n");
            }

            return new FileSearchResponse.Chunk(startLine + 1, endLine, chunkContent.toString());

        } finally {
            if (repository != null) {
                repository.close();
            }
        }
    }

    /**
     * Find the line number containing the match.
     */
    private int findMatchLine(String[] lines, String fragment) {
        if (fragment == null || fragment.isEmpty()) {
            return 0;
        }

        // Clean up fragment - remove HTML highlighting tags
        String cleanFragment = fragment.replaceAll("<[^>]+>", "").trim();

        // Try exact match first
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(cleanFragment)) {
                return i;
            }
        }

        // Try word matching
        String[] words = cleanFragment.split("\\s+");
        for (String word : words) {
            if (word.length() > 3) {
                for (int i = 0; i < lines.length; i++) {
                    if (lines[i].toLowerCase().contains(word.toLowerCase())) {
                        return i;
                    }
                }
            }
        }

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
}
