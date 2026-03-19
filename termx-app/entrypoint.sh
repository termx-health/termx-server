#!/usr/bin/env bash

if [[ ! -z "$(ls -A /certs)" ]]; then
  for f in /certs/* ; do
      echo "importing cert $f"
      keytool -import -cacerts -storepass changeit -noprompt -alias $(basename "$f") -file $f
  done
fi

exec "$@"
