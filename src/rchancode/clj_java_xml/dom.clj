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
  "options is a map that can inlcude the following keys:
  :schema-factory - javax.xml.validation.SchemaFactory instance to use.
  :error-handler - error handler function."
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
  "Parse input into a Document.

  input is either a file URI string, a File or an InputStream.

  options is a that include one or more of the following:
  :document-builder-factory - overrides DocumentBuilderFactory.
                              DocumentBuilderFactory might not be thread safe.
  :schema - javax.xml.validation.Schema object or build a Schema object from anything
            clojure.io/input-stream can convert from.

  DocumentBuilderFactory options:
  :namespace-aware - instructs the parser to be aware of namespaces. The value is true or false.
  :coalescing - the value is either true or false.
  :ignoring-comments - the value is either true or false.
  :features  - a map of values to setFeature on DocumentFactory.
  :ignoring-element-content-whitespace - either true or false.
  :validating - either true or false.
  :XInclude-aware - either true or false.
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
  "Parse a XML string into a Document.

  See parse function.
  "
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

(defn select-attr
  ([^String name ^Element element]
   (attr element name))
  ([^String namespaceURI ^String name ^Element element]
   (attr element namespaceURI name)))

(defn tag [^Element element]
  (.getNodeName element))

(defmulti content class)

(defmethod content Document [^Document doc]
  (content (.getDocumentElement doc)))

(defmethod content Element [^Element element]
  (NodeList-seq (.getChildNodes element)))

(defn ^Element element-of [x]
  (cond
    (instance? Element x) x
    (instance? Document x) (.getDocumentElement ^Document x)
    :else
    (throw (IllegalArgumentException. "x must be either an Element or a Document."))))

(defn select-elements
  ([e]
   (NodeList-seq (.getElementsByTagName (element-of e) "*")))
  ([^String tag e]
   (NodeList-seq (.getElementsByTagName (element-of e) tag)))
  ([^String namespace-uri ^String tag e]
   (NodeList-seq (.getElementsByTagNameNS (element-of e) namespace-uri tag))))

(defn select-first-element
  ([e]
   (first (select-elements e)))
  ([^String tag e]
   (first (select-elements tag e)))
  ([^String namespace-uri ^String tag e]
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

(defn insert-before!
  "Insert new-node before target node. If new-node is from a different document,
  it and its subtree will be recursively imported to the target document."
  [^Node new-node ^Node target]
  (if (= (.getOwnerDocument new-node) (.getOwnerDocument target))
    (.insertBefore (.getParentNode target) new-node target)
    (let [^Document d (.getOwnerDocument target)
          copied (.importNode d new-node true)]
      (insert-before! copied target))))

(defn insert-after!
  "Insert new-node after target node. If new-node is from a different document,
   it and its subtree will be recursively imported to the target document."
  [^Node new-node ^Node target]
  (if (= (.getOwnerDocument new-node) (.getOwnerDocument target))
    (.insertBefore (.getParentNode target) new-node (.getNextSibling target))
    (let [copied (.importNode (.getOwnerDocument target) new-node true)]
      (insert-after! copied target))))

(defn append-child!
  "Appends node x to node y. If x is from a different document,
   it and its subtree will be recursively imported to the target document."
  [^Node x ^Node y]
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
  ([^String ns-uri ^String tag attrs add-content]
   (let [^Document d (if (instance? Document current-node)
                       current-node
                       (.getOwnerDocument current-node))
         ^Element e (if (or (nil? ns-uri) (empty? ns-uri))
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

  ([^String tag attrs add-content]
   (<_ nil tag attrs add-content))

  ([^String tag add-content]
   (<_ nil tag nil add-content))

  ([^String tag]
   (<_ nil tag nil nil)))


(defn ^Document build-document
  "Builds a XML Document using builder functions."
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

(defn modify-node!
  "Binds the given 'node' to 'current-node' dynamic var, allowing the use of builder functions,
   such as '<_', in the given 'change-content' function."
  [^Node node change-content]
  (binding [^Node current-node node]
    (change-content)))

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


(defn- default-output-properties [^Node node]
  (if (== Node/TEXT_NODE (.getNodeType node))
    {OutputKeys/OMIT_XML_DECLARATION "yes"}
    {}))

(defn- emit-default-options [^Node node options]
  (let [op (default-output-properties node)]
    (assoc options :output-properties
      (merge op (get options :output-properties)))))

(defn emit!
  ([^Node node output options]
   (let [opts (emit-default-options node options)
         ^TransformerFactory tFactory (if-let [fac (:transformer-factory opts)]
                                        fac
                                        (TransformerFactory/newInstance))
         _ (set-transformer-factory-options! tFactory opts)
         ^Transformer transformer (.newTransformer tFactory)
         _ (set-transformer-options! transformer opts)
         ^DOMSource source (DOMSource. node)
         ^StreamResult result (StreamResult. (as-outputstream output))]
     (.transform transformer source result)))
  ([^Node node output]
   (emit! node output {})))

(defn emit-str
  ([^Node node]
   (emit-str node nil))
  ([^Node node options]
   (let [baos (ByteArrayOutputStream.)]
     (emit! node baos options)
     (String. (.toByteArray baos)))))

(defn show
  "Returns a string representation of a node omitting XML declaration.
  This is useful for inspection and debugging."
  [^Node node]
  (emit-str node
            {:output-properties
             {OutputKeys/OMIT_XML_DECLARATION "yes"}}))


;; Short cuts
(def ? select)
(def ?1 select-first)
(def ?> select-elements)
(def ?>1 select-first-element)
(def ?attr select-attr)
