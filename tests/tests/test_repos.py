"""
Tests for GET /api/.mcp-internal/repos endpoint.
"""
import pytest


class TestReposEndpoint:
    """Tests for the /repos endpoint."""

    def test_list_all_repos(self, api_client):
        """Test listing all repositories."""
        response = api_client.repos()
        assert response.status_code == 200

        data = response.json()
        assert "repositories" in data
        assert "totalCount" in data
        assert "limitHit" in data
        assert isinstance(data["repositories"], list)

    def test_list_repos_with_query(self, api_client):
        """Test filtering repositories by name."""
        # First get all repos to find a name to search for
        all_repos = api_client.repos().json()
        if not all_repos["repositories"]:
            pytest.skip("No repositories available for testing")

        # Get first repo name and search for part of it
        first_repo = all_repos["repositories"][0]["name"]
        search_term = first_repo[:3]  # First 3 characters

        response = api_client.repos(query=search_term)
        assert response.status_code == 200

        data = response.json()
        # All results should contain the search term (case-insensitive)
        for repo in data["repositories"]:
            assert search_term.lower() in repo["name"].lower()

    def test_list_repos_with_limit(self, api_client):
        """Test pagination limit."""
        response = api_client.repos(limit=2)
        assert response.status_code == 200

        data = response.json()
        assert len(data["repositories"]) <= 2

    def test_list_repos_pagination(self, api_client):
        """Test offset-based pagination."""
        # Get first page
        first_page = api_client.repos(limit=1).json()
        if first_page["totalCount"] < 2:
            pytest.skip("Not enough repositories for pagination test")

        # Get second page using offset
        second_page = api_client.repos(limit=1, offset=1).json()

        assert second_page["repositories"]
        # Second page should have different repos
        if first_page["repositories"] and second_page["repositories"]:
            assert first_page["repositories"][0]["name"] != second_page["repositories"][0]["name"]

    def test_repo_info_fields(self, api_client):
        """Test that repository info contains expected fields."""
        response = api_client.repos(limit=1)
        assert response.status_code == 200

        data = response.json()
        if not data["repositories"]:
            pytest.skip("No repositories available")

        repo = data["repositories"][0]
        assert "name" in repo
        assert "description" in repo
        assert "hasCommits" in repo
        # lastChange may be null for empty repos
        assert "lastChange" in repo

    def test_offset_skips_results(self, api_client):
        """Test that offset correctly skips results."""
        # Get all repos first
        all_repos = api_client.repos(limit=100).json()
        if all_repos["totalCount"] < 3:
            pytest.skip("Not enough repositories for offset test")

        # Get first 3 without offset
        first_three = api_client.repos(limit=3, offset=0).json()
        # Get repos starting from offset 2
        offset_two = api_client.repos(limit=3, offset=2).json()

        # First repo at offset 2 should be third repo from first page
        assert offset_two["repositories"][0]["name"] == first_three["repositories"][2]["name"]

    def test_limit_hit_correct(self, api_client):
        """Test that limitHit is set correctly."""
        # Get total count
        all_repos = api_client.repos(limit=100).json()
        total = all_repos["totalCount"]

        if total <= 1:
            pytest.skip("Not enough repositories for limitHit test")

        # Request fewer than total
        limited = api_client.repos(limit=1).json()
        assert limited["limitHit"] is True

        # Request all
        all_results = api_client.repos(limit=total).json()
        assert all_results["limitHit"] is False

    def test_offset_beyond_total(self, api_client):
        """Test that offset beyond total returns empty results."""
        all_repos = api_client.repos().json()
        total = all_repos["totalCount"]

        # Request with offset beyond total
        response = api_client.repos(offset=total + 100)
        assert response.status_code == 200

        data = response.json()
        assert data["repositories"] == []
        assert data["totalCount"] == total
        assert data["limitHit"] is False
