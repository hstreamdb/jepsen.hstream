#!/usr/bin/bash
docker build -t jepsen-hornbill:base \
       --build-arg "USE_CHINA_MIRROR=false" \
       --build-arg "HORNBILL_IMAGE=ghcr.io/hstreamdb/hornbill:v1.0.0-m0" \
       ./docker/base-hornbill
