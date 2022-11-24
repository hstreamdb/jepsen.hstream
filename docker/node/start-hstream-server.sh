#!/bin/bash

SERVER_ID=$(shuf -i 1-4294967296 -n 1)
MY_IP=$(hostname -I | head -n1 | awk '{print $1;}')
hstream-server \
    --config-path /etc/hstream/config.yaml \
    --bind-address 0.0.0.0 \
    --port 6570    \
    --internal-port 6571 \
    --advertised-address $MY_IP \
    --store-config zk:zookeeper:2181/logdevice.conf \
    --metastore-uri "zk://zookeeper:2181" \
    --server-id $SERVER_ID \
    --log-level debug \
    --log-with-color \
    --seed-nodes hserver-1,hserver-2,hserver-3,hserver-4,hserver-5 \
    >/tmp/node.log 2>&1 &
