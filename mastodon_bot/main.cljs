#!/usr/bin/env lumo

(ns mastodon-bot.main
  (:require
   [mastodon-bot.core :as core]
   ["rss-parser" :as rss]))

(core/get-mastodon-timeline
 (fn [timeline]
   (let [last-post-time (-> timeline first :created_at (js/Date.))]
     ;;post from Twitter
     (when-let [twitter-config (:twitter core/config)]
       (let [{:keys [access-keys accounts include-replies? include-rts?]} twitter-config
             client (core/twitter-client access-keys)]
         (doseq [account accounts]
           (.get client
                 "statuses/user_timeline"
                 #js {:screen_name account
                      :tweet_mode "extended"
                      :include_rts (boolean include-rts?)
                      :exclude_replies (not (boolean include-replies?))}
                 (core/post-tweets last-post-time)))))
     ;;post from Tumblr
     (when-let [{:keys [access-keys accounts limit tumblr-oauth]} (:tumblr core/config)]
       (doseq [account accounts]
         (let [client (core/tumblr-client access-keys account)]
           (.posts client #js {:limit (or limit 5)} (core/post-tumblrs last-post-time)))))
     ;;post from RSS
     (when-let [feeds (some-> core/config :rss)]
       (let [parser (rss.)]
         (doseq [feed feeds]
           (core/parse-feed last-post-time parser feed)))))))
