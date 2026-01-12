# Gitblit Search API Plugin

A Gitblit plugin that exposes the Lucene search functionality via a JSON API endpoint.

## Building

Requires JDK 8 and Maven.

```bash
cd searchplugin
mvn clean package
```

The plugin JAR will be created at `target/searchplugin-1.0.0.jar`.

## Installation

Copy the JAR to your Gitblit plugins directory:

```bash
cp target/searchplugin-1.0.0.jar /path/to/gitblit/data/plugins/
```

For Docker:
```bash
docker cp target/searchplugin-1.0.0.jar gitblit:/opt/gitblit-data/plugins/
```

Restart Gitblit to load the plugin.

## API Usage

### Endpoint

```
GET /api/search
```

### Query Parameters

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `q` | Yes | - | Lucene query string |
| `page` | No | 1 | Page number (1-indexed) |
| `pageSize` | No | 50 | Results per page (max 100) |
| `repositories` | No | all | Comma-separated list of repository names |
| `allRepos` | No | false | Set to `true` to search all accessible repos |

### Authentication

The API supports the same authentication methods as Gitblit:
- HTTP Basic Authentication
- Session cookies (if logged in via web UI)

Anonymous access works if your repositories allow it.

### Example Requests

Search all repositories:
```bash
curl -u username:password "http://localhost:8080/api/search?q=TODO&allRepos=true"
```

Search specific repositories:
```bash
curl -u username:password "http://localhost:8080/api/search?q=function&repositories=repo1,repo2"
```

Search for commits by author:
```bash
curl -u username:password "http://localhost:8080/api/search?q=author:john%20AND%20type:commit&allRepos=true"
```

Search for file contents:
```bash
curl -u username:password "http://localhost:8080/api/search?q=type:blob%20AND%20path:*.java&allRepos=true"
```

### Example Response

```json
{
  "query": "TODO",
  "page": 1,
  "pageSize": 50,
  "totalHits": 42,
  "results": [
    {
      "hitId": 1,
      "totalHits": 42,
      "score": 2.5,
      "date": "2024-01-15T10:30:00Z",
      "author": "John Doe",
      "committer": "John Doe",
      "summary": "Fix TODO item in parser",
      "fragment": "...found <em>TODO</em> in the code...",
      "repository": "myproject",
      "branch": "refs/heads/main",
      "commitId": "abc123def456...",
      "path": null,
      "tags": ["v1.0"],
      "type": "commit"
    },
    {
      "hitId": 2,
      "totalHits": 42,
      "score": 1.8,
      "date": "2024-01-10T08:00:00Z",
      "author": "Jane Smith",
      "committer": "Jane Smith",
      "summary": "Add utility functions",
      "fragment": "// <em>TODO</em>: optimize this later",
      "repository": "myproject",
      "branch": "refs/heads/main",
      "commitId": "def789abc012...",
      "path": "src/utils.java",
      "tags": null,
      "type": "blob"
    }
  ]
}
```

### Lucene Query Syntax

The `q` parameter accepts full Lucene query syntax:

- `type:commit` - Search only commits
- `type:blob` - Search only file contents
- `author:name` - Search by author
- `committer:name` - Search by committer
- `path:*.java` - Search files matching path pattern
- `"exact phrase"` - Exact phrase matching
- `word1 AND word2` - Both terms required
- `word1 OR word2` - Either term
- `word*` - Wildcard

See [Lucene Query Parser Syntax](https://lucene.apache.org/core/5_5_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package_description) for full documentation.

## Error Responses

```json
{
  "error": "Missing required parameter: q (search query)",
  "status": 400
}
```

## CORS

The API includes CORS headers allowing access from any origin, suitable for browser-based MCP clients or web applications.
