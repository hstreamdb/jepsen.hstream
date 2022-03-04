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

# Config services
COPY setup-services.sh /usr/local/bin/setup-services
RUN chmod +x /usr/local/bin/setup-services

# Script for restarting hstream-server
COPY start-hstream-server.sh /bin/start-hstream-server
RUN  chmod +x /bin/start-hstream-server

# Configure SSHD
RUN sed -i "s/#PermitRootLogin prohibit-password/PermitRootLogin yes/g" /etc/ssh/sshd_config && \
    sed -i "s/#PasswordAuthentication yes/PasswordAuthentication no/g" /etc/ssh/sshd_config

# Install Jepsen deps
RUN apt-get install -qy build-essential bzip2 curl dnsutils faketime iproute2 iptables iputils-ping logrotate man man-db net-tools ntpdate psmisc python rsyslog sudo tar unzip vim wget ca-certificates

EXPOSE 22 6570

CMD /usr/local/bin/setup-services
