(ns robotini-clojure.core
  (:require [clojure.data.json :as json])
  (:import (java.net Socket)
           (java.io DataInputStream PrintWriter ByteArrayInputStream)
           (javax.imageio ImageIO)
           (java.awt Dimension)
           (javax.swing JFrame JLabel ImageIcon)))

(def simulator-ip "127.0.0.1")
(def simulator-port 11000)
(def team-name "Fast lein")
(def graphics? true)

(defn connect
  [ip port]
  (println "Connecting to simulator" ip port)
  (let [simulator-socket (Socket. ip port)
        in (-> simulator-socket (.getInputStream) (DataInputStream.))
        out (-> simulator-socket (.getOutputStream) (PrintWriter. true))]
    (println "Connected!")
    [in out]))

(defn send-json-data
  [output-socket data]
  (let [data-as-json (str (json/write-str data) "\n")]
    (println "* send" data-as-json)
    (.println output-socket data-as-json)))

(defn get-image-bytes
  [input-socket]
  (let [image-length (.readUnsignedShort input-socket)
        image-bytes (byte-array image-length)]
    ;; (println "* reading image" image-length "bytes")
    (.read input-socket image-bytes 0 image-length)
    image-bytes))

(defn make-label
  [team-id]
  (let [frame (JFrame. team-id)
        pane (.getContentPane frame)
        label (JLabel.)]
    (doto pane
      (.setPreferredSize (Dimension. 128 80))
      (.add label))
    (doto frame
      (.setPreferredSize (Dimension. 128 80))
      (.setVisible true)
      (.pack)
      (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE))
    label))

(defn make-image
  [image-bytes]
  (let [byte-input-stream (ByteArrayInputStream. image-bytes)
        buffered-image (ImageIO/read byte-input-stream)]
    buffered-image))

(defn show-image
  [label buffered-image]
  (.setIcon label (ImageIcon. buffered-image)))

(defn get-actions
  [pixels]
  (let [threshold 160 ;; disregard darker pixels
        [b-count g-count r-count] (reduce
                                   (fn [[b-acc g-acc r-acc] [b g r]]
                                     (cond
                                       (< (+ r g b) threshold) [b-acc g-acc r-acc]
                                       (and (< r b) (< g b)) [(inc b-acc) g-acc r-acc]
                                       (and (< r g) (< b g)) [b-acc (inc g-acc) r-acc]
                                       (and (< b r) (< g r)) [b-acc g-acc (inc r-acc)]
                                       :else [b-acc g-acc r-acc]))
                                   [0 0 0]
                                   pixels)
        total (+ b-count g-count r-count)
        r-rel (if (pos? total) (/ r-count total) 0)
        g-rel (if (pos? total) (/ g-count total) 0)
        turn (+ (* r-rel -1) g-rel)]
    ;; (println b-count r-count g-count)
    [{"action" "forward" "value" 0.05}
     {"action" "turn" "value" turn}]))

(defn main
  []
  (println "Starting")
  (let [team-id (or (System/getenv "teamid")
                    (do
                      (println "No teamid env var set, defaulting to \"fast-lein\"")
                      "fast-lein"))
        [in out] (connect simulator-ip simulator-port)
        label (when graphics? (make-label team-id))]
    (send-json-data out {"teamId" team-id "name" team-name})
    (while true
      (let [buffered-image (-> in get-image-bytes make-image)
            pixels (partition 3 (-> buffered-image .getRaster .getDataBuffer .getData))
            actions (get-actions pixels)]
        (when label (show-image label buffered-image))
        (doseq [action actions]
          (send-json-data out action))))))
