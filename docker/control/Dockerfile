FROM debian:bullseye

ARG USE_CHINA_MIRROR
# MIRROR
RUN if [ "$USE_CHINA_MIRROR" = true ] ; then sed -i 's/deb.debian.org/mirrors.ustc.edu.cn/g' /etc/apt/sources.list && sed -i 's|security.debian.org/debian-security|mirrors.ustc.edu.cn/debian-security|g' /etc/apt/sources.list ; fi

# Jepsen dependencies
RUN apt-get -y -q update && \
    apt-get install -qy openjdk-11-jdk-headless \
    libjna-java \
    git \
    openssh-client \
    curl \
    wget \
    dos2unix \
    gnuplot \
    zookeeper \
    leiningen

ADD ./bashrc /root/.bashrc
ADD ./init-ssh-control.sh /init-ssh.sh
RUN dos2unix /init-ssh.sh /root/.bashrc \
    && chmod +x /init-ssh.sh

CMD /init-ssh.sh && \
    eval `ssh-agent` && \
    ssh-add /root/.ssh/id_rsa && \
    cd /home/Work && \
    # For jepsen.write-then-read
    # lein with-profile write-then-read run test \
    # --nodes "ld1,ld2,ld3,ld4,n1,n2,n3,n4,n5,zk" \
    # --ssh-private-key "/root/.ssh/id_rsa" \
    # --dummy false \
    # --rate 100 \
    # --write-time 120 \
    # --fetch-wait-time 240 \
    # --concurrency 150 \
    # --fetching-number 100 \
    # --max-streams 10 \
    # --write-timeout 60 \
    # --nemesis-interval 15 \
    # --nemesis-on false \
    # --max-partitions 10 && \
    # For jepsen.husky-test
    lein with-profile husky run test \
    --nodes "ld1,ld2,ld3,ld4,n1,n2,n3,n4,n5,zk" \
    --ssh-private-key "/root/.ssh/id_rsa" \
    --dummy false \
    --rate 10 \
    --write-number 2000 \
    --fetch-wait-time 180 \
    --concurrency 200 \
    --fetching-number 100 \
    --max-streams 10 \
    --write-timeout 60 \
    --nemesis-interval 15 \
    --nemesis-on true \
    --max-partitions 10 && \
    exit
