FDB_CLUSTER_FILE="/etc/foundationdb/fdb.cluster"
FDB_PORT=4500
FDB_NETWORKING_MODE="container"
fdbcli="/usr/bin/fdbcli"

## start fdb server
/var/fdb/scripts/fdb.bash >> /tmp/$HOSTNAME.log 2>&1 &

## wait & configure
# Attempt to connect. Configure the database if necessary.
if ! $fdbcli -C $FDB_CLUSTER_FILE --exec status --timeout 1 ; then
    config="configure new single ssd; status"
    if ! $fdbcli -C $FDB_CLUSTER_FILE --exec "$config" --timeout 10 ; then
        echo "Unable to configure new FDB cluster."
        exit 1
    fi
fi

echo "Can now connect to docker-based FDB cluster using $FDB_CLUSTER_FILE."

echo "Create hstream tenant"
config="configure tenant_mode=optional_experimental; createtenant hstream;"
if ! $fdbcli -C $FDB_CLUSTER_FILE --exec "$config" --timeout 10 ; then
    echo "Create hstream tenant failed"
fi

## Make sure the process doesn't exit
## FIXME: This makes the script block forever, which is bad for jepsen nemesis
tail -f /dev/null
