ARG BASE_IMAGE="jepsen-hstream:base"
FROM ${BASE_IMAGE}

# Script for restarting hstream-server
COPY start-server.sh /bin/start-server
RUN  chmod +x /bin/start-server

EXPOSE 22 9092

CMD /bin/start-server && \
    bash # Keep container alive
