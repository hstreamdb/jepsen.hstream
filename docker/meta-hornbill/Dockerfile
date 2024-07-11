ARG BASE_IMAGE="jepsen-hornbill:base"
FROM ${BASE_IMAGE}

COPY ./start-meta.sh /usr/local/bin/start-meta.sh
RUN chmod +x /usr/local/bin/start-meta.sh

EXPOSE 22 8964

CMD /usr/local/bin/start-meta.sh
