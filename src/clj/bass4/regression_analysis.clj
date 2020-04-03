(ns bass4.regression-analysis
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [bass4.utils :as utils]
            [bass4.db.core :as db])
  (:import (java.io File Writer)
           (java.util ArrayList)))

(comment (def updates (let [config {:dbtype          "mysql"
                                    :dbname          "cfs_andreasson"
                                    :user            "reader"
                                    :password        "reader.11"
                                    :host            "localhost"
                                    :port            3305
                                    "serverTimezone" "UTC"}]
                        (->> (map :database (jdbc/query config "SHOW DATABASES"))
                             (map (fn [db-name]
                                    (jdbc/with-db-connection [conn (assoc config :dbname db-name)]
                                      (let [tables (->> (jdbc/query conn "SHOW TABLES")
                                                        (map (comp first vals))
                                                        (into #{}))]
                                        (when (contains? tables "updates")
                                          [db-name (jdbc/query conn "select * from updates where filename = '160818_linkproperty.php' OR filename = '180326_bass4.php' OR filename = '151226_convert_to_db3.php' or filename = '150629_add_classname_index.php';")])))))
                             (filter identity)
                             (into {}))))
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

(defn load-reducer
  [acc row]
  (let [time (:time row)
        db   (:db row)
        acc  (if (empty? acc)
               {:acc      []
                :time     (:time row)
                :previous []}
               acc)]
    (if (= time (:time acc))
      (assoc acc :previous (conj (:previous acc) (Math/floor (+ 0.5 (:rendertime row)))))
      {:acc      (conj (:acc acc) [(:time acc) (count (:previous acc))])
       :previous (conj (->> (:previous acc)
                            (map #(- % (- time (:time acc))))
                            (remove neg?))
                       (Math/floor (+ 0.5 (:rendertime row))))
       :time     time})))

(comment (let [x     (reduce load-reducer {} (jdbc/reducible-query db/db-common ["SELECT time, RenderTime FROM pageloads_narrow ORDER BY time LIMIT 100"] {:raw true}))
               final (conj (:acc x)
                           [(:time x) (count (:previous x))])]
           final))


(defn ^String update-str
  [acc]
  (let [time  (:time acc)
        count (count (:previous acc))]
    (str "UPDATE pageloads_narrow SET concurrent = " count " WHERE time = " time ";\n")))

(defn load-reducer2
  [^Writer file]
  (fn [acc row]
    (let [time (:time row)
          acc  (if (empty? acc)
                 {:time     (:time row)
                  :previous []}
                 acc)]
      (if (= time (:time acc))
        (assoc acc :previous (conj (:previous acc) (Math/floor (+ 0.5 (:rendertime row)))))
        (do
          (.write file (update-str acc))
          {:previous (conj (->> (:previous acc)
                                (map #(- % (- time (:time acc))))
                                (remove neg?))
                           (Math/floor (+ 0.5 (:rendertime row))))
           :time     time})))))

(comment (let [file (clojure.java.io/writer "/Users/brjljo/Downloads/metallica.txt" :append true)
               x    (reduce (load-reducer2 file) {} (jdbc/reducible-query db/db-common ["SELECT time, RenderTime FROM pageloads_narrow ORDER BY time"] {:raw true}))]
           (.write file (update-str x))
           (.close file)))

(defn duration-reducer
  [acc row]
  (let [time (:time row)
        acc  (if (empty? acc)
               {:acc   []
                :time  (:time row)
                :carry []}
               acc)]
    (if (= time (:time acc))
      (assoc acc :carry (conj (:carry acc) (:duration row)))
      {:acc   (conj (:acc acc) [(:time acc) (count (:carry acc))])
       :carry (conj (->> (:carry acc)
                         (map #(- % (- time (:time acc))))
                         (filter pos?))
                    (:duration row))
       :time  time})))

(comment (let [x (reduce duration-reducer {} [{:time 0 :duration 1}
                                              {:time 0 :duration 2} ; time 0 => 2 events
                                              {:time 1 :duration 2} ; time 1 => 2 events
                                              {:time 3 :duration 1}
                                              {:time 3 :duration 2} ; time 3 => 2 events
                                              {:time 4 :duration 2}])] ; time 4 => 2 events
           (conj (:acc x)
                 [(:time x) (count (:carry x))])))