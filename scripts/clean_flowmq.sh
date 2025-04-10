docker rm -f jepsen-flowmq-n1 && \
docker rm -f jepsen-flowmq-fdb && \
docker rm -f jepsen-flowmq-control && \
docker volume rm jepsen-flowmq_jepsen-flowmq-shared
