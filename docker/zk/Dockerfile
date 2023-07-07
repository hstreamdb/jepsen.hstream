FROM zookeeper:3.7

ENV DEBIAN_FRONTEND=noninteractive

ARG USE_CHINA_MIRROR
# Mirror
RUN if [ "$USE_CHINA_MIRROR" = true ] ; then sed -i 's/deb.debian.org/mirrors.ustc.edu.cn/g' /etc/apt/sources.list && sed -i 's|security.debian.org/debian-security|mirrors.ustc.edu.cn/debian-security|g' /etc/apt/sources.list ; fi

# Basic system stuff
RUN apt-get -qy update && \
    apt-get -qy --no-install-recommends install \
        apt-transport-https

# Install packages
RUN apt-get -qy update && \
    apt-get -qy --no-install-recommends install \
        dos2unix \
        openssh-server

# Install Jepsen deps
RUN apt-get -qy --no-install-recommends install \
        build-essential \
        bzip2 \
        ca-certificates \
        curl \
        dirmngr \
        dnsutils \
        faketime \
        iproute2 \
        iptables \
        iputils-ping \
        logrotate \
        man \
        man-db \
        netcat \
        net-tools \
        ntpdate \
        psmisc \
        python3 \
        rsyslog \
        sudo \
        tar \
        tcpdump \
        unzip \
        vim \
        wget && \
    rm -rf /var/lib/apt/lists/* && apt-get clean

# Config SSH for Jepsen connection
COPY ./init-ssh.sh /usr/local/bin/init-ssh
RUN chmod +x /usr/local/bin/init-ssh

EXPOSE 22 2181 2888 3888

CMD /usr/local/bin/init-ssh && \
    zkServer.sh start-foreground >> /tmp/$HOSTNAME.log 2>&1
