#!/usr/bin/bash
docker compose --file ./docker/docker-compose-hornbill.yml \
               --compatibility \
               -p jepsen-hornbill \
               up \
               --renew-anon-volumes \
               --exit-code-from control

# lein run -- test --db hstream --no-txn --no-server-idempotence --nemesis none --workload queue --time-limit 60 --sub-via subscribe
