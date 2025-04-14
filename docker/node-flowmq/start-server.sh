#!/bin/bash

/usr/local/bin/init-ssh

echo "docker:docker@fdb:4500" > /etc/fdb.cluster

while ! fdbcli -C /etc/fdb.cluster --exec "status" --timeout 1 ; do
    sleep 1;
done

MY_IP=$(hostname -I | head -n1 | awk '{print $1;}')
/usr/local/bin/flowmq \
    -C /etc/fdb.cluster \
    --storage fdb \
    --kafka-advertised-address $MY_IP \
    --with-kafka 9092 \
    --with-amqp 5672 \
    >>/tmp/$HOSTNAME.log 2>&1 &
