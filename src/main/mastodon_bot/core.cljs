#!/usr/bin/env lumo

(ns mastodon-bot.core
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   [mastodon-bot.infra :as infra]
   [mastodon-bot.transform :as transform]
   [mastodon-bot.mastodon-api :as masto]
   [mastodon-bot.twitter-api :as twitter]
   [mastodon-bot.tumblr-api :as tumblr]
   [cljs.core :refer [*command-line-args*]]))

(s/def ::mastodon masto/mastodon-auth?)
(s/def ::twitter twitter/twitter-auth?)
(s/def ::transform transform/transformations?)
(s/def ::tumblr map?)
(s/def ::rss map?)
(s/def ::auth (s/keys :opt-un [::mastodon ::twitter]))
(def config? 
  (s/keys :req-un [::auth ::transform]))

(defn-spec mastodon-auth ::mastodon
  [config config?]
  (get-in config [:auth :mastodon]))

(defn-spec twitter-auth ::twitter
  [config config?]
  (get-in config [:auth :twitter]))

(defn-spec transform ::transform
  [config config?]
  (:transform config))

(def config (infra/load-config))

(defn post-tumblrs [last-post-time]
  (fn [err response]
    (->> response
         infra/js->edn
         :posts
         (mapv tumblr/parse-tumblr-post)
         (map #(transform/intermediate-to-mastodon
                (mastodon-auth config)
                ;todo: fix this
                (:target (first (transform config))) %))
         (masto/post-items 
          (mastodon-auth config)
          (:target (first (transform config)))
          last-post-time))))

(defn parse-feed [last-post-time parser [title url]]
  (-> (.parseURL parser url)
      (.then #(masto/post-items
               (mastodon-auth config)
               (:target (first (transform config)))
               last-post-time
               (for [{:keys [title isoDate pubDate content link]} (-> % infra/js->edn :items)]
                 {:created-at (js/Date. (or isoDate pubDate))
                  :text (str (transform/trim-text
                              title
                              (masto/max-post-length (:target (first (transform config))))) 
                             "\n\n" (twitter/strip-utm link))})))))

(defn -main []
  (let [mastodon-auth (mastodon-auth config)]
    (masto/get-mastodon-timeline
     mastodon-auth
     (fn [timeline]
       (let [last-post-time (-> timeline first :created_at (js/Date.))]
         (let [{:keys [transform]} config]
           (doseq [transformation transform]
             (let [source-type (get-in transformation [:source :type])
                   target-type (get-in transformation [:target :type])]               
               (cond
               ;;post from Twitter
                 (and (= :twitter-source source-type)
                      (= :mastodon-target target-type))
                 (when-let [twitter-auth (twitter-auth config)]
                   (transform/tweets-to-mastodon
                    mastodon-auth
                    twitter-auth
                    transformation
                    last-post-time))
               ;;post from RSS
                 (and (= :rss-source source-type)
                      (= :mastodon-target target-type))
                 (transform/rss-to-mastodon
                  mastodon-auth
                  transformation
                  last-post-time)
               ;;post from Tumblr
                 (and (= :tumblr-source source-type)
                      (= :mastodon-target target-type))
                 (when-let [{:keys [access-keys accounts limit]} (:tumblr config)]
                   (doseq [account accounts]
                     (let [client (tumblr/tumblr-client access-keys account)]
                       (.posts client #js {:limit (or limit 5)} (post-tumblrs last-post-time)))))))))
)))))

(set! *main-cli-fn* -main)
(st/instrument 'mastodon-auth)
(st/instrument 'twitter-auth)
(st/instrument 'transform)
