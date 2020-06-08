(ns mastodon-bot.tumblr-api
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   [clojure.string :as string]
   [mastodon-bot.infra :as infra]
   ["tumblr" :as tumblr]
   ))

(s/def ::consumer_key string?)
(s/def ::consumer_secret string?)
(s/def ::token string?)
(s/def ::token_secret string?)
(def tumblr-auth? (s/keys :req-un [::consumer_key ::consumer_secret ::token
                                    ::token_secret]))

(s/def ::limit pos?)
(s/def ::account string?)
(s/def ::accounts (s/* ::account))
(def tumblr-source?  (s/keys :req-un [::limit ::accounts]))

(defn-spec tumblr-client any?
  [access-keys tumblr-auth?
   account string?]
  (try
    (tumblr/Blog. account (clj->js access-keys))
    (catch js/Error e
      (infra/exit-with-error
       (str "failed to connect to Tumblr account " account ": " (.-message e))))))

(defmulti parse-tumblr-post :type)

(defmethod parse-tumblr-post "text" [{:keys [body date short_url]}]
  {:created-at (js/Date. date)
   :text body
   :untrimmed-text (str "\n\n" short_url)})

(defmethod parse-tumblr-post "photo" [{:keys [caption date photos short_url] :as post}]
  {:created-at (js/Date. date)
   :text (string/join "\n" [(string/replace caption #"<[^>]*>" "") short_url])
   :media-links (mapv #(-> % :original_size :url) photos)})

(defmethod parse-tumblr-post :default [post])