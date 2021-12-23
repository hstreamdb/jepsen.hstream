#!/usr/bin/env bash

INFO() {
    /bin/echo -e "\e[104m\e[97m[INFO]\e[49m\e[39m" "$@"
}

ERROR() {
    /bin/echo -e "\e[101m\e[97m[ERROR]\e[49m\e[39m" "$@"
}

exists() {
    type "$1" > /dev/null 2>&1
}


# Generate SSH keys for the control node
exists ssh-keygen || { ERROR "Please install ssh-keygen (apt-get install openssh-client)"; exit 1; }
exists perl || { ERROR "Please install perl (apt-get install perl)"; exit 1; }

if [ ! -f ./secret/node.env ]; then
    INFO "Generating key pair"
    mkdir -p secret
    ssh-keygen -t rsa -m PEM -N "" -f ./secret/id_rsa

    INFO "Generating ./secret/control.env"
    { echo "SSH_PRIVATE_KEY=$(perl -p -e "s/\n/↩/g" < ./secret/id_rsa)";
      echo "SSH_PUBLIC_KEY=$(cat ./secret/id_rsa.pub)"; } >> ./secret/control.env

    INFO "Generating authorized_keys"
    { echo "$(cat ./secret/id_rsa.pub)"; } >> ./secret/authorized_keys

    INFO "Generating ./secret/node.env"
    { echo "ROOT_PASS=root"; } >> ./secret/node.env
else
    INFO "No need to generate key pair"
fi
