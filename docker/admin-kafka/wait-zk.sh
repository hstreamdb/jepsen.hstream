#!/usr/bin/bash

Test_Open () {
    </dev/tcp/"$1"/"$2"
    if [ "$?" -ne 0 ]; then
        return 1
    else
        return 0
    fi
}

Wait_Until_Open () {
    until Test_Open "$1" "$2"
    do
        sleep 1
    done
}

Wait_Until_Open zookeeper 2181
