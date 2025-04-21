ARG FLOWMQ_IMAGE="ghcr.io/flowmq-io/flowmq:main"
ARG BASE_IMAGE="jepsen-flowmq:base"
FROM ${FLOWMQ_IMAGE} AS builder

# ------------------------------------------------------------------------------

FROM ${BASE_IMAGE}

RUN apt-get update && \
    apt-get -qy --no-install-recommends install \
        liblzma-dev \
        libz-dev

# libssl 1.1, if needed
#RUN curl -O https://security.ubuntu.com/ubuntu/pool/main/o/openssl/libssl1.1_1.1.1f-1ubuntu2.24_amd64.deb&& \
#    dpkg -i libssl1.1_1.1.1f-1ubuntu2.24_amd64.deb && \
#    rm libssl1.1_1.1.1f-1ubuntu2.24_amd64.deb

ARG LIB_PATH=/usr/lib/x86_64-linux-gnu

# Copy FlowMQ
COPY --from=builder /usr/local/bin/flowmq /usr/local/bin/flowmq
COPY --from=builder $LIB_PATH/libfdb_c.so $LIB_PATH/libfdb_c.so

# Script for restarting flowmq server
COPY start-server.sh /bin/start-server
RUN  chmod +x /bin/start-server

EXPOSE 22 9092

CMD /bin/start-server && \
    bash # Keep container alive
