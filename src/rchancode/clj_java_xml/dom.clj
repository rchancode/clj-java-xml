(ns rchancode.clj-java-xml.dom
  (:import [javax.xml.parsers DocumentBuilder DocumentBuilderFactory]
           [javax.xml.xpath XPathFactory XPathExpression XPath XPathConstants]
           [javax.xml.namespace NamespaceContext]
           [org.w3c.dom Document Element Node NodeList Text]
           [javax.xml.validation SchemaFactory Schema Validator]
           [javax.xml XMLConstants]
           [javax.xml.transform.stream StreamSource]
           [org.xml.sax ErrorHandler]
           [java.io InputStream ByteArrayInputStream ByteArrayOutputStream OutputStream]
           [javax.xml.transform Transformer TransformerFactory OutputKeys]
           [javax.xml.transform.dom DOMSource]
           [javax.xml.transform.stream StreamResult]
           (clojure.lang LazySeq))
  (:require [clojure.set :as set]
            [clojure.java.io :as io]))

(defn- ^InputStream as-inputstream [input]
  (if (instance? InputStream input)
    input
    (io/input-stream input)))

(defn ^InputStream string-as-inputstream [^String content]
  (ByteArrayInputStream. (.getBytes content)))

(defn create-xml-error-handler
  "Reify ErrorHandler from an error handling function that takes
   error type, either :warning, :error or :fatal and the exception."
  [handle-error]
  (reify ErrorHandler
    (warning [_ e]
      (handle-error :warning e))
    (error [_ e]
      (handle-error :error e))
    (fatalError [_ e]
      (handle-error :fatal e))))

(def default-xml-error-handler
  (create-xml-error-handler
   (fn [error-type e]
     (if (not (= error-type :warning))
       (throw e)))))

(defn ^Schema xml-schema
  "options
  :schema-factory SchemaFactory
  :error-handler  Error handler function"
  ([schema-source options]
   (let [^InputStream schema-stream (as-inputstream schema-source)
         ^SchemaFactory schema-factory (if-let [fac (:schema-factory options)]
                                        fac
                                        (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI))]
     (when-let [handle-error (:error-handler options)]
       (.setErrorHandler schema-factory (create-xml-error-handler handle-error)))
     (when-let [features (:features options)]
       (doseq [feature features]
         (.setFeature schema-factory (first feature) (second feature))))
     (.newSchema schema-factory (StreamSource. schema-stream))))
  ([schema-source]
   (xml-schema schema-source nil)))


(defn is-xml-valid?
  "Returns true if xml document is valid, otherwise throws org.xml.sax.SAXException"
  [^Schema schema xml-input]
  (with-open [^InputStream xml-stream (as-inputstream xml-input)]
    (let [^Validator xml-validator (.newValidator schema)
          ^StreamSource xml-stream (StreamSource. xml-stream)]
      (.validate xml-validator xml-stream)
      true)))

(defn- set-DocumentBuilderFactory-options! [^DocumentBuilderFactory doc-factory options]
  (when-some [ns-aware (:namespace-aware options)]
    (.setNamespaceAware doc-factory ns-aware))
  (when-some [schema-source (:schema options)]
    (let [^Schema schema (if (instance? Schema schema-source)
                           schema-source
                           (xml-schema schema-source))]
      (.setSchema doc-factory schema)))
  (when-some [coalescing (:coalescing options)]
    (.setCoalescing doc-factory coalescing))
  (when-some [ignoring-comments (:ignoring-comments options)]
    (.setIgnoringComments doc-factory ignoring-comments))
  (when-some [features (:features options)]
    (doseq [feature features]
      (.setFeature doc-factory (first feature) (second feature))))
  (when-some [b (:ignoring-element-content-whitespace options)]
    (.setIgnoringElementContentWhitespace doc-factory b))
  (when-some [b (:validating options)]
    (.setValidating doc-factory b))
  (when-some [b (:XInclude-aware options)]
    (.setXIncludeAware doc-factory b)))


(defn ^Document parse
  "options:

  :document-builder-factory
  Overrides DocumentBuilderFactory. DocumentBuilderFactory might not be thread safe.

  :namespace-aware
  true/false

  :schema
  javax.xml.validation.Schema object or
  build a Schema object from anything the clojure.io/input-stream can convert from.

  :coalescing true/false
  :ignoring-comments true/false
  "
  ([input]
   (parse input nil))
  ([input options]
   (let [^DocumentBuilderFactory doc-factory (if-let [fac (:document-builder-factory options)]
                                              fac
                                              (DocumentBuilderFactory/newInstance))]
     (set-DocumentBuilderFactory-options! doc-factory options)
     (let [^DocumentBuilder doc-builder (.newDocumentBuilder doc-factory)]
       (.setErrorHandler doc-builder default-xml-error-handler)
       (with-open [^InputStream in-stream (as-inputstream input)]
         (.parse doc-builder in-stream))))))


