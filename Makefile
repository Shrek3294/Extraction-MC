PYTHON ?= python3
VENV ?= .venv
PIP := $(VENV)/bin/pip
PYTEST := $(VENV)/bin/pytest
RUFF := $(VENV)/bin/ruff

.PHONY: install format lint test ci clean

install: $(PIP)

$(PIP): requirements-dev.txt
	$(PYTHON) -m venv $(VENV)
	$(PIP) install --upgrade pip
	$(PIP) install -r requirements-dev.txt

default: ci

format: install
	$(RUFF) format .

lint: install
	$(RUFF) check .

test: install
	$(PYTEST)

ci: lint test

clean:
	rm -rf $(VENV) .pytest_cache __pycache__ */__pycache__
