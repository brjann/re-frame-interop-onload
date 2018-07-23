(defproject bass4 "0.1.0-SNAPSHOT"

  :description "FIXME: write description"
  :url "http://example.com/FIXME"

  :dependencies [[buddy "1.3.0"]
                 [compojure "1.5.2"]
                 [conman "0.6.6"]
                 [cprop "0.1.10"]
                 [org.clojure/core.async "0.4.474"]
                 [funcool/struct "1.0.0"]
                 [luminus-immutant "0.2.3"]
                 [luminus-migrations "0.3.0"]
                 [luminus-nrepl "0.1.4"]
                 [markdown-clj "1.0.2"]
                 [metosin/ring-http-response "0.9.0"]
                 [mount "0.1.11"]
                 [mysql/mysql-connector-java "6.0.5"]
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.webjars.bower/tether "1.4.0"]
                 [org.webjars/bootstrap "4.0.0-beta.2"]
                 [org.webjars/font-awesome "4.7.0"]
                 [org.webjars/jquery "3.2.1"]
                 [org.webjars/jquery-ui "1.12.1"]
                 [org.webjars.bower/jquery-color "2.1.2"]
                 [org.webjars/webjars-locator-jboss-vfs "0.1.0"]
                 [ring-middleware-format "0.7.2"]
                 [ring-webjars "0.1.1"]
                 [ring/ring-core "1.7.0-RC1"]
                 [ring/ring-defaults "0.2.3"]
                 [selmer "1.10.6"]
                 [com.taoensso/tempura "1.1.2"]
                 [clj-time "0.13.0"]
                 [prismatic/schema "1.1.3"]
                 [org.flatland/ordered "1.5.4"]
                 [org.clojure/math.numeric-tower "0.0.4"]
                 [org.clojure/data.json "0.2.6"]
                 [prone "1.1.4"]
                 [camel-snake-kebab "0.4.0"]
                 [clj-http "3.9.0"]]

  :min-lein-version "2.0.0"

  ;; .lein-env is modified by leiningen to include the map from profiles.clj
  ;; https://yobriefca.se/blog/2014/04/29/managing-environment-variables-in-clojure/
  :jvm-opts ["-server" "-Dconf=.lein-env"]
  :source-paths ["src/clj"]
  :test-paths ["test/clj"]
  :resource-paths ["resources"]
  :target-path "target/%s/"
  :main bass4.core
  :migratus {:store :database :db ~(get (System/getenv) "DATABASE_URL")}

  ;; Difference between plugins and dependencies:
  ;; https://www.quora.com/In-Clojure-whats-the-difference-between-plugins-dependencies-require-use-import-etc
  :plugins [[lein-cprop "1.0.1"]
            [migratus-lein "0.4.4"]
            [lein-immutant "2.1.0"]]

  :profiles
  {:uberjar {:omit-source true
             :aot :all
             :uberjar-name "bass4.jar"
             :source-paths ["env/prod/clj"]
             :resource-paths ["env/prod/resources"]}

   :dev           [:project/dev :profiles/dev]
   :test          [:project/dev :project/test :profiles/test]

   ;; These dependencies are only compiled into the development app
   :project/dev  {:dependencies [[ring/ring-mock "0.3.0"]
                                 [ring/ring-devel "1.5.1"]
                                 [pjstadig/humane-test-output "0.8.1"]
                                 [peridot "0.4.4"]
                                 [kerodon "0.8.0"]]
                  :plugins      [[com.jakemccrary/lein-test-refresh "0.20.0"]]
                  
                  :source-paths ["env/dev/clj"]
                  :resource-paths ["env/dev/resources"]
                  :repl-options {:init-ns user}
                  :injections [(require 'pjstadig.humane-test-output)
                               (pjstadig.humane-test-output/activate!)]}
   :project/test {:resource-paths ["env/test/resources"]}
   :profiles/dev {}
   :profiles/test {}})
