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
   ["rss-parser" :as rss]
   ["tumblr" :as tumblr]
   ["twitter" :as twitter]))

(defn find-config []
  (or (first *command-line-args*)
      (-> js/process .-env .-MASTODON_BOT_CONFIG)
      "config.edn"))

(def config (-> (find-config) (fs/readFileSync #js {:encoding "UTF-8"}) edn/read-string))

(def content-filter-regexes (mapv re-pattern (-> config :mastodon :content-filters)))

(defn blocked-content? [text]
  (boolean (some #(re-matches % text) content-filter-regexes)))

(def max-post-length (or (-> config :mastodon :max-post-length) 300))

(def mastodon-client (or (some-> config :mastodon clj->js mastodon.)
                         (do
                           (js/console.error "missing Mastodon client configuration!")
                           (js/process.exit 1))))

(defn js->edn [data]
  (js->clj data :keywordize-keys true))

(defn trim-text [text]
  (if (> (count text) max-post-length)
    (reduce
     (fn [text word]
       (if (> (+ (count text) (count word)) (- max-post-length 3))
         (reduced (str text "..."))
         (str text " " word)))
     ""
     (clojure.string/split text #" "))
    text))

(defn delete-status [status]
  (.delete mastodon-client (str "statuses/" status) #js {}))

(defn post-status
  ([status-text]
   (post-status status-text nil))
  ([status-text media-ids]
   (.post mastodon-client "statuses"
          (clj->js (merge {:status (if-let [signature (-> config :mastodon :signature)]
                                     (str status-text "\n" signature)
                                     status-text)}
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
  (doseq [{:keys [text media-links]} (->> items
                                          (remove #(blocked-content? (:text %)))
                                          (filter #(> (:created-at %) last-post-time)))]
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
   :text (str (trim-text body) "\n\n" short_url)})

(defmethod parse-tumblr-post "photo" [{:keys [caption date photos short_url] :as post}]
  {:created-at (js/Date. date)
   :text (string/join "\n" [(string/replace caption #"<[^>]*>" "") short_url])
   :media-links (mapv #(-> % :original_size :url) photos)})

(defmethod parse-tumblr-post :default [post])

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

(defn strip-utm [news-link]
  (first
   (string/split news-link #"\?utm")))

(defn parse-feed [last-post-time parser [title url]]
  (-> (.parseURL parser url)
      (.then #(post-items
               last-post-time
               (for [{:keys [title isoDate pubDate content link]} (-> % js->edn :items)]
                 {:created-at (js/Date. (or isoDate pubDate))
                  :text (str (trim-text title) "\n\n" (strip-utm link))})))))

(get-mastodon-timeline
 (fn [timeline]
   (let [last-post-time (-> timeline first :created_at (js/Date.))]
     ;;post from Twitter
     (when-let [twitter-client (some-> config :twitter :access-keys clj->js twitter.)]
         (doseq [account (-> config :twitter :accounts)]
           (.get twitter-client
                 "statuses/user_timeline"
                 #js {:screen_name account :include_rts false}
                 (post-tweets last-post-time))))
     ;;post from Tumblr
     (when-let [tumblr-oauth (some-> config :tumblr :access-keys clj->js)]
       (when-let [tumblr-client (some-> config :tumblr :accounts first (tumblr/Blog. tumblr-oauth))]
         (.posts tumblr-client #js {:limit 5} (post-tumblrs last-post-time))))
     ;;post from RSS
     (when-let [feeds (some-> config :rss)]
       (let [parser (rss.)]
         (doseq [feed feeds]
           (parse-feed last-post-time parser feed)))))))
