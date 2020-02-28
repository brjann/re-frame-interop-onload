(ns bass4.test.defapi
  (:require [clojure.test :refer :all]
            [bass4.api-coercion :as api :refer [defapi]]
            [clojure.data.json :as json]))


(defapi api-int
  [a :- int?]
  a)

(defapi api-?int
  [a :- [:? int?]]
  a)

(defapi api-int!
  [a :- api/->int]
  a)

(defapi api-?int!
  [a :- [:? api/->int]]
  a)


(deftest test-api-int
  ;; int
  (is (thrown? Exception (api-int "x")))
  (is (thrown? Exception (api-int nil)))
  (is (= 1 (api-int 1)))

  (is (thrown? Exception (api-?int "x")))
  (is (nil? (api-?int nil)))
  (is (= 1 (api-?int 1)))

  (is (thrown? Exception (api-int! "x")))
  (is (thrown? Exception (api-int! nil)))
  (is (= 1 (api-int! "1")))

  (is (thrown? Exception (api-?int! "x")))
  (is (nil? (api-?int! nil)))
  (is (= 1 (api-?int! "1"))))


(defapi api-bool
  [a :- boolean?]
  a)

(defapi api-?bool
  [a :- [:? boolean?]]
  a)

(defapi api-bool!
  [a :- api/->bool]
  a)

(defapi api-?bool!
  [a :- [:? api/->bool]]
  a)

(deftest test-api-bool
  (is (thrown? Exception (api-bool "x")))
  (is (thrown? Exception (api-bool nil)))
  (is (thrown? Exception (api-bool 1)))
  (is (thrown? Exception (api-bool "1")))
  (is (= true (api-bool true)))

  (is (thrown? Exception (api-?bool "x")))
  (is (thrown? Exception (api-?bool 1)))
  (is (thrown? Exception (api-?bool "1")))
  (is (nil? (api-?bool nil)))
  (is (= true (api-?bool true)))

  (is (thrown? Exception (api-bool! "x")))
  (is (thrown? Exception (api-bool! nil)))
  (is (= true (api-bool! "1")))
  (is (= true (api-bool! 1)))
  (is (= false (api-bool! "0")))
  (is (= false (api-bool! 0)))

  (is (thrown? Exception (api-?bool! "x")))
  (is (nil? (api-?bool! nil)))
  (is (= true (api-?bool! "1")))
  (is (= true (api-?bool! 1)))
  (is (= false (api-?bool! "0")))
  (is (= false (api-?bool! 0))))

(defapi api-str
  [a :- [[api/str? 2 4]]]
  a)

(defapi api-?str
  [a :- [:? [api/str? 2 4]]]
  a)

(deftest test-api-str
  (is (thrown? Exception (api-str 2)))
  (is (thrown? Exception (api-str nil)))
  (is (thrown? Exception (api-str "1")))
  (is (thrown? Exception (api-str "55555")))
  (is (= "22" (api-str "22")))
  (is (= "4444" (api-str "4444")))

  (is (thrown? Exception (api-?str 2)))
  (is (thrown? Exception (api-?str "1")))
  (is (thrown? Exception (api-?str "55555")))
  (is (nil? (api-?str nil)))
  (is (= "22" (api-?str "22")))
  (is (= "4444" (api-?str "4444"))))

(defapi api-?int!+str
  [a :- [:? api/->int] b :- [[api/str? 2 4]]]
  [a b])

(deftest test-api-int+str
  (is (thrown? Exception (api-?int!+str "x" "22")))
  (is (thrown? Exception (api-?int!+str "22" "1")))
  (is (thrown? Exception (api-?int!+str "22" "55555")))
  (is (thrown? Exception (api-?int!+str "22" nil)))
  (is (thrown? Exception (api-?int!+str nil "1")))
  (is (= [22 "22"] (api-?int!+str "22" "22")))
  (is (= [22 "4444"] (api-?int!+str "22" "4444")))
  (is (= [nil "22"] (api-?int!+str nil "22")))
  )

(defapi api-json!
  [a :- [api/->json]]
  a)

(defapi api-json!-map
  [a :- [api/->json map?]]
  a)

(defapi api-?json!-map
  [a :- [:? api/->json map?]]
  a)

(defapi api-json!-vec
  [a :- [api/->json vector?]]
  a)

(deftest test-api-json
  (let [j-map  {"hejsan" "hoppsan"}
        j-maps (json/write-str j-map)
        j-vec  [1 2 3]
        j-vecs (json/write-str j-vec)]
    (is (thrown? Exception (api-json! "x")))
    (is (thrown? Exception (api-json! 1)))
    (is (thrown? Exception (api-json! nil)))
    (is (= j-map (api-json! j-maps)))
    (is (= j-vec (api-json! j-vecs)))

    (is (thrown? Exception (api-json!-map "x")))
    (is (thrown? Exception (api-json!-map 1)))
    (is (thrown? Exception (api-json!-map nil)))
    (is (thrown? Exception (api-json!-map j-vecs)))
    (is (= j-map (api-json!-map j-maps)))

    (is (thrown? Exception (api-?json!-map "x")))
    (is (thrown? Exception (api-?json!-map 1)))
    (is (thrown? Exception (api-?json!-map j-vecs)))
    (is (= j-map (api-?json!-map j-maps)))
    (is (nil? (api-?json!-map nil)))

    (is (thrown? Exception (api-json!-vec "x")))
    (is (thrown? Exception (api-json!-vec 1)))
    (is (thrown? Exception (api-json!-vec nil)))
    (is (thrown? Exception (api-json!-vec j-maps)))
    (is (= j-vec (api-json!-vec j-vecs)))))

(defapi api-json!-map+?str
  [a :- [api/->json map?] b :- [:? [api/str? 2 4]]]
  [a b])

(deftest test-api-json-map+str
  (let [j-map  {"hejsan" "hoppsan"}
        j-maps (json/write-str j-map)
        j-vec  [1 2 3]
        j-vecs (json/write-str j-vec)]
    (is (thrown? Exception (api-json!-map+?str "x" "22")))
    (is (thrown? Exception (api-json!-map+?str j-maps "1")))
    (is (thrown? Exception (api-json!-map+?str j-vecs "22")))
    (is (= [j-map nil] (api-json!-map+?str j-maps nil)))
    (is (= [j-map "22"] (api-json!-map+?str j-maps "22")))))