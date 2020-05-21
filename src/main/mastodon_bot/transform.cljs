(ns mastodon-bot.transform
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   [clojure.string :as string]
   ;; TODO: not allowed dep - move needed config parts to this ns
   [mastodon-bot.mastodon-api :as masto]))

(defn trim-text [masto-config text]
  (let [max-post-length (masto/max-post-length masto-config)]
    (cond

      (nil? max-post-length)
      text

      (> (count text) max-post-length)
      (reduce
       (fn [text word]
         (if (> (+ (count text) (count word)) (- max-post-length 3))
           (reduced (str text "..."))
           (str text " " word)))
       ""
       (string/split text #" "))

      :else text)))