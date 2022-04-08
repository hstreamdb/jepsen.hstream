(ns jepsen.hstream.generator
  (:gen-class)
  (:require [jepsen.generator :as gen]
            [clojure.tools.logging :refer [info]])
  )

(defrecord VecGen [gens]
  gen/Generator
  (gen/update [this test ctx event]
    (when (seq gens)
      (info "===== Update: on event " event)
      (let [_ (info "===== fst gen: " (first gens))
            fst-updated (update (first gens) test ctx event)
            _ (info "===== Update fst gen to " fst-updated)]
        (VecGen. (into [] (cons fst-updated (next gens)))))
      ; Updates are passed to the first generator in the sequence.
      ))

  (gen/op [this test ctx]
    (when (seq gens) ; Once we're out of generators, we're done
      (let [gen (first gens)]
        (if-let [[op gen'] (gen/op gen test ctx)]
          (do (info "===== Got an op: " op ". gen ->" gen')
          [op (if-let [nxt (next gens)]
                (VecGen. (into [] (cons gen' (next gens))))
                (VecGen. [gen']))])

          (do
            (info "===== Dropping...")
            (gen/op (VecGen. (into [] (next gens))) test ctx))

          )))

          )
  )
