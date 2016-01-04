(ns rchancode.clj-java-xml.stream
  (:require [clojure.string :as str]
            [rchancode.clj-java-xml.dom :as dom])
  (:import (javax.xml.stream XMLInputFactory
                             XMLEventReader
                             XMLOutputFactory
                             XMLEventWriter)
           (javax.xml.stream.events Attribute Characters XMLEvent StartElement)
           (rchancode.xml XMLPathEvent XMLPathEventIterator)
           (javax.xml.namespace QName)
           (java.io Writer InputStream))
  (:gen-class))


(def ^{:private true} xml-input-factory-props
  {:allocator                    XMLInputFactory/ALLOCATOR
   :coalescing                   XMLInputFactory/IS_COALESCING
   :namespace-aware              XMLInputFactory/IS_NAMESPACE_AWARE
   :replacing-entity-references  XMLInputFactory/IS_REPLACING_ENTITY_REFERENCES
   :supporting-external-entities XMLInputFactory/IS_SUPPORTING_EXTERNAL_ENTITIES
   :validating                   XMLInputFactory/IS_VALIDATING
   :reporter                     XMLInputFactory/REPORTER
   :resolver                     XMLInputFactory/RESOLVER
   :support-dtd                  XMLInputFactory/SUPPORT_DTD})

(defn- new-xml-input-factory [props]
  (let [fac (XMLInputFactory/newInstance)]
    (doseq [[k v] props
            :let [prop (xml-input-factory-props k)]]
      (.setProperty fac prop v))
    fac))

(defn XMLPathEvent-seq
  "Produces a lazy sequence of XMLPathEvent containing the original
  XMLEvent and the path of the current event."
  ([^InputStream s options]
   (let [merged-props (merge {:coalescing true
                              :supporting-external-entities false}
                             options)
         ^XMLInputFactory fac (new-xml-input-factory merged-props)
         ^XMLEventReader sreader (.createXMLEventReader fac s)
         ^XMLPathEventIterator it (XMLPathEventIterator. sreader)]
     (iterator-seq it)))
  ([^InputStream s]
   (XMLPathEvent-seq s nil)))

(defn XMLEvent-seq
  "Produces a lazy sequence of XMLEvent objects."
  ([^InputStream s options]
   (let [merged-props (merge {:coalescing true
                              :supporting-external-entities false}
                             options)
         ^XMLInputFactory fac (new-xml-input-factory merged-props)
         ^XMLEventReader sreader (.createXMLEventReader fac s)]
     (iterator-seq sreader)))
  ([^InputStream s]
   (XMLEvent-seq s nil)))


(defn ^XMLEventWriter XMLEvent-writer [^Writer writer]
  (let [^XMLOutputFactory f (XMLOutputFactory/newInstance)
        ^XMLEventWriter w (.createXMLEventWriter f writer)]
    w))

(defn write-event! [^XMLEventWriter ew e]
  (if (instance? XMLPathEvent e)
    (let [^XMLPathEvent p e
          ^XMLEvent e1 (.getEvent p)]
      (.add ew e1))
    (let [^XMLEvent e1 e]
      (.add ew e1))))

;; XMLEvent helper functions.

(defn text-event? [^XMLEvent e]
  (if (.isCharacters e)
    (let [^Characters c (.asCharacters e)]
      (not (.isWhiteSpace c)))
    false))

(defn get-event-name [^XMLEvent e]
  (cond
    (.isStartElement e)
    (let [^StartElement se (.asStartElement e)
          ^QName qn (.getName se)]
      (keyword (.getLocalPart qn)))

    (.isEndElement e)
    (let [^StartElement ee (.asEndElement e)
          ^QName qn (.getName ee)]
      (keyword (.getLocalPart qn)))
    :else
    nil))

(defn get-text [^XMLEvent e]
  (if (.isCharacters e)
    (let [^Characters c (.asCharacters e)]
      (.getData c))
    false))

(defn attribute->pair [^Attribute a]
  (let [^QName qname (.getName a)
        k (keyword (str "_" (.getLocalPart qname)))
        v (.getValue a)]
    [k v]))

(defn get-event-attributes-map [^XMLEvent event]
  (if (not (.isStartElement event))
    (throw (IllegalArgumentException. (str "XMLEvent " (str event) " is not a StartElement."))))
  (let [^StartElement se (.asStartElement event)]
    (into {} (map attribute->pair (iterator-seq (.getAttributes se))))))

(defn attribute-vector [^Attribute a]
  (let [^QName qname (.getName a)
        k (.getLocalPart qname)
        v (.getValue a)
        ^String n (.getNamespaceURI qname)]
    (if (or (nil? n) (.isEmpty n))
      [k v]
      [n k v])))

(defn get-event-attributes [^XMLEvent event]
  (if (not (.isStartElement event))
    (throw (IllegalArgumentException. (str "XMLEvent " (str event) " is not a StartElement."))))
  (let [^StartElement se (.asStartElement event)]
    (map attribute-vector (iterator-seq (.getAttributes se)))))

;; Convenient functions for filtering XMLEvent/XMLPathEvent sequence.

(defn create-path [^String s namespaces]
  (loop [r namespaces
         ^String result s]
    (if (empty? r)
      result
      (do
        (let [kv (first r)
              k (first kv)
              v (second kv)
              result' (.replaceAll result (str k ":") (str "{" v "}"))]
          (recur (rest r) result'))))))


(defn path-match? [^String path ^String pattern]
  (let [match-children (.endsWith pattern "/*")
        ^String p (if match-children
                    (.substring pattern 0 (- (.length pattern) 1))
                    pattern)
        starts-with (.startsWith path p)
        is-child (> (.length path) (.length p))]
    (if match-children
      (and starts-with is-child)
      starts-with)))


(defn filter-paths [paths events]
  (filter (fn [^XMLPathEvent e]
            (let [^String path (.getPath e)]
              (some #(path-match? path %) paths)))
          events))
