(ns mastodon-bot.twitter-api
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   [clojure.string :as string]
   ["twitter" :as twitter]
   [mastodon-bot.infra :as infra]
   ))

(s/def ::consumer_key string?)
(s/def ::consumer_secret string?)
(s/def ::access_token_key string?)
(s/def ::access_token_secret string?)
(s/def ::access-keys (s/keys :req-un [::consumer_key ::consumer_secret ::access_token_key 
                                      ::access_token_secret]))

(s/def ::include-rts? boolean?)
(s/def ::include-replies? boolean?)
(s/def ::account string?)
(s/def ::accounts (s/* ::account))
(def twitter-config?  (s/keys :req-un [::access-keys ::include-rts? ::include-replies?]))

(defn strip-utm [news-link]
  (first (string/split news-link #"\?utm")))

(defn-spec twitter-client any?
  [twitter-config twitter-config?]
  (let [{:keys [access-keys]} twitter-config]
    (try
      (twitter. (clj->js access-keys))
      (catch js/Error e
        (infra/exit-with-error
         (str "failed to connect to Twitter: " (.-message e)))))))

(defn-spec user-timeline any?
  [twitter-config twitter-config?
   account ::account
   callback fn?]
  (let [{:keys [include-rts? include-replies?]} twitter-config]
    (.get (twitter-client twitter-config)
          "statuses/user_timeline"
          #js {:screen_name account
               :tweet_mode "extended"
               :include_rts (boolean include-rts?)
               :exclude_replies (not (boolean include-replies?))}
          callback)))
  