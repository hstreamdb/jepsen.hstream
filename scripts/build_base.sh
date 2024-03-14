#!/usr/bin/bash
docker build -t jepsen-hstream:base \
       --build-arg "USE_CHINA_MIRROR=false" \
       ./docker/base/
