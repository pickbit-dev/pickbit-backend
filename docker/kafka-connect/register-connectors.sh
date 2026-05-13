#!/usr/bin/env bash

set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:18088}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONNECTORS_DIR="${SCRIPT_DIR}/connectors"

wait_for_connect() {
  until curl -fsS "${CONNECT_URL}/connectors" >/dev/null; do
    printf 'Waiting for Kafka Connect at %s...\n' "${CONNECT_URL}"
    sleep 2
  done
}

register_connector() {
  local file="$1"
  local name

  name="$(basename "${file}" .json)"

  if curl -fsS "${CONNECT_URL}/connectors/${name}" >/dev/null; then
    printf 'Updating connector: %s\n' "${name}"
    curl -fsS -X PUT \
      -H 'Content-Type: application/json' \
      --data "$(python3 -c "import json,sys; print(json.dumps(json.load(open(sys.argv[1]))['config']))" "${file}")" \
      "${CONNECT_URL}/connectors/${name}/config" >/dev/null
  else
    printf 'Creating connector: %s\n' "${name}"
    curl -fsS -X POST \
      -H 'Content-Type: application/json' \
      --data "@${file}" \
      "${CONNECT_URL}/connectors" >/dev/null
  fi
}

wait_for_connect

for connector in "${CONNECTORS_DIR}"/*.json; do
  register_connector "${connector}"
done

printf 'Kafka Connect connectors registered.\n'
