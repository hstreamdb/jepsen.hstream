#!/usr/bin/bash
docker compose --file ./docker/docker-compose-flowmq.yml \
               --compatibility \
               -p jepsen-flowmq \
               build \
               --build-arg USE_CHINA_MIRROR=false \
               --build-arg arg_http_proxy="" \
               --build-arg arg_https_proxy="" \
               --build-arg FLOWMQ_IMAGE=${FLOWMQ_IMAGE:-ghcr.io/flowmq-io/flowmq:main} \
               --build-arg BASE_IMAGE=${BASE_IMAGE:-jepsen-flowmq:base}
