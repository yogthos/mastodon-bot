(ns mastodon-bot.rss-api
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]   
   ["rss-parser" :as rss]
   [mastodon-bot.infra :as infra]
   ))

(s/def ::feed (s/cat :name string? :url string?))
(s/def ::feeds (s/coll-of ::feed))
(def rss-source?  (s/keys :req-un [::feeds]))

(s/def ::title string?)
(s/def ::content string?)
(s/def ::link string?)
(s/def ::author string?)
(s/def ::isoDate string?)
(s/def ::pubDate string?)
(s/def ::feed-item (s/keys :req-un [::title ::content ::link]
                           :opt-un [::author ::isoDate ::pubDate]))

(defn-spec rss-client any?
  []
  (rss.))

(defn-spec parse-feed any?
  [item ::feed-item]  
  (let [{:keys [title isoDate pubDate content link]} item]
      {:created-at (js/Date. (or isoDate pubDate))
       :text (str title
                  "\n\n"
                  link)}))

(defn-spec get-feed map?
  [url string?
   callback fn?]
  (print url)
  (-> (.parseURL (rss-client) url)
      (.then callback)))
