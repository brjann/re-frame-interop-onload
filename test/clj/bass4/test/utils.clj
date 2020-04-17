(ns bass4.test.utils
  (:require [clojure.test :refer :all]
            [bass4.utils :refer :all]
            [bass4.i18n :as i18n]
            [clojure.data.json :as json]))

(deftest t-unserialize-key
  (is (= {:hej {"hej" "då"}} (unserialize-key {:hej "a:1:{s:3:\"hej\";s:3:\"då\";}"} :hej)))
  (is (= {:hej [2 3 4]} (unserialize-key {:hej "a:3:{i:0;i:1;i:1;i:2;i:2;i:3;}"} :hej #(mapv inc %)))))

;
;(defn arity [f]
;  (let [m (first (.getDeclaredMethods (class f)))
;        p (.getParameterTypes m)]
;    (alength p)))

(deftest t-arity
  (is (= 2 (arity (fn [x y]))))
  (is (= 0 (arity (fn [])))))
;
;(defn map-map [f m]
;  (let [ks (keys m)
;        vs (vals m)]
;    (zipmap ks (mapv f vs))))

(deftest t-map-map
  (is (= {:hej 2 :då 9} (map-map inc {:hej 1 :då 8}))))


(deftest t-str->int
  (is (= 3 (str->int "3")))
  (is (= 3 (str->int 3)))
  (is (= nil (str->int nil))))

(deftest t-diff
  (is (= [1 2] (diff [1 2 3] [3 4 5])))
  (is (= [0 1 2] (diff [0 1 2 3] [3 4 5]))))

(deftest t-parse-php-constants
  (is (= {:jag "är glad", :hejsan "hoppsan"} (parse-php-constants "define(\"hejsan\", \"hoppsan\");\ndefine(\"jag\", \"är glad\");")))
  (is (= {:jag "är glad", :hejsan "hoppsan"} (parse-php-constants "define('hejsan', \"hoppsan\");\ndefine(\"jag\", \"är glad\");"))))

(deftest i18n-merge
  (is (= (i18n/add-missing (:en i18n/i18n-map)) (clojure.edn/read-string (i18n/merge-i18n "xx")))))

(deftest i18n-map-to-list
  (is (= [:x "y" :s [:s "x"]] (i18n/i18n-map-to-list "{:x\"y\":s{:s\"x\"}}"))))

(deftest zipmap-keys-vals
  ; https://stackoverflow.com/questions/10772384/clojures-maps-are-keys-and-vals-in-same-order
  (let [n  (+ 20 (rand-int 50))
        ks (map str (sort (repeatedly n #(rand-int 1e9))))
        vs (repeatedly n #(rand-int 1e9))
        m  (zipmap ks vs)
        j  (json/read-str (json/write-str m))]
    (is (= m (zipmap (keys m) (vals m))))
    (is (= j (zipmap (keys j) (vals j))))
    (is (= m (zipmap (keys j) (vals j))))))