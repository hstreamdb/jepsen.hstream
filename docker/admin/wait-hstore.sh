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

Wait_Until_Open hstore-1 6440
Wait_Until_Open hstore-2 6440
Wait_Until_Open hstore-3 6440
Wait_Until_Open hstore-4 6440
