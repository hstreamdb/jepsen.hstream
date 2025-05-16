#!/bin/bash

/usr/local/bin/init-ssh

echo "docker:docker@fdb:4500" > /etc/fdb.cluster

while true; do
  output=$(fdbcli -C /etc/fdb.cluster --exec "status json" --timeout 1 2>/dev/null)
  if [[ $? -eq 0 && "$output" == *'"tenant_mode" : "optional_experimental"'* ]]; then
    break
  fi
  sleep 1
done

MY_IP=$(hostname -I | head -n1 | awk '{print $1;}')
/usr/local/bin/flowmq \
    -C /etc/fdb.cluster \
    --storage fdb \
    --kafka-advertised-address $MY_IP \
    --with-kafka 9092 \
    --with-amqp 5672 \
    >>/tmp/$HOSTNAME.log 2>&1 &
