#!/usr/bin/bash
docker compose --file ./docker/docker-compose-hornbill.yml \
               --compatibility \
               -p jepsen-hornbill \
               build \
               --build-arg USE_CHINA_MIRROR=false \
               --build-arg arg_http_proxy="" \
               --build-arg arg_https_proxy="" \
               --build-arg BASE_IMAGE=jepsen-hornbill:base
