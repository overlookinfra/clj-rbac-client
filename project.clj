(defn deploy-info
  [url]
  {:url url
   :username :env/clojars_jenkins_username
   :password :env/clojars_jenkins_password
   :sign-releases false})

(defproject puppetlabs/rbac-client "1.1.6-SNAPSHOT"
  :description "Tools for interacting with PE RBAC"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}

  :parent-project {:coords [puppetlabs/clj-parent "5.3.6"]
                   :inherit [:managed-dependencies]}

  :dependencies [[org.clojure/clojure]
                 [ring/ring-core]
                 [ring/ring-json]
                 [puppetlabs/ring-middleware]
                 [slingshot]
                 [puppetlabs/kitchensink]
                 [puppetlabs/http-client]
                 [puppetlabs/trapperkeeper]
                 [puppetlabs/i18n]]

  :pedantic? :abort
  :profiles {:dev {:dependencies [[puppetlabs/kitchensink :classifier "test"]
                                  [puppetlabs/trapperkeeper :classifier "test"]
                                  [puppetlabs/trapperkeeper-webserver-jetty9]
                                  [puppetlabs/trapperkeeper-webserver-jetty9 :classifier "test"]
                                  [org.bouncycastle/bcpkix-jdk15on]
                                  ; transitive dependency
                                  [org.clojure/tools.nrepl "0.2.13"]]}
             :testutils {:source-paths ^:replace  ["test"]}}

  :plugins [[lein-parent "0.3.7"]
            [puppetlabs/i18n "0.8.0"]]

  :classifiers  [["test" :testutils]]

  :test-paths ["test"]

  :deploy-repositories [["releases" ~(deploy-info "https://clojars.org/repo")]
                        ["snapshots" "https://artifactory.delivery.puppetlabs.net/artifactory/clojure-snapshots__local/"]])
