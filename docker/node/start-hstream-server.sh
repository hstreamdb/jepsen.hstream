#!/bin/bash

SERVER_ID=$(shuf -i 1-4294967296 -n 1)
MY_IP=$(hostname -I | head -n1 | awk '{print $1;}')
hstream-server \
    --config-path /etc/hstream/config.yaml \
    --host 0.0.0.0 \
    --port 6570    \
    --address $MY_IP \
    --store-config /root/logdevice.conf \
    --zkuri "zookeeper:2188" \
    --server-id $SERVER_ID \
    --log-level debug \
    --log-with-color \
    >/tmp/node.log 2>&1 &
