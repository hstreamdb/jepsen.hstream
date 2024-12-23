#!/bin/bash

/usr/local/bin/init-ssh

echo "docker:docker@fdb:4500" > /etc/fdb.cluster

while ! fdbcli -C /etc/fdb.cluster --exec "tenant get hornbill" --timeout 1 ; do
    sleep 1;
done

SERVER_ID=$(echo $(hostname) | cut -c 2-) # n_i -> i
MY_IP=$(hostname -I | head -n1 | awk '{print $1;}')
/usr/local/bin/hornbill server \
    --listeners plaintext://0.0.0.0:9092 \
    --advertised-listeners plaintext://$MY_IP:9092 \
    --metrics-port 6600 \
    --meta-servers http://meta:8964 \
    --store-config /etc/fdb.cluster \
    --server-id $SERVER_ID \
    --log-level debug \
    --log-with-color \
    >>/tmp/$HOSTNAME.log 2>&1 &
