#!/bin/bash

# Configure sshd
# We allow root login, and we increase the number of concurrent connections
sed -i "s/#PermitRootLogin prohibit-password/PermitRootLogin yes/g" /etc/ssh/sshd_config
sed -i "s/#MaxStartups 10:30:100/MaxStartups 100:30:100/g" /etc/ssh/sshd_config

# Start ssh server
mkdir -p /run/sshd
/usr/sbin/sshd &

# We add our hostname to the shared volume, so that control can find us
echo "Adding hostname to shared volume" >> /var/log/jepsen-setup.log
# We do a little dance to get our hostname (random hex), IP, then use DNS to
# get a proper container name.
#HOSTNAME=`hostname`
#IP=`getent hosts "${HOSTNAME}" | awk '{ print $1 }'`
#NAME=`dig +short -x "${IP}" | cut -f 1 -d .`
#echo "${NAME}" >> /var/jepsen/shared/nodes
echo `hostname` >> /var/jepsen/shared/nodes

# We make sure that root's authorized keys are ready
echo "Setting up root's authorized_keys" >> /var/log/jepsen-setup.log
mkdir -p /root/.ssh
chmod 700 /root/.ssh
cp /run/secrets/authorized_keys /root/.ssh/
chmod 600 /root/.ssh/authorized_keys
