.PHONY: build test integration-test clean format run infra-up infra-down infra-clean infra-status infra-logs docker-build up down help

build: ## Compile + Spotless + unit + integration + ArchUnit
	./gradlew build

test: ## Run unit tests only
	./gradlew test

integration-test: ## Run integration tests (requires Docker for Testcontainers)
	./gradlew integrationTest

clean: ## Remove all build artifacts
	./gradlew clean

format: ## Auto-format code with Spotless
	./gradlew spotlessApply

run: ## Run the indexer application
	./gradlew :prism:run

infra-up: ## Start infrastructure (PostgreSQL, Prometheus, Grafana)
	docker compose up -d postgres prometheus grafana

infra-down: ## Stop infrastructure
	docker compose down

infra-clean: ## Stop infrastructure and remove volumes
	docker compose down -v

infra-status: ## Show infrastructure container status
	docker compose ps

infra-logs: ## Tail infrastructure logs
	docker compose logs -f postgres prometheus grafana

docker-build: ## Build Docker image via Jib
	./gradlew :prism:jibDockerBuild

up: ## Start all services (infra + app)
	docker compose up -d

down: ## Stop all services
	docker compose down

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'
