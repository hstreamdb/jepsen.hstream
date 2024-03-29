ARG HSTREAM_IMAGE="hstreamdb/hstream:latest"
FROM ${HSTREAM_IMAGE}

# Do not ask for user input when installing packages
ENV DEBIAN_FRONTEND=noninteractive

ARG USE_CHINA_MIRROR
# Mirror
RUN if [ "$USE_CHINA_MIRROR" = true ] ; then sed -i 's/archive.ubuntu.com/mirrors.ustc.edu.cn/g' /etc/apt/sources.list ; fi

# Basic system stuff
RUN apt-get -qy update && \
    apt-get -qy --no-install-recommends install \
        apt-transport-https

# Jepsen (control) dependencies
RUN apt-get -qy update && \
    apt-get -qy --no-install-recommends install \
        curl \
        dos2unix \
        git \
        gnuplot-nox \
        htop \
        iputils-ping \
        libjna-java \
        leiningen \
        openjdk-21-jdk-headless \
        openssh-client \
        screen \
        wget \
        zookeeper && \
    rm -rf /var/lib/apt/lists/* && apt-get clean

ADD ./bashrc /root/.bashrc
ADD ./init-ssh-control.sh /init-ssh.sh
RUN dos2unix /init-ssh.sh /root/.bashrc \
    && chmod +x /init-ssh.sh

# Proxy
ARG arg_http_proxy
ARG arg_https_proxy
ENV env_http_proxy=$arg_http_proxy
ENV env_https_proxy=$arg_https_proxy

CMD /init-ssh.sh && \
    eval `ssh-agent` && \
    ssh-add /root/.ssh/id_rsa && \
    cd /home/Work && \
    tail -f --retry /var/jepsen/shared/hserver-cluster-started | sed '/Bootstraped/ q' && \
    ### Use proxy (if set) to download deps, then unset proxy for running tests
    export http_proxy=$env_http_proxy https_proxy=$env_https_proxy && \
    lein deps && \
    unset http_proxy && \
    unset https_proxy && \
    ### For jepsen.hstream.list-append-test
    # lein with-profile legacy-list-append run test \
    # --nodes "ld1,ld2,ld3,n1,n2,n3,n4,n5,zk" \
    # --ssh-private-key "/root/.ssh/id_rsa" \
    # --dummy false \
    # --grpc-timeout 20 \
    # --rate 100 \
    # --write-time 120 \
    # --fetch-wait-time 120 \
    # --concurrency 150 \
    # --fetching-number 100 \
    # --max-streams 10 \
    # --write-timeout 60 \
    # --nemesis-interval 15 \
    # --nemesis-on true \
    # --max-partitions 10 && \
    ### For jepsen.hstream.legacy-husky
    lein with-profile legacy-husky run test \
    --nodes "ld1,ld2,ld3,n1,n2,n3,n4,n5,zk" \
    --ssh-private-key "/root/.ssh/id_rsa" \
    --dummy false \
    --grpc-timeout 60 \
    --rate 50 \
    --write-number 5000 \
    --fetch-wait-time 30 \
    --concurrency 200 \
    --fetching-number 25 \
    --max-streams 10 \
    --write-timeout 60 \
    --nemesis-interval 20 \
    --nemesis-on true \
    --max-partitions 5 && \
    exit
