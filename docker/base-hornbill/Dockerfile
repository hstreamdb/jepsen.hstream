ARG HORNBILL_IMAGE="hstreamdb/hornbill:latest"
FROM ${HORNBILL_IMAGE}

ENV DEBIAN_FRONTEND=noninteractive

ARG USE_CHINA_MIRROR
# Mirror
RUN if [ "$USE_CHINA_MIRROR" = true ] ; then sed -i 's/archive.ubuntu.com/mirrors.ustc.edu.cn/g' /etc/apt/sources.list ; fi

# Basic system stuff
RUN apt-get -qy update && \
    apt-get -qy --no-install-recommends install \
        apt-transport-https

# Install packages
RUN apt-get -qy update && \
    apt-get -qy --no-install-recommends install \
        dos2unix \
        openssh-server \
        pwgen

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
        wget \
        # control deps
        git \
        gnuplot-nox \
        graphviz \
        htop \
        libjna-java \
        leiningen \
        openjdk-21-jdk-headless \
        openssh-client \
        screen && \
    rm -rf /var/lib/apt/lists/* && apt-get clean

# Config SSH for Jepsen connection
COPY ./init-ssh.sh /usr/local/bin/init-ssh

EXPOSE 22

RUN chmod +x /usr/local/bin/init-ssh
