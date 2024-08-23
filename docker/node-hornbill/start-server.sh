#!/bin/bash

/usr/local/bin/init-ssh

echo "docker:docker@fdb:4500" > /etc/fdb.cluster

while ! fdbcli -C /etc/fdb.cluster --exec "tenant get hstream" --timeout 1 ; do
    sleep 1;
done

SERVER_ID=$(echo $(hostname) | cut -c 2-) # n_i -> i
MY_IP=$(hostname -I | head -n1 | awk '{print $1;}')
/usr/local/bin/hstream-server \
    --bind-address 0.0.0.0 \
    --port 9092    \
    --metrics-port 6600 \
    --advertised-address $MY_IP \
    --meta-servers http://meta:8964 \
    --store-config /etc/fdb.cluster \
    --server-id $SERVER_ID \
    --log-level debug \
    --log-with-color \
    >>/tmp/$HOSTNAME.log 2>&1 &
