#!/usr/bin/bash
docker compose --file ./docker/docker-compose-flowmq.yml \
               --compatibility \
               -p jepsen-flowmq \
               up \
               --renew-anon-volumes \
               --exit-code-from control

# lein run -- test --db hstream --no-txn --no-server-idempotence --nemesis none --workload queue --time-limit 60 --sub-via subscribe
