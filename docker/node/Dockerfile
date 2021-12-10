FROM ghcr.io/hstreamdb/jepsen_node:0.1.0

CMD mkdir -p /run/sshd && \
    cp /data/store/logdevice.conf /root/logdevice.conf && \
    sed -i "s/127.0.0.1/logdevice/g" /root/logdevice.conf && \
    /usr/local/bin/setup-jepsen && \
    /usr/sbin/sshd && \
    SERVER_ID=$(shuf -i 1-4294967296 -n 1) && \
    MY_IP=$(hostname -I) && \
    sleep 10 && \
    hstream-server --host 0.0.0.0 --port 6570 --address $MY_IP --store-config /root/logdevice.conf --zkuri "zookeeper:2188" --server-id $SERVER_ID --log-level debug --log-with-color