(defn ^Document parse-text
  ([^String text options]
   (parse (string-as-inputstream text) options))
  ([^String text]
   (parse (string-as-inputstream text))))


(defn namespace-context [ns-map]
  (let [ns-map-inverse (set/map-invert ns-map)]
    (reify NamespaceContext
      (getNamespaceURI [_ prefix]
        (get ns-map prefix))
      (getPrefix [_ uri]
        (get ns-map-inverse uri))
      (getPrefixes [_ uri]
        (let [^LazySeq prefixes
              (map #(first %)
                   (filter #(= (second %) uri)
                           ns-map))]
          (.iterator prefixes))))))

(defn ^XPathExpression xpath
  "options:
  :xpath-factory XPathFactory object
  :namespaces  NamespaceContext object or a map
  "
  ([^String expression options]
   (let [^XPathFactory f (if-let [fac (:xpath-factory options)]
                           fac
                          (XPathFactory/newInstance))
         ^XPath xp (.newXPath f)
         _ (if-let [namespaces (:namespaces options)]
             (cond
               (instance? NamespaceContext namespaces) (.setNamespaceContext xp namespaces)
              (map? namespaces) (.setNamespaceContext xp (namespace-context namespaces))) ())
         ^XPathExpression expr (.compile xp expression)]
     expr))
  ([^String expression]
   (xpath expression nil)))


(defn- NodeList-seq-n [^NodeList node-list index]
  (if (> (.getLength node-list) index)
    (cons (.item node-list index) (lazy-seq (NodeList-seq-n node-list (inc index))))
    []))

(defn NodeList-seq
  [^NodeList node-list]
  (NodeList-seq-n node-list 0))


(defn- ^XPathExpression coerce-xpath [xpath-expression]
  (cond
   (instance? XPathExpression xpath-expression)
   xpath-expression
   (string? xpath-expression)
   (xpath xpath-expression)
   :else (throw (IllegalArgumentException.
                 "unsupported type for xpath-expression."))))


(defn select [xpath-expression e]
  (let [^XPathExpression expr (coerce-xpath xpath-expression)
        ^NodeList node-list (.evaluate expr e (XPathConstants/NODESET))]
    (NodeList-seq node-list)))


(defn select-first [xpath-expression e]
  (let [^XPathExpression expr (coerce-xpath xpath-expression)
        ^Node node (.evaluate expr e (XPathConstants/NODE))]
    node))

(defn xpath-true? [xpath-expression ^Document doc]
  (let [^XPathExpression expr (coerce-xpath xpath-expression)]
    (.evaluate expr doc (XPathConstants/BOOLEAN))))

(defn attr
  ([^Element element ^String name]
   (when-let [item (.getNamedItem (.getAttributes element) name)]
     (.getNodeValue item)))
  ([^Element element ^String namespaceURI ^String name]
   (when-let [item (.getNamedItemNS (.getAttributes element) namespaceURI name)]
     (.getNodeValue item))))

(defn tag [^Element element]
  (.getNodeName element))

(defmulti content class)

(defmethod content Document [^Document doc]
  (content (.getDocumentElement doc)))

(defmethod content Element [^Element element]
  (NodeList-seq (.getChildNodes element)))

(defn select-elements
  ([^Element e]
   (NodeList-seq (.getElementsByTagName e "*")))
  ([^String tag ^Element e]
   (NodeList-seq (.getElementsByTagName e tag)))
  ([^String namespace-uri ^String tag ^Element e]
   (NodeList-seq (.getElementsByTagNameNS e namespace-uri tag))))

(defn select-first-element
  ([^Element e]
   (first (select-elements e)))
  ([^String tag ^Element e]
   (first (select-elements tag e)))
  ([^String namespace-uri ^String tag ^Element e]
   (first (select-elements namespace-uri tag e))))

