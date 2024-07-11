docker rm -f jepsen-hornbill-n1 && \
    docker rm -f jepsen-hornbill-n2 && \
    docker rm -f jepsen-hornbill-n3 && \
    docker rm -f jepsen-hornbill-n4 && \
    docker rm -f jepsen-hornbill-n5 && \
    docker rm -f jepsen-hornbill-fdb && \
    docker rm -f jepsen-hornbill-meta && \
    docker rm -f jepsen-hornbill-control && \
    docker volume rm jepsen-hornbill_jepsen-hornbill-shared
