ARG BASE_IMAGE="jepsen-hstream:base"
FROM ${BASE_IMAGE}

# Waiting for Zookeeper
COPY ./wait-zk.sh /usr/local/bin/wait-zk
RUN chmod +x /usr/local/bin/wait-zk

# Waiting for logdeviced
COPY ./wait-hstore.sh /usr/local/bin/wait-hstore
RUN chmod +x /usr/local/bin/wait-hstore

# Waiting for hservers
COPY ./wait-hservers.sh /usr/local/bin/wait-hservers
RUN chmod +x /usr/local/bin/wait-hservers

# Config for LogDevice
COPY ./logdevice.json /etc/logdevice.json

EXPOSE 22 6440

CMD /usr/local/bin/init-ssh && \
    ZK_IP=$(dig +short zookeeper) && \
    sed -i "s/172.16.0.10:2181/$ZK_IP:2181/g" /etc/logdevice.json && \
    /usr/local/bin/wait-zk && \
    /usr/share/zookeeper/bin/zkCli.sh -server zookeeper:2181 create /logdevice.conf "`cat /etc/logdevice.json`" && \
    ld-admin-server \
        --config-path zk:zookeeper:2181/logdevice.conf \
        --enable-maintenance-manager \
        --maintenance-log-snapshotting \
        --enable-safety-check-periodic-metadata-update \
    >> /tmp/$HOSTNAME.log 2>&1 & \
    /usr/local/bin/wait-hstore && \
    hadmin store nodes-config bootstrap --metadata-replicate-across node:3 && \
    /usr/local/bin/wait-hservers && \
    hadmin server --host hserver-1 init >> /tmp/$HOSTNAME.log 2>&1 && \
    echo "Bootstraped" > /var/jepsen/shared/hserver-cluster-started && \
    tail -f /dev/null
