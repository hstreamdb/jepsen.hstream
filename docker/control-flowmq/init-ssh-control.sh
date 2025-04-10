#!/bin/sh

sleep 1 && \

: "${SSH_PRIVATE_KEY?SSH_PRIVATE_KEY is empty, please use up.sh}"
: "${SSH_PUBLIC_KEY?SSH_PUBLIC_KEY is empty, please use up.sh}"

if [ ! -f ~/.ssh/known_hosts ]; then
    mkdir -m 700 ~/.ssh
    echo $SSH_PRIVATE_KEY | perl -p -e 's/↩/\n/g' > ~/.ssh/id_rsa
    chmod 600 ~/.ssh/id_rsa
    echo $SSH_PUBLIC_KEY > ~/.ssh/id_rsa.pub
    echo > ~/.ssh/known_hosts
    # Get nodes list
    sort -V /var/jepsen/shared/nodes > ~/nodes
    # Scan SSH keys
    while read node; do
      ssh-keyscan -t rsa $node >> ~/.ssh/known_hosts
      ssh-keyscan -t ed25519 $node >> ~/.ssh/known_hosts
    done <~/nodes
fi
