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

(defn exit-with-error [error]
  (js/console.error error)
  (js/process.exit 1))

(defn find-config []
  (or (first *command-line-args*)
      (-> js/process .-env .-MASTODON_BOT_CONFIG)
      "config.edn"))

(def config (-> (find-config) (fs/readFileSync #js {:encoding "UTF-8"}) edn/read-string))

(def mastodon-client (or (some-> config :mastodon clj->js mastodon.)
                         (exit-with-error "missing Mastodon client configuration!")))

(def content-filter-regexes (mapv re-pattern (-> config :mastodon :content-filters)))

(def append-screen-name? (boolean (-> config :mastodon :append-screen-name?)))

(def max-post-length (-> config :mastodon :max-post-length))

(defn blocked-content? [text]
 (boolean (some #(re-find % text) content-filter-regexes)))

(defn js->edn [data]
  (js->clj data :keywordize-keys true))

(defn trim-text [text]
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
     (clojure.string/split text #" "))

    :else text))

(defn delete-status [status]
  (.delete mastodon-client (str "statuses/" status) #js {}))

(defn post-status
  ([status-text]
   (post-status status-text nil))
  ([status-text media-ids]
   (let [{:keys [sensitive signature visibility]} (:mastodon config)]
     (.post mastodon-client "statuses"
          (clj->js (merge {:status (if signature (str status-text "\n" signature) status-text)}
                          (when media-ids {:media_ids media-ids})
                          (when sensitive {:sensitive sensitive})
                          (when visibility {:visibility visibility})))))))

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
   :text (trim-text (if append-screen-name? (str text "\n - " screen_name) text))
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
  (first (string/split news-link #"\?utm")))

(defn parse-feed [last-post-time parser [title url]]
  (-> (.parseURL parser url)
      (.then #(post-items
               last-post-time
               (for [{:keys [title isoDate pubDate content link]} (-> % js->edn :items)]
                 {:created-at (js/Date. (or isoDate pubDate))
                  :text (str (trim-text title) "\n\n" (strip-utm link))})))))

(defn twitter-client [access-keys]
  (try
    (twitter. (clj->js access-keys))
    (catch js/Error e
      (exit-with-error
       (str "failed to connect to Twitter: " (.-message e))))))

(defn tumblr-client [access-keys account]
  (try
    (tumblr/Blog. account (clj->js access-keys))
    (catch js/Error e
      (exit-with-error
       (str "failed to connect to Tumblr account " account ": " (.-message e))))))

(get-mastodon-timeline
 (fn [timeline]
   (let [last-post-time (-> timeline first :created_at (js/Date.))]
     ;;post from Twitter
     (when-let [twitter-config (:twitter config)]
       (let [{:keys [access-keys accounts include-replies? include-rts?]} twitter-config
             client (twitter-client access-keys)]
         (doseq [account accounts]
           (.get client
                 "statuses/user_timeline"
                 #js {:screen_name account
                      :include_rts (boolean include-replies?)
                      :exclude_replies (boolean include-rts?)}
                 (post-tweets last-post-time)))))
     ;;post from Tumblr
     (when-let [{:keys [accounts limit tumblr-oauth]} (:tumblr config)]
       (doseq [account accounts]
         (let [client (tumblr-client access-keys account)]
           (.posts client #js {:limit (or limit 5)} (post-tumblrs last-post-time)))))
     ;;post from RSS
     (when-let [feeds (some-> config :rss)]
       (let [parser (rss.)]
         (doseq [feed feeds]
           (parse-feed last-post-time parser feed)))))))
