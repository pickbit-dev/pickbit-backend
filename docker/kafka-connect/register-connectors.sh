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
  local status

  name="$(basename "${file}" .json)"

  status="$(curl -s -o /dev/null -w "%{http_code}" "${CONNECT_URL}/connectors/${name}")"

  if [[ "${status}" == "200" ]]; then
    printf 'Updating connector: %s\n' "${name}"
    curl -fsS -X PUT \
      -H 'Content-Type: application/json' \
      --data "$(python3 -c "import json,sys; print(json.dumps(json.load(open(sys.argv[1]))['config']))" "${file}")" \
      "${CONNECT_URL}/connectors/${name}/config" >/dev/null
  elif [[ "${status}" == "404" ]]; then
    printf 'Creating connector: %s\n' "${name}"
    curl -fsS -X POST \
      -H 'Content-Type: application/json' \
      --data "@${file}" \
      "${CONNECT_URL}/connectors" >/dev/null
  else
    printf 'Failed to inspect connector %s. HTTP status=%s\n' "${name}" "${status}" >&2
    return 1
  fi

  printf 'Restarting connector: %s\n' "${name}"
  curl -fsS -X POST "${CONNECT_URL}/connectors/${name}/restart?includeTasks=true" >/dev/null
}

print_connector_status() {
  local name="$1"
  printf '\nStatus for connector: %s\n' "${name}"
  curl -fsS "${CONNECT_URL}/connectors/${name}/status"
  printf '\n'
}

wait_for_connect

for connector in "${CONNECTORS_DIR}"/*.json; do
  register_connector "${connector}"
done

sleep 3

for connector in "${CONNECTORS_DIR}"/*.json; do
  print_connector_status "$(basename "${connector}" .json)"
done

printf 'Kafka Connect connectors registered.\n'
