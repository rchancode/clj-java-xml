(ns rchancode.clj-java-xml.stream-tests
  (:require [rchancode.clj-java-xml.stream :refer :all]
            [clojure.test :refer :all]))

(deftest filter-names-from-contacts
  (with-open [^java.io.InputStream input
              (clojure.java.io/input-stream "./testfiles/contacts.xml")]
    (let [result (->> input
                      (XMLPathEvent-seq)
                      (filter-paths ["/contacts/contact/name"])
                      (filter #(text-event? %))
                      (map get-text)
                      )]
      (is (= ["Bob" "John"] result)))))


(deftest parse-event-seq-with-options
  (with-open [^java.io.InputStream input
              (clojure.java.io/input-stream "./testfiles/namespaces/table.xml")]
    (let [result (as-> input y
                   (XMLPathEvent-seq y {:namespace-aware true})
                   (filter-paths ["/root/{http://www.w3schools.com/furniture}table/{http://www.w3schools.com/furniture}name"] y)
                   (filter #(text-event? %) y)
                   (map get-text y))]
      (is (= ["African Coffee Table"] result)))))


