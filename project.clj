(defproject jepsen.hstream "0.0.0-SNAPSHOT"
  :description "Jepsen test instances for HStreamDB"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0",
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :profiles {:write-then-read {:main jepsen.write-then-read},
             :husky {:main jepsen.husky-test}}
  :repositories
    [["sonatype-snapshot"
      "https://s01.oss.sonatype.org/content/repositories/snapshots"]]
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [jepsen "0.3.2"]
                 [random-string "0.1.0"]
                 [io.hstream/hstreamdb-java
                  "0.16.0-SNAPSHOT"
                  :exclusions
                  [org.apache.logging.log4j/log4j-slf4j-impl
                   org.apache.logging.log4j/Log4jLogger]]])
