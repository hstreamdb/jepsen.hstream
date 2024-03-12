docker rm -f jepsen-kafka-n1 && \
    docker rm -f jepsen-kafka-n2 && \
    docker rm -f jepsen-kafka-n3 && \
    docker rm -f jepsen-kafka-n4 && \
    docker rm -f jepsen-kafka-n5 && \
    docker rm -f jepsen-kafka-ld1 && \
    docker rm -f jepsen-kafka-ld2 && \
    docker rm -f jepsen-kafka-ld3 && \
    docker rm -f jepsen-kafka-zookeeper && \
    docker rm -f jepsen-kafka-ld-admin && \
    docker rm -f jepsen-kafka-control && \
    docker volume rm jepsen-kafka_jepsen-kafka-shared
