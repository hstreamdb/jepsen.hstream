FROM ghcr.io/hstreamdb/jepsen_control:0.1.0

ADD ./init.sh /init-ssh.sh

CMD chmod +x /init-ssh.sh &&\
    /init-ssh.sh && \
    eval $(ssh-agent) &> /dev/null && \
    ssh-add /root/.ssh/id_rsa &> /dev/null && \
    cd /home/Work && \
    lein run test --ssh-private-key "/root/.ssh/id_rsa" --dummy true --rate 100 --write-time 120 --fetch-wait-time 120 --concurrency 100 --consumer-number 100 --max-streams 10 && \
    exit
