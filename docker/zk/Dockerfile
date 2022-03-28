FROM zookeeper:3.7.0

ARG USE_CHINA_MIRROR

# Mirror
RUN if [ "$USE_CHINA_MIRROR" = true ] ; then sed -i 's/deb.debian.org/mirrors.ustc.edu.cn/g' /etc/apt/sources.list && sed -i 's|security.debian.org/debian-security|mirrors.ustc.edu.cn/debian-security|g' /etc/apt/sources.list ; fi

# Basic system stuff
RUN apt-get update
RUN apt-get install -y apt-transport-https openssh-server

# Config Jepsen
COPY ./init-ssh.sh /usr/local/bin/init-ssh
RUN chmod +x /usr/local/bin/init-ssh

# Configure SSHD
RUN sed -i "s/#PermitRootLogin prohibit-password/PermitRootLogin yes/g" /etc/ssh/sshd_config

# Install Jepsen deps
RUN apt-get install -qy build-essential bzip2 curl dnsutils faketime iproute2 iptables iputils-ping libzip4 logrotate man man-db net-tools ntpdate psmisc python rsyslog sudo tar unzip vim wget ca-certificates

# Config for LogDevice
COPY ./logdevice.json /etc/logdevice.json

EXPOSE 2181 2888 3888

CMD mkdir -p /run/sshd && \
    /usr/local/bin/init-ssh && \
    /usr/sbin/sshd && \
    zkServer.sh start && \
    MY_IP=$(hostname -I | head -n1 | awk '{print $1;}') && \
    sed -i "s/172.16.0.10:2181/$MY_IP:2181/g" /etc/logdevice.json && \
    zkCli.sh create /logdevice.conf "`cat /etc/logdevice.json`" && \
    tail -f /dev/null
