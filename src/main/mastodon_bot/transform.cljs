(ns mastodon-bot.transform
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   [clojure.string :as string]
   [mastodon-bot.infra :as infra]
   [mastodon-bot.mastodon-api :as masto]
   [mastodon-bot.twitter-api :as twitter]
   [mastodon-bot.tumblr-api :as tumblr]))

(s/def ::created-at any?)
(s/def ::text string?)
(s/def ::untrimmed-text string?)
(s/def ::media-links string?)
(s/def ::screen_name string?)
(def input?  (s/keys :req-un [::created-at ::text ::screen_name]
                     :opt-un [::media-links ::untrimmed-text]))
(def mastodon-output?  (s/keys :req-un [::created-at ::text]
                               :opt-un [::media-links]))
(s/def ::type keyword?)
(defmulti source-type :type)
(defmethod source-type :twitter-source [_]
  (s/merge (s/keys :req-un[::type]) twitter/twitter-source?))
(s/def ::source (s/multi-spec source-type ::type))
(s/def ::target any?)
(s/def ::transformation (s/keys :req-un [::source ::target]))
(def transformations? (s/* ::transformation))

(defn trim-text [text max-post-length]
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
    
    :else text))

(defn-spec to-mastodon mastodon-output?
  [mastodon-config masto/mastodon-auth?
   input input?]
  (let [{:keys [created-at text media-links screen_name untrimmed-text]} input
        untrimmed (if (some? untrimmed-text)
                  (str " " untrimmed-text) "")
        sname (if (masto/append-screen-name? mastodon-config)
                (str "\n - " screen_name) "")
        trim-length (- (masto/max-post-length mastodon-config)
                       (count untrimmed)
                       (count sname))]
    {:created-at created-at
     :text (str (trim-text text trim-length)
                untrimmed
                sname)
     :media-links media-links}))

(defn-spec post-tweets any?
  [mastodon-config masto/mastodon-auth?
   last-post-time any?]
  (fn [error tweets response]
    (if error
      (infra/exit-with-error error)
      (->> (infra/js->edn tweets)
           (map twitter/parse-tweet)
           (map #(to-mastodon mastodon-config %))
           (masto/post-items mastodon-config last-post-time)))))

(defn-spec tweets-to-mastodon any?
  [mastodon-config masto/mastodon-auth?
   twitter-config twitter/twitter-auth?
   accounts (s/* string?)
   last-post-time any?]
  (doseq [account accounts]
    (twitter/user-timeline
     twitter-config
     account
     (post-tweets mastodon-config last-post-time))))
