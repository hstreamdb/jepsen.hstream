# jepsen.hstream

Jepsen test instances for [HStreamDB](https://github.com/hstreamdb/hstream).

## Overview

The project is at its early stage and is under active development.

It currently contains the following tests:

- A modified set test suitable for append-only streaming databases

## Usage

- Use the [script](https://github.com/hstreamdb/jepsen-start-cluster) to bootstrap the test cluster and mount this project to it.
- Go to the project folder in the jepsen console and run

```
lein run test
```

**Note:** For users in Chinese mainland, you can uncomment the following lines in the `project.clj` file to accelerate package retrieving:

```
  ; :mirrors {#"clojars" {:name "clojars-ustc"
  ;                       :url "https://mirrors.ustc.edu.cn/clojars/"
  ;                       :repo-manager true}
  ;           #"central" {:name "central-aliyun"
  ;                       :url "https://maven.aliyun.com/repository/public"
  ;                       :repo-manager true}}
```
