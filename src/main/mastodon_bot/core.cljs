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
(s/def ::tumblr tumblr/tumblr-auth?)
(s/def ::transform transform/transformations?)
(s/def ::auth (s/keys :opt-un [::mastodon ::twitter ::tumblr]))
(def config? 
  (s/keys :req-un [::auth ::transform]))

(defn-spec mastodon-auth ::mastodon
  [config config?]
  (get-in config [:auth :mastodon]))

(defn-spec twitter-auth ::twitter
  [config config?]
  (get-in config [:auth :twitter]))

(defn-spec tumblr-auth ::tumblr
  [config config?]
  (get-in config [:auth :tumblr]))

(defn-spec transform ::transform
  [config config?]
  (:transform config))

(def config (infra/load-config))

(defn -main []
  (let [mastodon-auth (mastodon-auth config)]
    (masto/get-mastodon-timeline
     mastodon-auth
     (fn [timeline]
       (let [last-post-time (-> timeline first :created_at (js/Date.))]
         (let [{:keys [transform]} config]
           (doseq [transformation transform]
             (let [source-type (get-in transformation [:source :source-type])
                   target-type (get-in transformation [:target :target-type])]               
               (cond
               ;;post from Twitter
                 (and (= :twitter source-type)
                      (= :mastodon target-type))
                 (when-let [twitter-auth (twitter-auth config)]
                   (transform/tweets-to-mastodon
                    mastodon-auth
                    twitter-auth
                    transformation
                    last-post-time))
               ;;post from RSS
                 (and (= :rss source-type)
                      (= :mastodon target-type))
                 (transform/rss-to-mastodon
                  mastodon-auth
                  transformation
                  last-post-time)
               ;;post from Tumblr
                 (and (= :tumblr source-type)
                      (= :mastodon target-type))
                 (when-let [tumblr-auth (tumblr-auth config)]
                   (transform/tumblr-to-mastodon
                    mastodon-auth
                    tumblr-auth
                    transformation
                    last-post-time))
                 ))))
)))))

(set! *main-cli-fn* -main)
(st/instrument 'mastodon-auth)
(st/instrument 'twitter-auth)
(st/instrument 'transform)
