(ns mastodon-bot.transform
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   [clojure.string :as string]
   [mastodon-bot.mastodon-api :as masto]))

(s/def ::created-at any?)
(s/def ::text string?)
(s/def ::media-links string?)
(s/def ::screen_name string?)
(def element?  (s/keys :req-un [::created-at ::text ::media-links ::screen_name]))

(defn trim-text [text max-post-length]
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

    :else text))

(defn-spec to-mastodon any?
  [mastodon-config masto/mastodon-config?
   input element?]
  (let [{:keys [created-at text media-links screen_name]} input]
    {:created-at created-at
     :text (trim-text
            (str text
                 (if (masto/append-screen-name? mastodon-config)
                   (str "\n - " screen_name) ""))
            (masto/max-post-length mastodon-config))
     :media-links media-links}))