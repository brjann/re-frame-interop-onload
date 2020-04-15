(ns bass4.test.regression-analysis
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [bass4.utils :as utils]
            [bass4.db.core :as db]
            [clojure.string :as str]
            [clojure.java.io :as io])
  (:import (java.io Writer)))

(def jdbc-config {:dbtype          "mysql"
                  :dbname          "cfs_andreasson"
                  :user            "reader"
                  :password        "reader.11"
                  :host            "localhost"
                  :port            3305
                  "serverTimezone" "UTC"})

(def mappings {"genetic_dmc"             "nordicgenetik"
               "coronaoro_andersson"     "coronaoro"
               "magtarmskolan_uusijarvi" "magtarmskolan"
               "therapy_ruck"            "therapy"
               "dersed_bjureberg"        "dersed"
               "trauma_andersson"        "trauma"
               "hdtrex_anova"            "hdtrex"
               "pelarkbt"                "pelarkbt"
               "ilska_bjureberg"         "ilska"
               "ibs_gbg"                 "ibsskola"
               "astma_bass4"             "astma"
               "diabetes"                "diabetes"
               "supk_sthlms_universitet" "supk"
               "flimmerenkaten_ljotsson" "flimmerenkaten"
               "smartstudien_lalouni"    "smartstudien"
               "igetliving_simons"       "igetliving"
               "s3_mansson"              "ss3"
               "erita_danmark"           "eritadk"
               "toc_ruck"                "toc"
               "fibro"                   "fibro"
               "stgoran_wicksell"        "stgoran"
               "iact"                    "iact"
               "atrialf"                 "ff"
               "ocdnet_ruck"             "ocdnet"
               "insomni_jernelov"        "insomni"
               "socialoro_hogstrom"      "socialoro"
               "ibd_kern"                "ibd"
               "mbs_johansson"           "mbs"
               "indiacbt_jayaram"        "indiacbt"
               "crps_rickardsson"        "crps"
               "sebs_alfonsson"          "sebs"
               "instruments"             "instruments"
               "eat_hesser"              "eat"
               "DELETE_sofia"            "sofia"
               "enebrink"                "barnforaldrar"
               "tvangstankar_andersson"  "tvangstankar"
               "panik2017"               "panik"
               "ergt"                    "ergt"
               "DELETE_labs"             "labs"
               "bass4_demo"              nil
               "orbit"                   "orbit"
               "cfs_andreasson"          "mecfs"
               "ibh_kaldo"               "ibh"
               "internetpsykologi"       "internetpsykologi"
               "saintcereb_nasri"        "saintcereb"
               "propsykologi_parling"    "propsykologi"
               "esteem_bjureberg"        "esteem"
               "jensen_pain"             "pain"
               "journalist"              "journalist"
               "egenvard_eliza"          "egenvard"
               "oro"                     "oro"
               "selbstmanagement_ruck"   "selbstmanagement"
               "ekut"                    "ekut"
               "trichderma_asplund"      "trichderma"
               "idrottspsykologi_umea"   "idrottpsykologi"
               "eksem"                   "eksem"
               "bip"                     "bip"
               "DELETE_ki_oli"           "oli"
               "redheart"                "redheart"
               "transact_anova"          "transact"
               "healthanxiety"           "ha"
               "ert"                     "ert"
               "promis_rickardsson"      "promis"
               "mio"                     "mio"
               "soma_axelsson"           "soma"
               "bupfunk"                 "bupocd"
               "immunpsyk_dmc"           "bupip"
               "tidattleva_ghaderi"      "tidattleva"
               "ptsd_gratz"              "ptsd"})

(defn databases
  [jdbc-config]
  (map :database (jdbc/query jdbc-config "SHOW DATABASES")))

