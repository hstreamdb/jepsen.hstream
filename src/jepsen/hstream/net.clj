(ns jepsen.hstream.net
  (:require [clojure.string :as str]
            [jepsen.control :refer :all]
            [jepsen.control.net :as control.net]
            [jepsen.net.proto :as p]
            [jepsen.net :refer :all]))

(defn is-qdisc-dev-exist?
  [dev]
  (let [out (su (exec tc :qdisc :list :dev dev))]
    (if (re-find #"Cannot find device" out) false true)))

(def iptables+
  "Default iptables (assumes we control everything)."
  (reify
    Net
      (drop! [net test src dest]
        (on dest
            (su (exec :iptables
                      :-A
                      :INPUT
                      :-s
                      (control.net/ip src)
                      :-j
                      :DROP
                      :-w))))
      (heal! [net test]
        (with-test-nodes test
                         (su (exec :iptables :-F :-w)
                             (exec :iptables :-X :-w))))
      (slow! [net test]
        (with-test-nodes test
                         (try (su (exec tc
                                        :qdisc
                                        :add :dev
                                        :eth0 :root
                                        :netem :delay
                                        :50ms :10ms
                                        :distribution :normal))
                              (catch RuntimeException e
                                (if (re-find
                                      #"Exclusivity flag on, cannot modify"
                                      (.getMessage e))
                                  nil
                                  (throw e))))))
      (slow! [net test
              {:keys [mean variance distribution],
               :or {mean 50, variance 10, distribution :normal}}]
        (with-test-nodes test
                         (try (su (exec tc
                                        :qdisc
                                        :add
                                        :dev
                                        :eth0
                                        :root
                                        :netem
                                        :delay
                                        (str mean "ms")
                                        (str variance "ms")
                                        :distribution
                                        distribution))
                              (catch RuntimeException e
                                (if (re-find
                                      #"Exclusivity flag on, cannot modify"
                                      (.getMessage e))
                                  nil
                                  (throw e))))))
      (flaky! [net test]
        (with-test-nodes
          test
          (try
            (su (exec tc :qdisc :add :dev :eth0 :root :netem :loss "20%" "75%"))
            (catch RuntimeException e
              (if (re-find #"Exclusivity flag on, cannot modify"
                           (.getMessage e))
                nil
                (throw e))))))
      (fast! [net test]
        (with-test-nodes
          test
          (when (is-qdisc-dev-exist? :eth0)
            (try (su (exec tc :qdisc :del :dev :eth0 :root))
                 (catch RuntimeException e
                   (if (re-find #"Cannot delete qdisc with handle of zero"
                                (.getMessage e))
                     nil
                     (throw e)))))))
    p/PartitionAll
      (drop-all! [net test grudge]
        (on-nodes test
                  (keys grudge)
                  (fn snub [_ node]
                    (when (seq (get grudge node))
                      (su (exec :iptables
                                :-A
                                :INPUT
                                :-s
                                (->> (get grudge node)
                                     (map control.net/ip)
                                     (str/join ","))
                                :-j
                                :DROP
                                :-w))))))))
