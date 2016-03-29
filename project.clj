(defproject rchancode/clj-java-xml "0.1.0-SNAPSHOT"
  :description "A clojure library providing convenient and idiomatic wrapper functions for Java API for XML Processing (JAXP)."
  :url "https://github.com/rchancode/clj-java-xml"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]]
  :profiles {:uberjar {:aot :all}}
  :global-vars {*warn-on-reflection* true}
  :java-source-paths ["java"]
  )
