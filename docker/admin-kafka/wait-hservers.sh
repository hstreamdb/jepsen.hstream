#!/usr/bin/bash

until ( \
    /usr/local/bin/hstream-kafka --host hserver-1 --port 9092 node status && \
    /usr/local/bin/hstream-kafka --host hserver-2 --port 9092 node status && \
    /usr/local/bin/hstream-kafka --host hserver-3 --port 9092 node status && \
    /usr/local/bin/hstream-kafka --host hserver-4 --port 9092 node status && \
    /usr/local/bin/hstream-kafka --host hserver-5 --port 9092 node status \
) >/dev/null 2>&1; do
    sleep 1
done;
