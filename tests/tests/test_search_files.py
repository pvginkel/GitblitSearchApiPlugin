"""
Tests for GET /api/mcp-server/search/files endpoint.
"""
import pytest


class TestFileSearchEndpoint:
    """Tests for the /search/files endpoint."""

    @pytest.fixture
    def indexed_repo(self, api_client):
        """Get a repository that is indexed for searching."""
        repos = api_client.repos().json()
        for repo in repos["repositories"]:
            if repo["hasCommits"]:
                return repo["name"]
        pytest.skip("No indexed repository available")

    def test_basic_search(self, api_client, indexed_repo):
        """Test basic file content search."""
        # Search for a common term that should exist
        response = api_client.search_files(query="public", repos=indexed_repo)
        assert response.status_code == 200

        data = response.json()
        assert "query" in data
        assert "totalCount" in data
        assert "limitHit" in data
        assert "results" in data
        assert isinstance(data["results"], list)

    def test_search_result_structure(self, api_client, indexed_repo):
        """Test that search results have correct structure."""
        response = api_client.search_files(query="class", repos=indexed_repo)
        assert response.status_code == 200

        data = response.json()
        if not data["results"]:
            pytest.skip("No search results to validate structure")

        result = data["results"][0]
        assert "repository" in result
        assert "path" in result
        assert "chunks" in result
        assert isinstance(result["chunks"], list)

    def test_search_chunk_structure(self, api_client, indexed_repo):
        """Test that search result chunks have correct structure."""
        response = api_client.search_files(query="import", repos=indexed_repo)
        assert response.status_code == 200

        data = response.json()
        if not data["results"]:
            pytest.skip("No search results")

        for result in data["results"]:
            if result["chunks"]:
                chunk = result["chunks"][0]
                assert "startLine" in chunk
                assert "endLine" in chunk
                assert "content" in chunk
                # Line numbers should be positive integers
                assert chunk["startLine"] > 0
                assert chunk["endLine"] >= chunk["startLine"]
                break
        else:
            pytest.skip("No chunks in search results")

    def test_search_with_path_pattern(self, api_client, indexed_repo):
        """Test search with path pattern filter."""
        response = api_client.search_files(
            query="public",
            repos=indexed_repo,
            path_pattern="*.java"
        )
        assert response.status_code == 200

        data = response.json()
        # All results should be Java files
        for result in data["results"]:
            assert result["path"].endswith(".java")

    def test_search_with_count_limit(self, api_client, indexed_repo):
        """Test search result count limit."""
        response = api_client.search_files(query="the", repos=indexed_repo, count=3)
        assert response.status_code == 200

        data = response.json()
        assert len(data["results"]) <= 3

    def test_search_multiple_repos(self, api_client):
        """Test searching across multiple repositories."""
        repos = api_client.repos().json()
        if len(repos["repositories"]) < 2:
            pytest.skip("Need at least 2 repositories for this test")

        repo_names = [r["name"] for r in repos["repositories"][:2] if r["hasCommits"]]
        if len(repo_names) < 2:
            pytest.skip("Need at least 2 repositories with commits")

        response = api_client.search_files(query="public", repos=repo_names)
        assert response.status_code == 200

    def test_missing_query_parameter(self, api_client):
        """Test error when query parameter is missing."""
        response = api_client.get("search/files")
        assert response.status_code == 400

    def test_search_query_in_response(self, api_client, indexed_repo):
        """Test that the executed query is included in response."""
        response = api_client.search_files(query="test", repos=indexed_repo)
        assert response.status_code == 200

        data = response.json()
        # Query should include type:blob
        assert "type:blob" in data["query"]
        assert "test" in data["query"]
