#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ROOT_DIR}/config/dev.env"
EXAMPLE_FILE="${ROOT_DIR}/config/dev.env.example"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing required env file: ${ENV_FILE}"
  echo "Create it from template:"
  echo "  cp ${EXAMPLE_FILE} ${ENV_FILE}"
  exit 1
fi

echo "Loading environment from ${ENV_FILE}"
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

require_aad_env() {
  local missing=()
  local placeholders=()
  local vars=(AAD_TENANT_ID AAD_CLIENT_ID AAD_CLIENT_SECRET AAD_REDIRECT_URI)

  for var_name in "${vars[@]}"; do
    local value="${!var_name:-}"
    if [[ -z "${value}" ]]; then
      missing+=("${var_name}")
    elif [[ "${value}" == "__FILL_ME__" ]]; then
      placeholders+=("${var_name}")
    fi
  done

  if (( ${#missing[@]} > 0 || ${#placeholders[@]} > 0 )); then
    echo "AAD configuration is incomplete."
    if (( ${#missing[@]} > 0 )); then
      echo "Missing vars: ${missing[*]}"
    fi
    if (( ${#placeholders[@]} > 0 )); then
      echo "Placeholder vars (still __FILL_ME__): ${placeholders[*]}"
    fi
    echo "Update ${ENV_FILE} with real AAD values before running."
    exit 1
  fi
}

require_aad_env

BASE_PROFILES="${SPRING_PROFILES_ACTIVE:-aad}"
RUN_PROFILES="${BASE_PROFILES},dev"

cd "${ROOT_DIR}"
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles="${RUN_PROFILES}"
