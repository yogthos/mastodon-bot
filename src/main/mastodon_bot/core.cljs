#!/usr/bin/env lumo

(ns mastodon-bot.core
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   [cljs.core :refer [*command-line-args*]]
   [cljs.reader :as edn]
   [clojure.string :as string]
   ["fs" :as fs]
   ["rss-parser" :as rss]
   ["tumblr" :as tumblr]
   ["twitter" :as twitter]
   [mastodon-bot.infra :as infra]
   [mastodon-bot.mastodon-api :as masto]))

(s/def ::mastodon-config masto/mastodon-config?)
(s/def ::twitter map?)
(s/def ::tumblr map?)
(s/def ::rss map?)

(def config? (s/keys :req [::mastodon-config]
                     :opt [::twitter ::tumblr ::rss]))
(s/def ::config config?)

;this has to stay on top - only ns-keywords can be uses in spec
(defn-spec mastodon-config ::mastodon-config
  [config ::config]
  (::mastodon-config config))

(defn find-config []
  (let [config (or (first *command-line-args*)
                   (-> js/process .-env .-MASTODON_BOT_CONFIG)
                   "config.edn")]
    (if (fs/existsSync config)
      config
      (infra/exit-with-error (str "failed to read config: " config)))))

(def config (-> (find-config) (fs/readFileSync #js {:encoding "UTF-8"}) edn/read-string))

(defn trim-text [text]
  (let [max-post-length (masto/max-post-length (mastodon-config config))]
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

(defn in [needle haystack]
  (some (partial = needle) haystack))

; If the text ends in a link to the media (which is uploaded anyway),
; chop it off instead of including the link in the toot
(defn chop-tail-media-url [text media]
  (string/replace text #" (\S+)$" #(if (in (%1 1) (map :url media)) "" (%1 0))))

(defn parse-tweet [{created-at            :created_at
                    text                  :full_text
                    {:keys [media]}       :extended_entities
                    {:keys [screen_name]} :user :as tweet}]
  {:created-at (js/Date. created-at)
   :text (trim-text (str (chop-tail-media-url text media) 
                         (if (masto/append-screen-name? (mastodon-config config)) 
                           ("\n - " screen_name) "")))
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
         infra/js->edn
         :posts
         (mapv parse-tumblr-post)
         (masto/post-items 
          (mastodon-config config)
          last-post-time))))

(defn post-tweets [last-post-time]
  (fn [error tweets response]
    (if error
      (infra/exit-with-error error)
      (->> (infra/js->edn tweets)
           (map parse-tweet)
           (masto/post-items 
            (mastodon-config config)
            last-post-time)))))

(defn strip-utm [news-link]
  (first (string/split news-link #"\?utm")))

(defn parse-feed [last-post-time parser [title url]]
  (-> (.parseURL parser url)
      (.then #(masto/post-items
               (mastodon-config config)
               last-post-time
               (for [{:keys [title isoDate pubDate content link]} (-> % infra/js->edn :items)]
                 {:created-at (js/Date. (or isoDate pubDate))
                  :text (str (trim-text title) "\n\n" (strip-utm link))})))))

(defn twitter-client [access-keys]
  (try
    (twitter. (clj->js access-keys))
    (catch js/Error e
      (infra/exit-with-error
       (str "failed to connect to Twitter: " (.-message e))))))

(defn tumblr-client [access-keys account]
  (try
    (tumblr/Blog. account (clj->js access-keys))
    (catch js/Error e
      (infra/exit-with-error
       (str "failed to connect to Tumblr account " account ": " (.-message e))))))

(defn -main []
  (masto/get-mastodon-timeline
   (mastodon-config config)
   (fn [timeline]
     (let [last-post-time (-> timeline first :created_at (js/Date.))]
     ;;post from Twitter
       (when-let [twitter-config (::twitter config)]
         (let [{:keys [access-keys accounts include-replies? include-rts?]} twitter-config
               client (twitter-client access-keys)]
           (doseq [account accounts]
             (.get client
                   "statuses/user_timeline"
                   #js {:screen_name account
                        :tweet_mode "extended"
                        :include_rts (boolean include-rts?)
                        :exclude_replies (not (boolean include-replies?))}
                   (post-tweets last-post-time)))))
     ;;post from Tumblr
       (when-let [{:keys [access-keys accounts limit]} (:tumblr config)]
         (doseq [account accounts]
           (let [client (tumblr-client access-keys account)]
             (.posts client #js {:limit (or limit 5)} (post-tumblrs last-post-time)))))
     ;;post from RSS
       (when-let [feeds (some-> config :rss)]
         (let [parser (rss.)]
           (doseq [feed feeds]
             (parse-feed last-post-time parser feed))))))))

(set! *main-cli-fn* -main)
(st/instrument 'mastodon-config)
