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
(def twitter-auth?  (s/keys :req-un [::access-keys]))

(s/def ::include-rts? boolean?)
(s/def ::include-replies? boolean?)
(s/def ::nitter-urls? boolean?)
(s/def ::account string?)
(s/def ::accounts (s/* ::account))
(def twitter-source?  (s/keys :req-un [::include-rts? ::include-replies? ::accounts]
                              :opt-un [::nitter-urls?]))

(defn-spec twitter-client any?
  [twitter-auth twitter-auth?]
  (try
    (twitter. (clj->js twitter-auth))
    (catch js/Error e
      (infra/exit-with-error
       (str "failed to connect to Twitter: " (.-message e))))))

(defn strip-utm [news-link]
  (first (string/split news-link #"\?utm")))

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
   :text (chop-tail-media-url text media)
   :screen_name screen_name
   :media-links (keep #(when (= (:type %) "photo") (:media_url_https %)) media)})

(defn-spec nitter-url map?
  [source twitter-source?
   parsed-tweet map?]
  (when (:nitter-urls? source)
    (update parsed-tweet :text #(string/replace % #"https://twitter.com" "https://nitter.net"))))

(defn-spec user-timeline any?
  [twitter-auth twitter-auth?
   source twitter-source?
   account ::account
   callback fn?]
  (let [{:keys [include-rts? include-replies?]} source]
    (.get (twitter-client twitter-auth)
          "statuses/user_timeline"
          #js {:screen_name account
               :tweet_mode "extended"
               :include_rts (boolean include-rts?)
               :exclude_replies (not (boolean include-replies?))}
          callback)))
  