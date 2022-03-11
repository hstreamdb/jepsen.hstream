(defproject jepsen.hstream "0.0.0-SNAPSHOT"
  :description "Jepsen test instances for HStreamDB"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :main jepsen.write-then-read
  ; :mirrors {#"clojars" {:name "clojars-ustc"
  ;                       :url "https://mirrors.ustc.edu.cn/clojars/"
  ;                       :repo-manager true}
  ;           #"central" {:name "central-aliyun"
  ;                       :url "https://maven.aliyun.com/repository/public"
  ;                       :repo-manager true}}
  :repositories [["sonatype-snapshot" "https://s01.oss.sonatype.org/content/repositories/snapshots"]]
  :dependencies [[org.clojure/clojure "1.10.3"]
                 [jepsen "0.2.6"]
                 [random-string "0.1.0"]
                 [io.hstream/hstreamdb-java "0.7.1-SNAPSHOT" :exclusions [org.apache.logging.log4j/log4j-slf4j-impl]]])
