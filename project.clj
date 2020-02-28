(defproject bass4 "4.6-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [
                 ; Clojure libraries
                 [org.clojure/clojure "1.10.1"]             ; https://github.com/clojure/clojure
                 [org.clojure/core.async "0.4.490"]         ; https://github.com/clojure/core.async
                 [org.clojure/tools.cli "0.4.1"]            ; https://github.com/clojure/tools.cli
                 [org.clojure/tools.logging "0.4.1"]        ; https://github.com/clojure/tools.logging
                 [org.clojure/math.numeric-tower "0.0.4"]   ; https://github.com/clojure/math.numeric-tower
                 [org.clojure/data.json "0.2.6"]            ; https://github.com/clojure/data.json
                 [org.clojure/core.cache "0.7.2"]

                 ; Web server libraries
                 [luminus-immutant "0.2.5"]                 ; https://github.com/luminus-framework/luminus-immutant
                 [metosin/compojure-api "2.0.0-alpha30"]    ; https://github.com/metosin/compojure-api
                 [compojure "1.6.1"                         ; https://github.com/weavejester/compojure
                  :exclusions [ring/ring-codec]]
                 [ring-middleware-format "0.7.3"]           ; https://github.com/ngrunwald/ring-middleware-format
                 [ring-webjars "0.2.0"]                     ; https://github.com/weavejester/ring-webjars
                 [ring/ring-core "1.7.1"]                   ; https://github.com/ring-clojure/ring
                 [ring/ring-defaults "0.3.2"]               ; https://github.com/ring-clojure/ring-defaults
                 [prone "1.6.1"]                            ; https://github.com/magnars/prone
                 [metosin/ring-http-response "0.9.1"]       ; https://github.com/metosin/ring-http-response
                 [prismatic/schema "1.1.10"]                ; https://github.com/plumatic/schema
                 [clout "2.2.1"]                            ; https://github.com/weavejester/clout
                 [com.taoensso/nippy "2.14.0"]              ; https://github.com/ptaoussanis/nippy

                 ; Database and state management libraries
                 [conman "0.8.4"]                           ; https://github.com/luminus-framework/conman
                 [mount "0.1.16"]                           ; https://github.com/tolitius/mount
                 [tolitius/mount-up "0.1.2"]                ; https://github.com/tolitius/mount-up
                 [mysql/mysql-connector-java "8.0.15"]      ; https://mvnrepository.com/artifact/mysql/mysql-connector-java
                 [cprop "0.1.13"]                           ; https://github.com/tolitius/cprop
                 [luminus-nrepl "0.1.6"]                    ; https://github.com/luminus-framework/luminus-nrepl

                 ; HTML and language libraries
                 [selmer "1.12.6"]                          ; https://github.com/yogthos/Selmer
                 [markdown-clj "1.0.7"]                     ; https://github.com/yogthos/markdown-clj
                 [com.taoensso/tempura "1.2.1"]             ; https://github.com/ptaoussanis/tempura

                 ; Utility libraries
                 [buddy/buddy-hashers "1.3.0"]              ; https://github.com/funcool/buddy-hashers
                 [clj-http "3.9.1"]                         ; https://github.com/dakrone/clj-http
                 [org.flatland/ordered "1.5.7"]             ; https://github.com/amalloy/ordered
                 [clj-time "0.15.1"]                        ; https://github.com/clj-time/clj-time
                 [camel-snake-kebab "0.4.0"]                ; https://github.com/qerub/camel-snake-kebab
                 [clj-logging-config "1.9.12"]              ; https://github.com/malcolmsparks/clj-logging-config

                 ; Webjars
                 [org.webjars/bootstrap "4.3.1"]            ; https://github.com/twbs/bootstrap
                 [org.webjars/font-awesome "4.7.0"]         ; https://fontawesome.com/
                 ;[org.webjars/jquery "3.3.1"]               ; https://jquery.com/
                 [org.webjars/jquery-ui "1.12.1"]           ; https://jqueryui.com/
                 [org.webjars.bower/jquery-color "2.1.2"]   ; https://github.com/jquery/jquery-color
                 [org.webjars/webjars-locator-jboss-vfs "0.1.0"]]

  :min-lein-version "2.0.0"

  ;; .lein-env is modified by leiningen to include the map from profiles.clj
  ;; https://yobriefca.se/blog/2014/04/29/managing-environment-variables-in-clojure/
  :jvm-opts ["-server" "-Dconf=.lein-env"]
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main bass4.core

  :bat-test {:test-matcher #"bass4.test.*"
             :parallel?    true}
  :eftest {:multithread? false}

  ;; Difference between plugins and dependencies:
  ;; https://www.quora.com/In-Clojure-whats-the-difference-between-plugins-dependencies-require-use-import-etc
  :plugins [[lein-cprop "1.0.3"]
            [lein-immutant "2.1.0"]]

  :profiles
  {:uberjar       {:omit-source    true
                   :aot            :all
                   :uberjar-name   "bass4.jar"
                   :source-paths   ["env/prod/clj"]
                   :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   ;; These dependencies are only compiled into the development app
   :project/dev   {:dependencies   [[ring/ring-devel "1.7.1"] ; Used for reloading namespaces before web requests
                                    [peridot "0.5.1"]       ; https://github.com/xeqi/peridot
                                    [kerodon "0.9.0"]       ; https://github.com/xeqi/kerodon
                                    [philoskim/debux "0.5.5"] ; https://github.com/philoskim/debux
                                    [enlive "1.1.6"]        ; https://github.com/cgrand/enlive
                                    [org.clojure/tools.namespace "0.3.0-alpha4"]]

                   :plugins        [[com.jakemccrary/lein-test-refresh "0.20.0"]
                                    [metosin/bat-test "0.4.0"]
                                    [lein-eftest "0.5.9"]
                                    [lein-ancient "0.6.15"]]

                   :source-paths   ["env/dev/clj"]
                   :resource-paths ["env/dev/resources"]
                   :repl-options   {:init-ns user}}
   :project/test  {:resource-paths ["env/test/resources"]}
   :profiles/dev  {}
   :profiles/test {}})
