ARG BASE_IMAGE="jepsen-hstream:base"
FROM ${BASE_IMAGE}

# Script for restarting hstream-server
COPY start-server.sh /bin/start-server
RUN  chmod +x /bin/start-server

EXPOSE 22 6570

CMD /usr/local/bin/init-ssh && \
    # Waiting for logdevice to start
    sleep 2 && \
    /bin/start-server && \
    bash # Keep container alive
