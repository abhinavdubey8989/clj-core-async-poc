(defproject clj-async-core-poc "0.1.0-SNAPSHOT"
  :description "POC for clojure's core.async"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [io.weft/gregor "1.0.0"]
                 [clj-statsd "0.4.2"]]
  :main ^:skip-aot clj-core-async-poc.producer
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all
                       :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
