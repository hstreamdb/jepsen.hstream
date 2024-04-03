#!/usr/bin/bash
docker compose --file ./docker/docker-compose.yml \
               --compatibility \
               -p jepsen \
               up \
               --renew-anon-volumes \
               --exit-code-from control
