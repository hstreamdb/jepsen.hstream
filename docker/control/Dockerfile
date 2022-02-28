FROM ghcr.io/hstreamdb/jepsen_control:0.1.0

ADD ./init.sh /init-ssh.sh

CMD dos2unix /init-ssh.sh && \
    chmod +x /init-ssh.sh && \
    /init-ssh.sh && \
    eval `ssh-agent` && \
    ssh-add /root/.ssh/id_rsa && \
    cd /home/Work && \
    lein run test --ssh-private-key "/root/.ssh/id_rsa" --dummy false --rate 100 --write-time 180 --fetch-wait-time 180 --concurrency 100 --fetching-number 100 --max-streams 10 --write-timeout 60 --nemesis-interval 15 --nemesis-on false --max-partitions 10 && \
    exit