(defn text [^Node node]
  (if (not (nil? node))
    (condp == (.getNodeType node)
        Node/TEXT_NODE
        (.getTextContent node)

        Node/ELEMENT_NODE
        (clojure.string/join
         (map text (filter #(instance? Text %) (content node))))

        Node/DOCUMENT_NODE
        (let [^Document d node]
          (text (.getDocumentElement d)))
        ;; else
        nil)))

;; Import if owner documents are different
(defn insert-before! [^Node new-node ^Node target]
  (if (= (.getOwnerDocument new-node) (.getOwnerDocument target))
    (.insertBefore (.getParentNode target) new-node target)
    (let [^Document d (.getOwnerDocument target)
          copied (.importNode d new-node true)]
      (insert-before! copied target))))

(defn insert-after! [^Node new-node ^Node target]
  (if (= (.getOwnerDocument new-node) (.getOwnerDocument target))
    (.insertBefore (.getParentNode target) new-node (.getNextSibling target))
    (let [copied (.importNode (.getOwnerDocument target) new-node true)]
      (insert-after! copied target))))

(defn append-child! [^Node x ^Node y]
  (let [^Node x' (if (= (.getNodeType x) Node/DOCUMENT_NODE)
                   (let [^Document d x ]
                     (.getDocumentElement d))
                   x)]
    (if (= (.getOwnerDocument x') (.getOwnerDocument y))
      (.appendChild x' y)
      (let [^Document d (.getOwnerDocument x')
            copied (.importNode d y true)]
        (append-child! x' copied)))))

;; Builder

(def ^:dynamic ^Node current-node nil)

(defn $ [^String text-value]
  (.appendChild current-node (.createTextNode (.getOwnerDocument current-node) text-value)))

(defn <!-- [comment-value]
  (.appendChild current-node (.createComment (.getOwnerDocument current-node) comment-value)))

(defn <!CDATA [data]
  (.appendChild current-node (.createCDATASection (.getOwnerDocument current-node) data)))

(defn <_
  ([ns-uri tag attrs add-content]
   (let [^Document d (if (instance? Document current-node)
                       current-node
                       (.getOwnerDocument current-node))
         e (if (or (nil? ns-uri) (empty? ns-uri))
             (.createElement d tag)
             (.createElementNS d ns-uri tag))]
     (doseq [attr attrs]
       (cond
        (= (count attr) 2) (.setAttribute e (first attr) (second attr))
        (= (count attr) 3) (.setAttributeNS e (first attr) (second attr) (last attr))))
     (.appendChild current-node e)
     (binding [^Node current-node e]
       (cond
         (string? add-content) ($ add-content)
         (fn? add-content) (add-content)))))

  ([tag attrs add-content]
   (<_ nil tag attrs add-content)))


(defn ^Document build-document
  ([options ns-uri root-tag attrs add-content]
   (let [^DocumentBuilderFactory doc-factory (if-let [fac (:document-builder-factory options)]
                                              fac
                                              (DocumentBuilderFactory/newInstance))
         _ (set-DocumentBuilderFactory-options! doc-factory options)
         ^DocumentBuilder doc-builder (.newDocumentBuilder doc-factory)
         ^Document doc (.newDocument doc-builder)]
     (binding [current-node doc]
       (<_ ns-uri root-tag attrs add-content))
     doc))

  ([options root-tag attrs add-content]
   (build-document options nil root-tag attrs add-content))

  ([root-tag attrs add-content]
   (build-document {} nil root-tag attrs add-content)))


(defmacro body [& body]
  `(fn []
     ~@body))


;; Emit

(defn- ^OutputStream as-outputstream [output]
  (if (instance? OutputStream output)
    output
    (io/output-stream output)))

(defn- set-transformer-options! [^Transformer t options]
  ;; short cut to a commonly used option.
  (when-some [indent (:indent options)]
    (.setOutputProperty t OutputKeys/INDENT "yes")
    (.setOutputProperty t "{http://xml.apache.org/xslt}indent-amount" (str indent)))

  (when-some [output-properties (:output-properties options)]
    (doseq [output-property output-properties]
      (.setOutputProperty t (first output-property) (second output-property)))))

(defn- set-transformer-factory-options! [^TransformerFactory tf options]
  (when-some [features (:features options)]
    (doseq [feature features]
      (.setFeature tf (first feature) (second feature)))))

(defn emit!
  ([^Document doc output]
   (emit! doc output nil))
  ([^Document doc output options]
   (let [^TransformerFactory tFactory (if-let [fac (:transformer-factory options)]
                                        fac
                                        (TransformerFactory/newInstance))
         _ (set-transformer-factory-options! tFactory options)
         ^Transformer transformer (.newTransformer tFactory)
         _ (set-transformer-options! transformer options)
         ^DOMSource source (DOMSource. doc)
         ^StreamResult result (StreamResult. (as-outputstream output))]
     (.transform transformer source result))))

(defn emit-str
  ([^Document doc]
   (emit-str doc nil))

  ([^Document doc options]
   (let [baos (ByteArrayOutputStream.)]
     (emit! doc baos options)
     (String. (.toByteArray baos)))))


;; Short cuts
(def ? select)
(def ?1 select-first)
(def ?> select-elements)
(def ?>1 select-first-element)

