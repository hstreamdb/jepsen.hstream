#!/bin/bash

/usr/local/bin/init-ssh

# Waiting for logdevice to start
sleep 2

SERVER_ID=$(echo $(hostname) | cut -c 2-) # n_i -> i
MY_IP=$(hostname -I | head -n1 | awk '{print $1;}')
hstream-server kafka \
    --config-path /etc/hstream/config.yaml \
    --bind-address 0.0.0.0 \
    --port 9092    \
    --gossip-port 6571 \
    --advertised-address $MY_IP \
    --store-config zk:zookeeper:2181/logdevice.conf \
    --metastore-uri "zk://zookeeper:2181" \
    --server-id $SERVER_ID \
    --log-level debug \
    --log-with-color \
    --seed-nodes hserver-1,hserver-2,hserver-3,hserver-4,hserver-5 \
    >>/tmp/$HOSTNAME.log 2>&1 &
