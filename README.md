# clj-java-xml

A Clojure library designed to provide convenient and idiomatic wrapper
functions to Java APIs for XML processing (JAXP).

## Installation

With Leiningen, run:

```
lein install
```

Add the following to your `:dependencies`:

```clojure
[rchancode/clj-java-xml "0.1.0-SNAPSHOT"]
```

## Usage

### Document Object Model (DOM) Usage examples

```clojure
(use 'rchancode.clj-java-xml.dom)
```

Or in the `ns` declaration:

```clojure
(:require [rchancode.clj-java-xml.dom :refer :all])
```

```clojure

;; Parse XML string into a org.w3c.dom.Document object.
(parse-text "<a><b>TEXTB</b></a>")

;; Input to parse can be either a file URI string, a File object or an InputStream.
(parse "./testfiles/contacts.xml")

;; Set parse options.
(parse "./testfiles/validation/note.xml"
       {;; Schema can be either a file URI string, Schema object or an InputStream.
        :schema "./testfiles/validation/note.xsd"
        :coalescing true
        :ignoring-comments true
        :vaidating true
        :ignoring-element-content-whitespace true
        :XInclude-aware true
        :namespace-aware true
        ;; overrides DocumentBuilderFactory in case you want to override it yourself.
        :document-builder-factory (javax.xml.parsers.DocumentBuilderFactory/newInstance)})

;; Schema

;; Define a xml-schema
;; Schema object is thread safe.
(def note-schema (xml-schema "./testfiles/validation/note.xsd"))

;; XML Schema validation.
;; Throws SAXException if xml document does not conform to its schema.
(is-xml-valid? note-schema "./testfiles/validation/note.xml")

;; select nodes using XPath from a Node/Document.

(let [xml-document (parse "./testfiles/contacts.xml")
      root (.getDocumentElement xml-document)]
  (prn (map text (select "./contact/name" root)))
  (prn (text (select-first "./contact/name" root)))

  ;; Or select elements by tag name
  (prn (map #(text (select-first-element "email" %))
            (select-elements "contact" root))))

;; Use convenient short-hand functions ?, ?1, ?>, and ?>1 to do the same things as above
;; and saves you some typing.

(let [xml-document (parse "./testfiles/contacts.xml")
      root (.getDocumentElement xml-document)]
  (prn (map text (? "./contact/name" root)))
  (prn (text (?1 "./contact/name" root)))

  ;; Or select elements by tag name
  (prn (map #(text (?>1 "email" %))
            (?> "contact" root))))

;; Creates XPathExpression from an expression string.
;; NOTE: XPathExpression object is not thread safe! Only use it on the same thread and DON'T declare it as
;; a global var.
(let [^XPathExpression contact-names (xpath "/contacts/contact/name")
      xml-document (parse "./testfiles/contacts.xml")]
  (prn (map text (select contact-names xml-document))))

;; XPath expression with namespaces

(let [d (parse "./testfiles/namespaces/table.xml" {:namespace-aware true})
      p (xpath "/root/y:table/y:name" {:namespaces {"y" "http://www.w3schools.com/furniture"}})]
  (prn (map text (select p d))))


;; Create a XML document

;; Produces <a><b>TEXTB</b></a>
(def example-doc
  (build-document
   "x" {}
   (fn []
     (<_ "a" {}
         (fn []
           (<_ "b" {} "TEXTB"))))))

;; Outputing XML

(emit-str example-doc)

(emit! example-doc "/tmp/testout.xml")

;; With indenting
(emit! example-doc "/tmp/testout.xml" {:indent 2})

;; TransformerFactory options
(emit-str example-doc {:output-properties {"omit-xml-declaration" "yes"}})

```

### Streaming XML Builder Usage examples

```clojure
(use 'rchancode.clj-java-xml.builder)
```

Or in the `ns` declaration:

```clojure
(:require [rchancode.clj-java-xml.builder :refer :all])
```

```clojure

;; Generates an XML string with indenting
(build-xml-str
 {:indent-str "  "} ;; indent-str is optional. If set, indents the output otherwise not.
 (body
  (<? "utf-8" "1.0"  ;; If omitted, the encoding will be utf-8 and version will be 1.0
      (body
       (<_ "a" {"id" "1"}
           (body
            (dotimes [i 2]
              (<_ "b" "TEXTB"))))))))

;; Writes to a file.

(build-xml
 (clojure.java.io/writer "/tmp/testout.xml")
 {:indent-str "  "}
 (body
  (<? (body
       (<_ "a" {}
           (body
            (<_ "b" {} "TEXTB")))))))

```

### XMLEvent Streaming Usage examples


```clojure
(use 'rchancode.clj-java-xml.stream)
```

Or in the `ns` declaration:

```clojure
(:require [rchancode.clj-java-xml.stream :refer :all])
```

You may also import java classes XMLEvent and XMLPathEvent. For example:

```clojure
(:import (javax.xml.stream.events XMLEvent)
         (rchancode.xml XMLPathEvent))
```

```clojure

;; Creates XMLEvent sequence that you can process further using
;; standard clojure functions such as map, reduce, filter etc.

(with-open [input (clojure.java.io/input-stream "./testfiles/contacts.xml")]
  (->> (XMLEvent-seq input)
       (reduce (fn [tally e] (inc tally)) 0)))


;; Creates a sequence of XPathEvent.
;; XMLPathEvent wraps a XMLEvent and tracks the element path of the event.
;; Path can be retrieved by '.getPath' method of the class.
;; Example path name: "/contacts/contact", the root path is "/"

(with-open [input (clojure.java.io/input-stream "./testfiles/contacts.xml")]
  (doseq [e (->> (XMLPathEvent-seq input)
                 (map (fn [x] (.getPath x))))]
    (prn e)))

;; You can also filter events by their element paths.
;; '*' can be used to match all child elements.

(with-open [input (clojure.java.io/input-stream "./testfiles/contacts.xml")]
  (doseq [e (->> (XMLPathEvent-seq input)
                 (filter-paths ["/contacts/contact/*"])
                 (filter #(text-event? %))
                 (map get-text))]
    (prn e)))
```

## License

Copyright © 2016 rchancode

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
