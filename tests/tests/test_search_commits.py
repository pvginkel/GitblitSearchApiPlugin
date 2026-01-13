"""
Tests for GET /api/mcp-server/search/commits endpoint.
"""
import pytest


class TestCommitSearchEndpoint:
    """Tests for the /search/commits endpoint."""

    @pytest.fixture
    def indexed_repo(self, api_client):
        """Get a repository that is indexed for searching."""
        repos = api_client.repos().json()
        for repo in repos["repositories"]:
            if repo["hasCommits"]:
                return repo["name"]
        pytest.skip("No indexed repository available")

    def test_search_by_message_terms(self, api_client, indexed_repo):
        """Test searching commits by message terms."""
        response = api_client.search_commits(
            repos=indexed_repo,
            message_terms=["fix", "add", "update"]
        )
        assert response.status_code == 200

        data = response.json()
        assert "query" in data
        assert "totalCount" in data
        assert "limitHit" in data
        assert "commits" in data
        assert isinstance(data["commits"], list)

    def test_commit_result_structure(self, api_client, indexed_repo):
        """Test that commit results have correct structure."""
        response = api_client.search_commits(
            repos=indexed_repo,
            message_terms=["initial", "first", "add"]
        )
        assert response.status_code == 200

        data = response.json()
        if not data["commits"]:
            pytest.skip("No commit search results")

        commit = data["commits"][0]
        assert "repository" in commit
        assert "commit" in commit  # SHA
        assert "author" in commit
        assert "date" in commit
        assert "title" in commit
        assert "message" in commit

    def test_search_by_author(self, api_client, indexed_repo):
        """Test searching commits by author."""
        # First find an author name from existing commits
        initial_search = api_client.search_commits(
            repos=indexed_repo,
            message_terms=["a"]  # Broad search to get some results
        )

        if not initial_search.json()["commits"]:
            pytest.skip("No commits found to get author name")

        author = initial_search.json()["commits"][0]["author"]
        # Extract just the name part if email is included
        author_name = author.split("<")[0].strip() if "<" in author else author

        response = api_client.search_commits(
            repos=indexed_repo,
            authors=[author_name]
        )
        assert response.status_code == 200

    def test_search_with_count_limit(self, api_client, indexed_repo):
        """Test commit search result count limit."""
        response = api_client.search_commits(
            repos=indexed_repo,
            message_terms=["a"],  # Broad search
            count=2
        )
        assert response.status_code == 200

        data = response.json()
        assert len(data["commits"]) <= 2

    def test_missing_repos_parameter(self, api_client):
        """Test error when repos parameter is missing."""
        response = api_client.get("search/commits", {"messageTerms": "test"})
        assert response.status_code == 400

    def test_missing_search_criteria(self, api_client, indexed_repo):
        """Test error when neither messageTerms nor authors is provided."""
        response = api_client.get("search/commits", {"repos": indexed_repo})
        assert response.status_code == 400

        data = response.json()
        assert "error" in data
        assert "messageTerms" in data["error"] or "authors" in data["error"]

    def test_search_query_includes_type_commit(self, api_client, indexed_repo):
        """Test that the executed query includes type:commit."""
        response = api_client.search_commits(
            repos=indexed_repo,
            message_terms=["test"]
        )
        assert response.status_code == 200

        data = response.json()
        assert "type:commit" in data["query"]

    def test_commit_title_is_first_line(self, api_client, indexed_repo):
        """Test that commit title is the first line of the message."""
        response = api_client.search_commits(
            repos=indexed_repo,
            message_terms=["a"]
        )

        data = response.json()
        if not data["commits"]:
            pytest.skip("No commits found")

        for commit in data["commits"]:
            if commit["message"] and "\n" in commit["message"]:
                # Title should be first line of message
                first_line = commit["message"].split("\n")[0]
                assert commit["title"] == first_line
                break

    def test_search_multiple_repos(self, api_client):
        """Test searching commits across multiple repositories."""
        repos = api_client.repos().json()
        repo_names = [r["name"] for r in repos["repositories"] if r["hasCommits"]][:2]

        if len(repo_names) < 2:
            pytest.skip("Need at least 2 repositories with commits")

        response = api_client.search_commits(
            repos=repo_names,
            message_terms=["initial", "first"]
        )
        assert response.status_code == 200
