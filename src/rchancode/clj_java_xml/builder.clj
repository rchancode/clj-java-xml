(ns rchancode.clj-java-xml.builder
  (:import [javax.xml.stream XMLStreamWriter XMLOutputFactory]
           [com.sun.xml.internal.txw2.output IndentingXMLStreamWriter]
           (java.io Writer StringWriter)))

(def ^:dynamic ^XMLStreamWriter xml-writer nil)

(defn build-xml [^Writer out-writer options content]
  (let [^XMLOutputFactory xof (XMLOutputFactory/newInstance)
        ^XMLStreamWriter xtw (.createXMLStreamWriter xof out-writer)
        ^XMLStreamWriter bxtw (if-let [^String indent-str (:indent-str options)]
                                (let [^IndentingXMLStreamWriter ixtw (IndentingXMLStreamWriter. xtw)]
                                  (.setIndentStep ixtw indent-str)
                                  ixtw)
                                xtw)]
     (binding [^XMLStreamWriter xml-writer bxtw]
       (content)
       (.writeEndDocument xml-writer)
       (.flush xml-writer)
       (.close xml-writer))))

(defn build-xml-str
  ([options content]
   (let [s (new StringWriter)]
     (build-xml s options content)
     (str s)))
  ([content]
   (let [s (new StringWriter)]
     (build-xml s {} content)
     (str s))))

(defn $ [^String text]
  (.writeCharacters xml-writer text))

(defn <!CDATA [^String cdata-value]
  (.writeCData xml-writer cdata-value))

(defn <!-- [^String comment-value]
  (.writeComment xml-writer comment-value))

(defn & [^String eref]
  (.writeEntityRef xml-writer eref))

(defn <_
  ([^String tag]
   (<_ tag {} nil))
  ([^String tag attrs-or-content]
   (cond
     (fn? attrs-or-content)     (<_ tag {} attrs-or-content)
     (map? attrs-or-content)    (<_ tag attrs-or-content nil)
     (string? attrs-or-content) (<_ tag {} attrs-or-content)
     :else                      (<_ tag)))
  ([^String tag attrs content]
   (if (not (nil? content))
     (.writeStartElement xml-writer tag)
     (.writeEmptyElement xml-writer tag))
   (doseq [attr attrs]
     (.writeAttribute xml-writer (first attr) (second attr)))
   (cond
     (string? content)  ($ content)
     (fn? content)      (content))
   (if (not (nil? content))
     (.writeEndElement xml-writer))
   (.flush xml-writer)))

(defn <?
  ([^String encoding ^String version]
   (.writeStartDocument xml-writer encoding version))
  ([]
   (.writeStartDocument xml-writer)))

