(ns rchancode.clj-java-xml.builder-tests
  (:require [rchancode.clj-java-xml.builder :refer :all]
            [clojure.test :refer :all]))


(deftest output-xml
  (is (= "<?xml version=\"1.0\" ?><a id=\"a\"><b>TEXTB</b><b>TEXTB</b><c><!--comment--><![CDATA[cdata]]>Hello</c></a>"
         (build-xml-str
           (fn []
             (<?)
             (<_ "a" {"id" "a"}
                 (fn []
                   (dotimes [i 2]
                     (<_ "b" "TEXTB"))
                   (<_ "c"
                       (fn []
                         (<!-- "comment")
                         (<!CDATA "cdata")
                         ($ "Hello"))))))))))

(deftest output-xml-indented
  (is (= "<?xml version=\"1.1\" encoding=\"utf-16\"?>\n<a>\n  <b>TEXTB</b>\n  <b>TEXTB</b>\n</a>"
         (build-xml-str {:indent-str "  "}
                        (fn []
                          (<? "utf-16" "1.1")
                          (<_ "a"
                              (fn []
                                (dotimes [i 2]
                                  (<_ "b" "TEXTB"))))
                          )))))

(deftest output-valid-empty-root-element
  (is (= "<a/>"
         (build-xml-str
           (fn [] (<_ "a"))))))


(defn examples []
  (build-xml
    (clojure.java.io/writer "/tmp/testout.xml")
    {:indent-str "  "}
    (fn []
      (<?)
      (<_ "a" {}
          (fn []
            (<_ "b" {} "TEXTB")))))

  (build-xml-str
    {:indent-str "  "}
    (fn []
      (<? "utf-16" "1.1")
      (<_ "a" {}
          (fn []
            (dotimes [i 2]
              (<_ "b" {} "TEXTB")))))))

