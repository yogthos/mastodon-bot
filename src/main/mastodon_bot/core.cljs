#!/usr/bin/env lumo

(ns mastodon-bot.core
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   ["rss-parser" :as rss]
   [mastodon-bot.infra :as infra]
   [mastodon-bot.transform :as transform]
   [mastodon-bot.mastodon-api :as masto]
   [mastodon-bot.twitter-api :as twitter]
   [mastodon-bot.tumblr-api :as tumblr]
   [cljs.core :refer [*command-line-args*]]))

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

(defn post-tumblrs [last-post-time]
  (fn [err response]
    (->> response
         infra/js->edn
         :posts
         (mapv tumblr/parse-tumblr-post)
         (map #(transform/to-mastodon
                (mastodon-config config) %))
         (masto/post-items 
          (mastodon-config config)
          last-post-time))))

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

(defn -main []
  (masto/get-mastodon-timeline
   (mastodon-config config)
   (fn [timeline]
     (let [last-post-time (-> timeline first :created_at (js/Date.))]
     ;;post from Twitter
       (when-let [twitter-config (:twitter config)]
         (let [{:keys [accounts]} twitter-config]
         (transform/tweets-to-mastodon
          (mastodon-config config)
          twitter-config
          accounts
          last-post-time)))
     ;;post from Tumblr
       (when-let [{:keys [access-keys accounts limit]} (:tumblr config)]
         (doseq [account accounts]
           (let [client (tumblr/tumblr-client access-keys account)]
             (.posts client #js {:limit (or limit 5)} (post-tumblrs last-post-time)))))
     ;;post from RSS
       (when-let [feeds (some-> config :rss)]
         (let [parser (rss.)]
           (doseq [feed feeds]
             (parse-feed last-post-time parser feed))))))))

(set! *main-cli-fn* -main)
(st/instrument 'mastodon-config)
