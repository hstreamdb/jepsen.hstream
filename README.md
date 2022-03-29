# jepsen.hstream

Jepsen test instances for [HStreamDB](https://github.com/hstreamdb/hstream).

## Overview

The project is at its early stage and is under active development.

It currently contains the following tests:

- A modified set test suitable for append-only streaming databases

## Usage

```
docker-compose --file ./docker/docker-compose.yml --compatibility -p jepsen build

docker-compose --file ./docker/docker-compose.yml --compatibility -p jepsen up --renew-anon-volumes --exit-code-from control
```

**Note:** For users in the Chinese mainland, you can uncomment the following lines in the `project.clj` file to accelerate package retrieving:

```
  ; :mirrors {#"clojars" {:name "clojars-ustc",
  ;                       :url "https://mirrors.ustc.edu.cn/clojars/",
  ;                       :repo-manager true},
  ;           #"central" {:name "central-aliyun",
  ;                       :url "https://maven.aliyun.com/repository/public",
  ;                       :repo-manager true}}
```

and add `--build-arg USE_CHINA_MIRROR=true` when building docker images.

## Check Test Results

The test results will be stored at `./store` directory. Check it manually or by a simple server:

- If you have [`leiningen`](https://leiningen.org/) installed:
```
lein with-profile husky run serve
```
- If you do not have `leiningen`:
```
docker run -t --rm --network host -v $(pwd):/working ardoq/leiningen:jdk11-2.9.4-mp /bin/bash -c "cd /working && lein with-profile husky run serve"
```

Then browse the results at `localhost:8080`.

## Customization

- Add `HSTREAM_IMAGE` arg on building step to use your own hstream image.
- Adjust test parameters in `docker/control/Dockerfile`.