(defn tables
  [conn]
  (->> (jdbc/query conn "SHOW TABLES")
       (map (comp first vals))
       (into #{})))

(comment
  (def updates (->> (databases jdbc-config)
                    (map (fn [db-name]
                           (jdbc/with-db-connection [conn (assoc jdbc-config :dbname db-name)]
                             (let [tables (tables conn)]
                               (when (contains? tables "updates")
                                 [db-name (jdbc/query conn "select * from updates where filename = '160818_linkproperty.php' OR filename = '180326_bass4.php' OR filename = '151226_convert_to_db3.php' or filename = '150629_add_classname_index.php';")])))))
                    (filter identity)
                    (into {})))

  (->> updates
       (map (fn [[db-name us]]
              (let [db25  (some #(when (= "150629_add_classname_index.php" (:filename %)) %) us)
                    db30  (some #(when (= "151226_convert_to_db3.php" (:filename %)) %) us)
                    db35  (some #(when (= "160818_linkproperty.php" (:filename %)) %) us)
                    db40  (some #(when (= "180326_bass4.php" (:filename %)) %) us)
                    db-id (get mappings db-name)]
                (when (and db-id)
                  [(when db40
                     (str "UPDATE pageloads_narrow SET bass_version = '4.0' WHERE DB = '" db-id "' AND time >= " (utils/to-unix (:date db40)) ";"))
                   (when db35
                     (str "UPDATE pageloads_narrow SET bass_version = '3.5' WHERE DB = '" db-id "' AND bass_version IS NULL AND time >= " (utils/to-unix (:date db35)) ";"))
                   (when db30
                     (str "UPDATE pageloads_narrow SET bass_version = '3.0' WHERE DB = '" db-id "' AND bass_version IS NULL AND time >= " (utils/to-unix (:date db30)) ";"))
                   (when db25
                     (str "UPDATE pageloads_narrow SET bass_version = '2.5' WHERE DB = '" db-id "' AND bass_version IS NULL AND time >= " (utils/to-unix (:date db25)) ";"))
                   (when (or db25 db30 db35 db40)
                     (str "UPDATE pageloads_narrow SET bass_version = '2.0' WHERE DB = '" db-id "' AND bass_version IS NULL;"))]))))
       (flatten)
       (filter identity)
       (interpose "\n")
       (apply str)))

(comment
  (let [updates (->> (databases jdbc-config)
                     (map (fn [db-name]
                            (jdbc/with-db-connection [conn (assoc jdbc-config :dbname db-name)]
                              (let [tables (tables conn)]
                                (when (contains? tables "c_participant")
                                  [db-name {:participants (jdbc/query conn
                                                                      "SELECT count(*) AS `participant_count`,
                                                                      unix_timestamp(date_add(date(from_unixtime(created)), interval 1 day)) AS morning_after
                                                                      FROM c_participant GROUP BY morning_after ORDER BY morning_after;")
                                            :answers      (jdbc/query conn
                                                                      "SELECT count(*) AS `answers_count`,
                                                                      unix_timestamp(date_add(date(from_unixtime(DateCompleted)), interval 1 day)) AS morning_after
                                                                      FROM c_instrumentanswers WHERE DateCompleted > 0 GROUP BY morning_after ORDER BY morning_after;")}])))))
                     )]))

(def test-rows
  [{:time 0 :duration 0.1}
   {:time 0 :duration 0.5}
   {:time 0 :duration 1.01}
   {:time 0 :duration 1}
   {:time 0 :duration 2.5}
   {:time 1 :duration 0.5}
   {:time 1 :duration 2}
   {:time 3 :duration 0}])

(defn xf'concurrent-reducer
  [rf]
  (let [a-time      (volatile! nil)
        a-carry     (volatile! [])
        time-counts (fn [time carry]
                      [time (count carry) (count (->> carry
                                                      (map #(- % 0.5))
                                                      (remove neg?)))])]
    (fn
      ([] [])
      ([result] (rf result (time-counts @a-time @a-carry)))
      ([result {:keys [time duration]}]
       (let [duration (+ duration 0.5)
             acc-time (or @a-time (vreset! a-time time))]
         (if (= time acc-time)
           (do
             (vswap! a-carry conj duration)
             result)
           (let [x (time-counts acc-time @a-carry)]
             (vreset! a-carry (conj (->> @a-carry
                                         (map #(- % (- time acc-time)))
                                         (remove neg?))
                                    duration))
             (vreset! a-time time)
             (rf result x))))))))

(comment
  (assert (= [[0 5 5] [1 6 5] [3 3 2]])
          (transduce xf'concurrent-reducer conj test-rows)))


(defn ^String update-str
  [[time concurrent concurrent5]]
  (assert (not (zero? concurrent)))
  (str "UPDATE pageloads_narrow SET concurrent = " concurrent ", concurrent5 = " concurrent5 " WHERE time = " time ";\n"))

(comment
  (let [filename "/Users/brjljo/Downloads/concurrent-reqs.sql"]
    (with-open [chan (.getChannel (java.io.FileOutputStream. filename true))]
      (.truncate chan 0))
    (time
      (with-open [file (clojure.java.io/writer filename)]
        (transduce xf'concurrent-reducer
                   (fn [& [_ output]]
                     (when output
                       (.write file (update-str output))))
                   (jdbc/reducible-query db/db-common ["SELECT time AS time, RenderTime AS duration FROM pageloads_narrow ORDER BY time"] {:raw true}))))))

;; CSV Writing

(defn- write-cell [^Writer writer obj sep quote quote?]
  (let [string     (str obj)
        must-quote (quote? string)]
    (when must-quote (.write writer (int quote)))
    (.write writer (if must-quote
                     (str/escape string
                                 {quote (str quote quote)})
                     string))
    (when must-quote (.write writer (int quote)))))

(defn- write-record [^Writer writer record sep quote quote?]
  (loop [record record]
    (when-first [cell record]
      (write-cell writer cell sep quote quote?)
      (when-let [more (next record)]
        (.write writer (int sep))
        (recur more)))))

(defn- write-csv*
  [^Writer writer sep quote quote? ^String newline]
  (fn [record]
    (write-record writer record sep quote quote?)
    (.write writer newline)))

(defn write-csv
  "Returns function that writes data to writer in CSV-format.
   Valid options are
     :separator (Default \\,)
     :quote (Default \\\")
     :quote? (A predicate function which determines if a string should be quoted. Defaults to quoting only when necessary.)
     :newline (:lf (default) or :cr+lf)"
  [writer & options]
  (let [opts      (apply hash-map options)
        separator (or (:separator opts) \,)
        quote     (or (:quote opts) \")
        quote?    (or (:quote? opts) #(some #{separator quote \return \newline} %))
        newline   (or (:newline opts) :lf)]
    (write-csv* writer
                separator
                quote
                quote?
                ({:lf "\n" :cr+lf "\r\n"} newline))))

(defn sql->csv
  [db query filename]
  (with-open [file (io/writer filename)]
    (let [writer    (write-csv file)
          col-names (atom nil)]
      (reduce
        (fn [_ row]
          (when (nil? @col-names)
            (println "Processing first row")
            (reset! col-names (keys row))
            (writer @col-names))
          (->> (map row @col-names)
               (writer)))
        nil
        (jdbc/reducible-query db query {:raw true :keywordize? false :fetch-size Integer/MIN_VALUE})))))

(comment
  (sql->csv db/db-common "SELECT * FROM client_dbs" "/Users/brjljo/Downloads/test.csv")
  (time
    (sql->csv db/db-common "SELECT * FROM pageloads_narrow" "/Users/brjljo/Downloads/DUMP_pageloads.csv")))


;(defn load-reducer
;  [acc row]
;  (let [time (:time row)
;        db   (:db row)
;        acc  (if (empty? acc)
;               {:acc      []
;                :time     (:time row)
;                :previous []}
;               acc)]
;    (if (= time (:time acc))
;      (assoc acc :previous (conj (:previous acc) (Math/floor (+ 0.5 (:rendertime row)))))
;      {:acc      (conj (:acc acc) [(:time acc) (count (:previous acc))])
;       :previous (conj (->> (:previous acc)
;                            (map #(- % (- time (:time acc))))
;                            (remove neg?))
;                       (Math/floor (+ 0.5 (:rendertime row))))
;       :time     time})))
;
;(comment (let [x     (reduce load-reducer {} (jdbc/reducible-query db/db-common ["SELECT time, RenderTime FROM pageloads_narrow ORDER BY time LIMIT 100"] {:raw true}))
;               final (conj (:acc x)
;                           [(:time x) (count (:previous x))])]
;           final))
;
;(defn ^String update-str
;  [acc]
;  (let [time  (:time acc)
;        count (count (:previous acc))]
;    (str "UPDATE pageloads_narrow SET concurrent = " count " WHERE time = " time ";\n")))
;
;(defn load-reducer2
;  [^Writer file]
;  (fn [acc row]
;    (let [time (:time row)
;          acc  (if (empty? acc)
;                 {:time     (:time row)
;                  :previous []}
;                 acc)]
;      (if (= time (:time acc))
;        (assoc acc :previous (conj (:previous acc) (Math/floor (+ 0.5 (:rendertime row)))))
;        (do
;          (.write file (update-str acc))
;          {:previous (conj (->> (:previous acc)
;                                (map #(- % (- time (:time acc))))
;                                (remove neg?))
;                           (Math/floor (+ 0.5 (:rendertime row))))
;           :time     time})))))
;
;(comment (let [file (clojure.java.io/writer "/Users/brjljo/Downloads/concurrent-reqs.sql" :append true)
;               x    (reduce (load-reducer2 file) {} (jdbc/reducible-query db/db-common ["SELECT time, RenderTime FROM pageloads_narrow ORDER BY time"] {:raw true}))]
;           (.write file (update-str x))
;           (.close file)))
;
;(defn load-reducer3
;  [acc row]
;  (let [time (:time row)
;        db   (:db row)
;        acc  (if (empty? acc)
;               {:acc   []
;                :time  (:time row)
;                :carry []}
;               acc)]
;    (if (= time (:time acc))
;      (assoc acc :carry (conj (:carry acc) (Math/floor (+ 0.5 (:duration row)))))
;      {:acc   (conj (:acc acc) [(:time acc) (count (:carry acc))])
;       :carry (conj (->> (:carry acc)
;                         (map #(- % (- time (:time acc))))
;                         (remove neg?))
;                    (Math/floor (+ 0.5 (:duration row))))
;       :time  time})))