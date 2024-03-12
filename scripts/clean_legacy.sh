docker rm -f jepsen-n1 && \
    docker rm -f jepsen-n2 && \
    docker rm -f jepsen-n3 && \
    docker rm -f jepsen-n4 && \
    docker rm -f jepsen-n5 && \
    docker rm -f jepsen-ld1 && \
    docker rm -f jepsen-ld2 && \
    docker rm -f jepsen-ld3 && \
    docker rm -f jepsen-zookeeper && \
    docker rm -f jepsen-ld-admin && \
    docker rm -f jepsen-control && \
    docker volume rm jepsen_jepsen-shared
