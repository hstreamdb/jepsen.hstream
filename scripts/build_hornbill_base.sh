#!/usr/bin/bash
docker build -t jepsen-hornbill:base \
       --build-arg "USE_CHINA_MIRROR=false" \
       --build-arg "HORNBILL_IMAGE=ghcr.io/hstreamdb/hornbill:latest" \
       ./docker/base-hornbill
