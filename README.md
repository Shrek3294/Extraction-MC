# Extraction-MC

This repository is standardized around a Docker-based Python 3.11 environment with pinned
dependencies in `requirements-dev.txt`. Build the image or run locally with the same
commands to avoid drift between cloud and local setups.

## Common workflow

1. Install dependencies (local):
   ```bash
   make install
   ```

2. Run formatting, linting, and tests consistently:
   ```bash
   make format
   make lint
   make test
   make ci   # lint + test
   ```

3. Run inside Docker for a fully reproducible environment:
   ```bash
   docker build -t extraction-mc .
   docker run --rm -it -v "$PWD":"/app" extraction-mc make ci
   ```

The same Make targets are intended for both local development and CI so results stay in sync.
