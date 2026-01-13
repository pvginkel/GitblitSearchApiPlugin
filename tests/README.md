# MCP Support Plugin Tests

Test suite for the Gitblit MCP Support Plugin REST API.

## Setup

```bash
cd tests
poetry install
```

## Running Tests

```bash
# Run all tests
poetry run pytest

# Run with verbose output
poetry run pytest -v

# Run specific test file
poetry run pytest tests/test_repos.py

# Run specific test
poetry run pytest tests/test_repos.py::TestReposEndpoint::test_list_all_repos
```

## Configuration

The test suite connects to a Gitblit server. Configure the URL via environment variable:

```bash
export GITBLIT_URL=http://10.1.2.3:8080
poetry run pytest
```

Or set it in `pyproject.toml` under `[tool.pytest.ini_options]`.

## Test Coverage

- **test_repos.py** - Tests for `GET /api/mcp-server/repos`
- **test_files.py** - Tests for `GET /api/mcp-server/files`
- **test_file.py** - Tests for `GET /api/mcp-server/file`
- **test_search_files.py** - Tests for `GET /api/mcp-server/search/files`
- **test_search_commits.py** - Tests for `GET /api/mcp-server/search/commits`
