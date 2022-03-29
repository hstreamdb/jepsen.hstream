ARG HSTREAM_IMAGE="hstreamdb/hstream:latest"
FROM ${HSTREAM_IMAGE}

ARG USE_CHINA_MIRROR
# Mirror
RUN if [ "$USE_CHINA_MIRROR" = true ] ; then sed -i 's/archive.ubuntu.com/mirrors.ustc.edu.cn/g' /etc/apt/sources.list ; fi

# Basic system stuff
RUN apt-get update
RUN apt-get install -y apt-transport-https

# Install packages
RUN apt-get -qy update && \
    apt-get -qy install \
        dos2unix \
        openssh-server \
        pwgen

# Install Jepsen deps
RUN apt-get install -qy build-essential bzip2 curl dnsutils faketime iproute2 iptables iputils-ping logrotate man man-db net-tools ntpdate psmisc python rsyslog sudo tar unzip vim wget ca-certificates

# Config Jepsen
COPY ./init-ssh.sh /usr/local/bin/init-ssh
RUN chmod +x /usr/local/bin/init-ssh

# Configure SSHD
RUN sed -i "s/#PermitRootLogin prohibit-password/PermitRootLogin yes/g" /etc/ssh/sshd_config

# Waiting for admin
COPY ./wait.sh /usr/local/bin/wait
RUN chmod +x /usr/local/bin/wait

CMD mkdir -p /run/sshd && \
    /usr/local/bin/init-ssh && \
    /usr/sbin/sshd && \
    MY_IP=$(hostname -I | head -n1 | awk '{print $1;}') && \
    mkdir -p /data/store && \
    echo 1 | tee /data/store/NSHARDS && \
    /usr/local/bin/wait && \
    logdeviced \
        --config-path zk:zookeeper:2181/logdevice.conf \
        --name $HOSTNAME \
        --address $MY_IP \
        --local-log-store-path /data/store \
        --num-shards 1
