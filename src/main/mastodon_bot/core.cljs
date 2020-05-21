#!/usr/bin/env lumo

(ns mastodon-bot.core
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   [cljs.core :refer [*command-line-args*]]
   [clojure.string :as string]
   ["rss-parser" :as rss]
   ["tumblr" :as tumblr]
   [mastodon-bot.infra :as infra]
   [mastodon-bot.transform :as transform]
   [mastodon-bot.mastodon-api :as masto]
   [mastodon-bot.twitter-api :as twitter]))

(s/def ::mastodon-config masto/mastodon-config?)
(s/def ::twitter twitter/twitter-config?)
(s/def ::tumblr map?)
(s/def ::rss map?)

(def config? (s/keys :req-un [::mastodon-config]
                     :opt-un [::twitter ::tumblr ::rss]))

(defn-spec mastodon-config ::mastodon-config
  [config config?]
  (:mastodon-config config))

(defn-spec twitter-config ::twitter
  [config config?]
  (:twitter config))

(def config (infra/load-config))

(defn parse-tweet [{created-at            :created_at
                    text                  :full_text
                    {:keys [media]}       :extended_entities
                    {:keys [screen_name]} :user :as tweet}]
  {:created-at (js/Date. created-at)
   :text (transform/trim-text 
          (str (twitter/chop-tail-media-url text media)
               (if (masto/append-screen-name? (mastodon-config config))
                 (str "\n - " screen_name) ""))
          (masto/max-post-length (mastodon-config config)))
   :media-links (keep #(when (= (:type %) "photo") (:media_url_https %)) media)})

(defmulti parse-tumblr-post :type)

(defmethod parse-tumblr-post "text" [{:keys [body date short_url]}]
  {:created-at (js/Date. date)
   :text (str (transform/trim-text
               body
               (masto/max-post-length (mastodon-config config))) 
              "\n\n" short_url)})

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

(defn parse-feed [last-post-time parser [title url]]
  (-> (.parseURL parser url)
      (.then #(masto/post-items
               (mastodon-config config)
               last-post-time
               (for [{:keys [title isoDate pubDate content link]} (-> % infra/js->edn :items)]
                 {:created-at (js/Date. (or isoDate pubDate))
                  :text (str (transform/trim-text
                              title
                              (masto/max-post-length (mastodon-config config))) 
                             "\n\n" (twitter/strip-utm link))})))))

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
       (when-let [twitter-config (:twitter config)]
         (let [{:keys [accounts]} twitter-config]
           (doseq [account accounts]
             (twitter/user-timeline
              twitter-config
              account
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
