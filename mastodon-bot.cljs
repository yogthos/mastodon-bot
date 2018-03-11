#!/usr/bin/env lumo
(ns mastodon-bot.core
  (:require
   [cljs.core :refer [*command-line-args*]]
   [cljs.reader :as edn]
   ["fs" :as fs]
   ["https" :as https]
   ["mastodon-api" :as mastodon]
   ["twitter" :as twitter]))

(def config (-> (or (first *command-line-args*)
                    (-> js/process .-env .-MASTODON_BOT_CONFIG)
                    "config.edn")
                fs/readFileSync
                str
                edn/read-string))
(def mastodon-client (mastodon. (-> config :mastodon clj->js)))
(def twitter-client (twitter. (-> config :twitter :access-keys clj->js)))
(def twitter-accounts (-> config :twitter :accounts))

(defn js->edn [data]
  (js->clj data :keywordize-keys true))

(defn delete-status [status]
  (.delete mastodon-client (str "statuses/" status) #js {}))

(defn post-status
  ([status-text]
   (post-status status-text nil))
  ([status-text media-ids]
   (.post mastodon-client "statuses"
          (clj->js (merge {:status status-text}
                          (when media-ids {:media_ids media-ids}))))))

(defn post-image [image-stream description callback]
  (-> (.post mastodon-client "media" #js {:file image-stream :description description})
      (.then #(-> % .-data .-id callback))))

(defn post-status-with-images
  ([status-text urls]
   (post-status-with-images status-text urls []))
  ([status-text [url & urls] ids]
   (if url
     (.get https url
           (fn [image-stream]
             (post-image image-stream status-text #(post-status-with-images status-text urls (conj ids %)))))
     (post-status status-text (not-empty ids)))))

(defn get-mastodon-timeline [callback]
  (.then (.get mastodon-client "timelines/home" #js {}) #(-> % .-data js->edn callback)))

(defn parse-tweet [{created-at            :created_at
                    text                  :text
                    {:keys [media]}       :extended_entities
                    {:keys [screen_name]} :user :as tweet}]
  {:created-at (js/Date. created-at)
   :text (str text "\n - " screen_name)
   :media-links (keep #(when (= (:type %) "photo") (:media_url_https %)) media)})

(defn post-tweets [last-post-time]
  (fn [error tweets response]
    (when tweets
      (doseq [{:keys [text media-links]} (->> (js->edn tweets)
                                              (map parse-tweet)
                                              (filter #(> (:created-at %) last-post-time)))]
        (if media-links
          (post-status-with-images text media-links)
          (post-status text))))))

(get-mastodon-timeline
 (fn [timeline]
   (doseq [account twitter-accounts]
     (.get twitter-client
           "statuses/user_timeline"
           #js {:screen_name account :include_rts false}
           (-> timeline first :created_at (js/Date.) post-tweets)))))
