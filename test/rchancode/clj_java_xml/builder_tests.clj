(ns rchancode.clj-java-xml.builder-tests
  (:require [rchancode.clj-java-xml.builder :refer :all]
            [clojure.test :refer :all]))


(deftest output-xml
  (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><a id=\"a\"><b>TEXTB</b><b>TEXTB</b><c><!--comment--><![CDATA[cdata]]>Hello</c></a>"
         (build-xml-str
          (body
           (<? (body
                (<_ "a" {"id" "a"}
                    (body
                     (dotimes [i 2]
                       (<_ "b""TEXTB"))
                     (<_ "c"
                         (body
                          (<!-- "comment")
                          (<!CDATA "cdata")
                          ($ "Hello"))))))))))))

(deftest output-xml-indented
  (is (= "<?xml version=\"1.1\" encoding=\"utf-16\"?>\n<a>\n  <b>TEXTB</b>\n  <b>TEXTB</b>\n</a>"
         (build-xml-str {:indent-str "  "}
          (body
           (<? "utf-16" "1.1"
               (body
                (<_ "a"
                    (body
                     (dotimes [i 2]
                       (<_ "b" "TEXTB")))))))))))


(defn examples []
  (build-xml
    (clojure.java.io/writer "/tmp/testout.xml")
    {:indent-str "  "}
    (body
      (<? (body
            (<_ "a" {}
                (body
                  (<_ "b" {} "TEXTB")))))))

  (build-xml-str
    {:indent-str "  "}
    (body
      (<? "utf-16" "1.1"
          (body
            (<_ "a" {}
                (body
                  (dotimes [i 2]
                    (<_ "b" {} "TEXTB")))))))))


