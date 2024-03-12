#!/usr/bin/bash
docker-compose --file ./docker/docker-compose.yml \
               --compatibility \
               -p jepsen \
               build \
               --build-arg USE_CHINA_MIRROR=false \
               --build-arg arg_http_proxy="" \
               --build-arg arg_https_proxy="" \
               --build-arg BASE_IMAGE=jepsen-hstream:base \
               --build-arg HSTREAM_IMAGE=hstreamdb/hstream:latest
