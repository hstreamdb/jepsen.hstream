echo "docker:docker@fdb:4500" > /etc/fdb.cluster

while ! fdbcli -C /etc/fdb.cluster --exec "tenant get hornbill" --timeout 1 ; do
    sleep 1;
done

/usr/local/bin/hornbill meta \
    --host 0.0.0.0 \
    --port 8964 \
    --log-level trace \
    --log-with-color \
    --log-flush-immediately \
    --backend /etc/fdb.cluster \
    >> /tmp/$HOSTNAME.log 2>&1
