ARG BASE_IMAGE="jepsen-hornbill:base"
FROM ${BASE_IMAGE}

ADD ./bashrc /root/.bashrc
ADD ./init-ssh-control.sh /init-ssh-control.sh
RUN dos2unix /init-ssh-control.sh /root/.bashrc \
    && chmod +x /init-ssh-control.sh

# Proxy
ARG arg_http_proxy
ARG arg_https_proxy
ENV env_http_proxy=$arg_http_proxy
ENV env_https_proxy=$arg_https_proxy

CMD /init-ssh-control.sh && \
    eval `ssh-agent` && \
    ssh-add /root/.ssh/id_rsa && \
    cd /home/Work && \
    ### Use proxy (if set) to download deps, then unset proxy for running tests
    export http_proxy=$env_http_proxy https_proxy=$env_https_proxy && \
    lein deps && \
    unset http_proxy && \
    unset https_proxy && \
    ####################### start test #######################
    lein with-profile kafka run test \
    #-------------- basic options --------------#
    --db hornbill \
    --workload queue \
    --sub-via subscribe \
    --time-limit 180 \
    --final-time-limit 120 \
    --key-dist uniform \
    --key-count 32 \
    ##<<<
    # This value is enough. A larger one can make elle exhausted.
    --concurrency 10 \
    --rate 20 \
    ##>>>
    ##<<<
    # When writes of a key exceed the limit, the generator will pick
    # a new key (which can be larger than 'key-count'!!!), which is
    # very confusing. So we set a very large limit here.
    --max-writes-per-key 102400 \
    ##>>>
    #-------------- kafka client options --------------#
    ##<<<
    # The default number of partitions is 1, which may be different
    # from the test. This can make some partitions unusable. So we
    # never create topics automatically.
    --disable-server-auto-create-topics \
    ##>>>
    ##<<<
    # We disable producer retry now to avoid dupicate writes.
    # It may be turned on in the future to perform more tests...
    --retries 0 \
    ##>>>
    ##<<<
    # We allow the producer to send messages in batch, but do not
    # be too large.
    --producer-linger-ms 2000 \
    --batch-max-bytes 10240 \
    ##>>>
    #-------------- txns options --------------#
    ##<<<
    # We do not support txns and idempotent for now.
    # So we disable it.
    --no-txn \
    --no-idempotence \
    --no-server-idempotence \
    ##>>>
    #-------------- nemesis options --------------#
    --nemesis all \
    --nemesis-interval 15 \
    --crash-clients true \
    --tcpdump \
    # --crash-client-interval 30 \ # FIXME: parse error, what happened?
    # --tcpdump \ # TODO: sadly, tcpdump is only available for redpanda.
    #-------------- default options --------------#
    ##<<<
    # The following opts is client default (3.7.0) but we
    # specify them explicitly here for clarity.
    --auto-offset-reset latest \
    --enable-auto-commit \
    --enable-server-auto-create-topics \
    --isolation-level read_uncommitted \
    --acks all && \
    ##>>>
    ##<<<
    # For kafka test, the following options are hard-coded,
    # see workload/queue.clj and workload/list_append.clj.
    # - message-prefix-bytes = 1024
    # - replication-factor = 3
    # - partition-count = 4
    # - poll-ms = 100
    # - ... TODO
    ##>>>
    exit
