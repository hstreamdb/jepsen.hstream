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
RUN apt-get install -qy build-essential bzip2 curl dnsutils faketime iproute2 iptables iputils-ping logrotate man man-db net-tools ntpdate psmisc python rsyslog sudo tar unzip vim wget ca-certificates zookeeper

# Config Jepsen
COPY ./init-ssh.sh /usr/local/bin/init-ssh
RUN chmod +x /usr/local/bin/init-ssh

# Configure SSHD
RUN sed -i "s/#PermitRootLogin prohibit-password/PermitRootLogin yes/g" /etc/ssh/sshd_config

# Waiting for Zookeeper
COPY ./wait-zk.sh /usr/local/bin/wait-zk
RUN chmod +x /usr/local/bin/wait-zk

# Waiting for logdeviced
COPY ./wait-hstore.sh /usr/local/bin/wait-hstore
RUN chmod +x /usr/local/bin/wait-hstore

# Config for LogDevice
COPY ./logdevice.json /etc/logdevice.json

CMD mkdir -p /run/sshd && \
    /usr/local/bin/init-ssh && \
    /usr/sbin/sshd && \
    ZK_IP=$(dig +short zookeeper) && \
    sed -i "s/172.16.0.10:2181/$ZK_IP:2181/g" /etc/logdevice.json && \
    /usr/local/bin/wait-zk && \
    /usr/share/zookeeper/bin/zkCli.sh -server zookeeper:2181 create /logdevice.conf "`cat /etc/logdevice.json`" && \
    ld-admin-server \
        --config-path zk:zookeeper:2181/logdevice.conf \
        --enable-maintenance-manager \
        --maintenance-log-snapshotting \
        --enable-safety-check-periodic-metadata-update & \
    /usr/local/bin/wait-hstore && \
    hadmin store nodes-config bootstrap --metadata-replicate-across node:3 && \
    echo "Bootstraped" > /var/jepsen/shared/ld-cluster-started && \
    tail -f /dev/null
