FROM foundationdb/foundationdb:7.3.57

COPY ./start.sh /usr/local/bin/start.sh
RUN chmod +x /usr/local/bin/start.sh

EXPOSE 22 4500

ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/start.sh"]
