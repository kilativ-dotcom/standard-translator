#!/usr/bin/env bash
set -eo pipefail
ROOT_PATH="$(cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd)/.."
cd "$ROOT_PATH"
java -jar "$ROOT_PATH/out/artifacts/standard_translator_jar/standard-translator.jar" "$@"
