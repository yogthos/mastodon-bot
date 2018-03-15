#!/usr/bin/env lumo
(ns mastodon-bot.core
  (:require
   [cljs.core :refer [*command-line-args*]]
   [cljs.reader :as edn]
   [clojure.set :refer [rename-keys]]
   [clojure.string :as string]
   ["fs" :as fs]
   ["http" :as http]
   ["https" :as https]
   ["mastodon-api" :as mastodon]
   ["tumblr" :as tumblr]
   ["twitter" :as twitter]))

(defn find-config []
  (or (first *command-line-args*)
      (-> js/process .-env .-MASTODON_BOT_CONFIG)
      "config.edn"))

(def config (-> (find-config) fs/readFileSync str edn/read-string))

(def mastodon-client (or (some-> config :mastodon clj->js mastodon.)
                         (do
                           (js/console.error "missing Mastodon client configuration!")
                           (js/process.exit 1))))

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
     (.get (if (string/starts-with? url "https://") https http) url
           (fn [image-stream]
             (post-image image-stream status-text #(post-status-with-images status-text urls (conj ids %)))))
     (post-status status-text (not-empty ids)))))

(defn get-mastodon-timeline [callback]
  (.then (.get mastodon-client "timelines/home" #js {}) #(-> % .-data js->edn callback)))

(defn post-items [last-post-time items]
  (doseq [{:keys [text media-links]} (filter #(> (:created-at %) last-post-time) items)]
    (if media-links
      (post-status-with-images text media-links)
      (post-status text))))

(defn parse-tweet [{created-at            :created_at
                    text                  :text
                    {:keys [media]}       :extended_entities
                    {:keys [screen_name]} :user :as tweet}]
  {:created-at (js/Date. created-at)
   :text (str text "\n - " screen_name)
   :media-links (keep #(when (= (:type %) "photo") (:media_url_https %)) media)})

(defmulti parse-tumblr-post :type)

(defmethod parse-tumblr-post "text" [{:keys [body date short_url]}]
  {:created-at (js/Date. date)
   :text (str body "\n\n" short_url)})

(defmethod parse-tumblr-post "photo" [{:keys [caption date photos short_url] :as post}]
  {:created-at (js/Date. date)
   :text (string/join "\n" [(string/replace caption #"<[^>]*>" "") short_url])
   :media-links (mapv #(-> % :original_size :url) photos)})

(defmethod parse-tumblr-post :default [post]
  (:type post))

(defn post-tumblrs [last-post-time]
  (fn [err response]
    (->> response
         js->edn
         :posts
         (mapv parse-tumblr-post)
         (post-items last-post-time))))

(defn post-tweets [last-post-time]
  (fn [error tweets response]
    (->> (js->edn tweets)
         (map parse-tweet)
         (post-items last-post-time))))

(get-mastodon-timeline
 (fn [timeline]
   (let [last-post-time (-> timeline first :created_at (js/Date.))]
     (when-let [twitter-client (some-> config :twitter :access-keys clj->js twitter.)]
         (doseq [account (-> config :twitter :accounts)]
           (.get twitter-client
                 "statuses/user_timeline"
                 #js {:screen_name account :include_rts false}
                 (post-tweets last-post-time))))
     (when-let [tumblr-oauth (some-> config :tumblr :access-keys clj->js)]
       (when-let [tumblr-client (some-> config :tumblr :accounts first (tumblr/Blog. tumblr-oauth))]
         (.posts tumblr-client #js {:limit 5} (post-tumblrs last-post-time)))))))
