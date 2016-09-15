(ns rchancode.clj-java-xml.dom-tests
  (:require [rchancode.clj-java-xml.dom :refer :all]
            [clojure.test :refer :all]))


(deftest parse-xml
  (is (instance? org.w3c.dom.Document (parse "./testfiles/domtest.xml"
                                               {:namespace-aware true}))))

(deftest valid-xml
  (parse "./testfiles/validation/note.xml"
           {:schema "./testfiles/validation/note.xsd"
            :coalescing true
            :ignoring-comments true}))

(defn note-schema []
  (xml-schema "./testfiles/validation/note.xsd"))

(deftest bad-schema
  (is (thrown? org.xml.sax.SAXParseException
               (xml-schema "./testfiles/validation/bad-schema.xsd"))))

(deftest override-schema-error-handler-expects-no-exception-thrown
  (xml-schema "./testfiles/validation/bad-schema.xsd"
              {:error-handler (fn [_ _])}))

(deftest use-schema-unspported-features-expects-exception
  (is (thrown? org.xml.sax.SAXNotRecognizedException
               (xml-schema "./testfiles/validation/note.xsd"
                           {:features {"unsupported" true}}))))

(deftest valid-doc-with-schema
  (parse "./testfiles/validation/note.xml"
         {:schema (note-schema)
          :coalescing true
          :ignoring-comments true}))

(deftest parse-with-most-options
  (parse "./testfiles/validation/note.xml"
         {:schema (note-schema)
          :coalescing true
          :ignoring-comments true
          :vaidating true
          :features {"http://apache.org/xml/features/validation/schema" false}
          :ignoring-element-content-whitespace true
          :XInclude-aware true}))

(deftest invalid-doc
  (is (thrown? org.xml.sax.SAXException
               (parse "./testfiles/validation/badnote.xml"
                        {:schema (note-schema)}))))

(deftest valid-xml
  (is-xml-valid? (note-schema) "./testfiles/validation/note.xml"))

(deftest invalid-xml
  (is (thrown? org.xml.sax.SAXException
               (is-xml-valid? (note-schema) "./testfiles/validation/badnote.xml"))))


(deftest selecting
  (let [all-selected (->> (parse "./testfiles/domtest.xml")
                          (select (xpath "/contacts/contact[@id='1']")))
        selected (->> all-selected first)]
    (is (= 1 (count all-selected)))
    (is (= "contact" (tag selected)))
    (is (= "1" (attr selected "id")))))


(deftest xpath-conditions
  (let [d (parse "./testfiles/domtest.xml")]
    (is (= true (xpath-true? "/contacts/contact[@id='1']/name/text() = 'Bob'" d)))
    (is (= false (xpath-true? "/contacts/contact[@id='1']/name/text() = 'Incorrect'" d)))))


(deftest select-docs-with-namespaces
  (let [all-selected (->> (parse "./testfiles/domtest.xml")
                          (select (xpath "/contacts/contact[@id='1']"
                                         {:namespaces {"a" "http://a"}})))
        selected (-> all-selected first)]
    (is (= 1 (count all-selected)))
    (is (= "contact" (tag selected)))
    (is (= "1" (attr selected "id")))))


(deftest modifying
  (let [d (parse (string-as-inputstream "<a><b>TEXTB</b></a>"))
        node-to-insert (.getDocumentElement (parse-text "<c>TEXTC</c>"))
        selected (select-first (xpath "/a/b") d)]
    (append-child! d node-to-insert)
    (insert-after! node-to-insert selected)
    (insert-before! node-to-insert selected)
    (is (= ["TEXTC" "TEXTB" "TEXTC" "TEXTC"]
           (->> (select (xpath "/a/*") d)
                (map text))))))

(deftest emit-str-indented
  (let [d (build-document
            "x" {}
            (fn []
              (<_ "a"
                  (fn []
                    (<_ "b" "TEXTB")))))]
    (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n<x>\n  <a>\n    <b>TEXTB</b>\n  </a>\n</x>\n"
           (emit-str d {:indent 2})))))


(deftest emit-str-omit-declaration
  (let [d (build-document "x" {}
                          (fn []
                            (<_ "a")))]
    (is (= "<x><a/></x>"
           (emit-str d {:output-properties
                        {javax.xml.transform.OutputKeys/OMIT_XML_DECLARATION "yes"}})))))


(deftest emit-str-omit-declaration-for-character-data
  (let [d (parse-text "<x><a>TEXTA</a></x>")]
    (is (= "TEXTA"
           (emit-str (select-first "/x/a/text()" d))))))


(deftest invalid-emit-str-option
  (is (thrown? javax.xml.transform.TransformerConfigurationException
                 (emit-str (build-document "root" {} nil) {:features {"a" true}}))))


(deftest builder-output-xml
  (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?><root><a id=\"1\"><c id=\"2\">TEXTC</c></a><!--Comment--><![CDATA[cdata]]><b/>HelloHello</root>"
         (emit-str (build-document
                    "root" {}
                    (fn []
                      (<_ "a" {"id" "1"}
                          (fn []
                           (<_ "c" {"id" "2"} "TEXTC")))
                      (<!-- "Comment")
                      (<_ "b" (<!CDATA "cdata"))
                      (dotimes [i 2]
                        ($ "Hello"))))))))

(deftest shortcuts-example
  (let [d (parse "./testfiles/contacts.xml")
        contacts (? "/contacts/contact" d)
        contact (?1 "/contacts/contact[@id='1']" d)
        names (map text (?> "name" contact))
        phones (map text (?> "phone" contact))
        bob (-> (?>1 "name" contact) text)]
    (is (= 2 (count contacts)))
    (is (= ["Bob"] names))
    (is (= ["123" "456"] phones))
    (is (= "Bob" bob))))


(deftest namespaces-select-query
  (let [namespaces (namespace-context {"x" "http://www.w3.org/TR/html4/"
                                       "y" "http://www.w3schools.com/furniture"})
        d (parse "./testfiles/namespaces/table.xml" {:namespace-aware true})
        t1-text (text (select-first (xpath "/root/x:table/x:tr/x:td" {:namespaces namespaces}) d))
        t2-text (text (select-first (xpath "/root/y:table/y:name" {:namespaces namespaces}) d))]
    (is (= "Apples" t1-text))
    (is (= "African Coffee Table" t2-text))))


(deftest show-string-value
  (let [d (parse-text "<a><b>TEXTB</b></a>")]
    (is (= "<a><b>TEXTB</b></a>" (show d)))))

(deftest modify-an-existing-node
  (let [doc (parse-text "<a><b>TEXTB</b></a>")
        b (select-first "/a/b" doc)]
    (modify-node! b
      (fn []
        (<_ "c" {}
            (fn []
              (<_ "d" {} "TEXTD")))))
    (is (= "<a><b>TEXTB<c><d>TEXTD</d></c></b></a>" (show doc)))))

(deftest select-elements-from-document
  (let [d (parse-text "<a><b>B1</b><b>B2</b><c>C1</c>A</a>")]
    (is (= "A" (text d)))
    (is (= "B1" (text (?>1 d))))
    (is (= "B2" (text (second (?> d)))))
    (is (= ["<b>B1</b>" "<b>B2</b>" "<c>C1</c>"] (map show (?> d))))
    (is (= "C1" (text (?>1 "c" d))))
    ))

(deftest element-of-throws-IllegalArgumentException
  (is (thrown? IllegalArgumentException
              (element-of 1))))

