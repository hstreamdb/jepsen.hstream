ARG FLOWMQ_IMAGE="ghcr.io/flowmq-io/flowmq:main"
ARG BASE_IMAGE="jepsen-flowmq:base"
FROM ${FLOWMQ_IMAGE} AS builder

# ------------------------------------------------------------------------------

FROM ${BASE_IMAGE}

ARG LIB_PATH=/usr/lib/x86_64-linux-gnu

# Copy FlowMQ
COPY --from=builder /usr/local/bin/flowmq /usr/local/bin/flowmq
COPY --from=builder $LIB_PATH/libfdb_c.so      $LIB_PATH/libfdb_c.so
COPY --from=builder $LIB_PATH/liblzma.so.5     $LIB_PATH/liblzma.so.5
COPY --from=builder $LIB_PATH/libz.so.1        $LIB_PATH/libz.so.1
COPY --from=builder $LIB_PATH/libssl.so.1.1    $LIB_PATH/libssl.so.1.1
COPY --from=builder $LIB_PATH/libcrypto.so.1.1 $LIB_PATH/libcrypto.so.1.1

# Script for restarting flowmq server
COPY start-server.sh /bin/start-server
RUN  chmod +x /bin/start-server

EXPOSE 22 9092

CMD /bin/start-server && \
    bash # Keep container alive
