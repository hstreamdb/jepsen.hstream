ARG BASE_IMAGE="jepsen-hstream:base"
FROM ${BASE_IMAGE}

# Waiting for admin
COPY ./wait.sh /usr/local/bin/wait
RUN chmod +x /usr/local/bin/wait

EXPOSE 22 6440

CMD /usr/local/bin/init-ssh && \
    MY_IP=$(hostname -I | head -n1 | awk '{print $1;}') && \
    mkdir -p /data/store && \
    echo 1 | tee /data/store/NSHARDS && \
    /usr/local/bin/wait && \
    logdeviced \
        --config-path zk:zookeeper:2181/logdevice.conf \
        --name $HOSTNAME \
        --address $MY_IP \
        --local-log-store-path /data/store \
        --num-shards 1 \
    >> /tmp/$HOSTNAME.log 2>&1
