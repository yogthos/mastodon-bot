(ns mastodon-bot.twitter-api
  (:require
   [clojure.set :refer [rename-keys]]
   [clojure.string :as string]
   ["deasync" :as deasync]
   ["request" :as request]
   ["twitter" :as twitter]))


(defn resolve-url [[uri]]
  (try
    (or
      (some-> ((deasync request)
               #js {:method "GET"
                    :uri (if (string/starts-with? uri "https://") uri (str "https://" uri))
                    :followRedirect false})
              (.-headers)
              (.-location)
              (string/replace "?mbid=social_twitter" ""))
      uri)
    (catch js/Error _ uri)))

(def shortened-url-pattern #"(https?://)?(?:\S+(?::\S*)?@)?(?:(?!(?:10|127)(?:\.\d{1,3}){3})(?!(?:169\.254|192\.168)(?:\.\d{1,3}){2})(?!172\.(?:1[6-9]|2\d|3[0-1])(?:\.\d{1,3}){2})(?:[1-9]\d?|1\d\d|2[01]\d|22[0-3])(?:\.(?:1?\d{1,2}|2[0-4]\d|25[0-5])){2}(?:\.(?:[1-9]\d?|1\d\d|2[0-4]\d|25[0-4]))|(?:(?:[a-z\u00a1-\uffff0-9]-*)*[a-z\u00a1-\uffff0-9]+)(?:\.(?:[a-z\u00a1-\uffff0-9]-*)*[a-z\u00a1-\uffff0-9]+)*(?:\.(?:[a-z\u00a1-\uffff]{2,}))\.?)(?::\d{2,5})?(?:[/?#]\S*)?")

(defn resolve-urls [text]
  (cond-> text
    (:resolve-urls? mastodon-config)
    (string/replace shortened-url-pattern resolve-url)
    (:nitter-urls? mastodon-config)
    (string/replace #"https://twitter.com" "https://nitter.net")))

; If the text ends in a link to the media (which is uploaded anyway),
; chop it off instead of including the link in the toot
(defn chop-tail-media-url [text media]
  (string/replace text #" (\S+)$" #(if (in (%1 1) (map :url media)) "" (%1 0))))

(defn parse-tweet [{created-at            :created_at
                    text                  :full_text
                    {:keys [media]}       :extended_entities
                    {:keys [screen_name]} :user :as tweet}]
  {:created-at (js/Date. created-at)
   :text (trim-text (str (chop-tail-media-url text media) (if append-screen-name? ("\n - " screen_name) "")))
   :media-links (keep #(when (= (:type %) "photo") (:media_url_https %)) media)})

(defn post-tweets [last-post-time]
  (fn [error tweets response]
    (if error
      (exit-with-error error)
      (->> (js->edn tweets)
           (map parse-tweet)
           (post-items last-post-time)))))

(defn twitter-client [access-keys]
  (try
    (twitter. (clj->js access-keys))
    (catch js/Error e
      (exit-with-error
       (str "failed to connect to Twitter: " (.-message e))))))
