#!/usr/bin/bash
docker build -t jepsen-flowmq:base \
       --build-arg "USE_CHINA_MIRROR=false" \
       ./docker/base-flowmq
