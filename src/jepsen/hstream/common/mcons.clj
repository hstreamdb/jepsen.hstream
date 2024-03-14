;; https://gist.github.com/fredyr/6341286
;; http://docs.racket-lang.org/reference/mpairs.html

(ns jepsen.hstream.common.mcons)

(defprotocol IMCons
  (mcar [p])
  (mcdr [p])
  (set-mcar! [p val])
  (set-mcdr! [p val]))

(deftype MCons [^{:volatile-mutable true} car ^{:volatile-mutable true} cdr]
  IMCons
    (mcar [this] car)
    (mcdr [this] cdr)
    (set-mcar! [this val] (set! car val))
    (set-mcdr! [this val] (set! cdr val)))

(defn mcons [a b] (MCons. a b))
(defn mcar [p] (.mcar p))
(defn mcdr [p] (.mcdr p))
(defn set-mcar! [p v] (.set-mcar! p v))
(defn set-mcdr! [p v] (.set-mcdr! p v))
