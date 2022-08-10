#!/usr/bin/bash

until ( \
    /usr/local/bin/hadmin server --host hserver-1 --port 6570 status && \
    /usr/local/bin/hadmin server --host hserver-2 --port 6570 status && \
    /usr/local/bin/hadmin server --host hserver-3 --port 6570 status && \
    /usr/local/bin/hadmin server --host hserver-4 --port 6570 status && \
    /usr/local/bin/hadmin server --host hserver-5 --port 6570 status \
) >/dev/null 2>&1; do
    sleep 1
done;
