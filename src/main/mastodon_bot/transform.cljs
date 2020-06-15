(ns mastodon-bot.transform
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   [clojure.string :as string]
   [mastodon-bot.infra :as infra]
   [mastodon-bot.mastodon-api :as masto]
   [mastodon-bot.twitter-api :as twitter]
   [mastodon-bot.rss-api :as rss]
   [mastodon-bot.tumblr-api :as tumblr]
   ["deasync" :as deasync]
   ["request" :as request]))

(s/def ::created-at any?)
(s/def ::text string?)
(s/def ::untrimmed-text string?)
(s/def ::media-links string?)
(s/def ::screen_name string?)
(def input?  (s/keys :req-un [::created-at ::text ::screen_name]
                     :opt-un [::media-links ::untrimmed-text]))
(def mastodon-output?  (s/keys :req-un [::created-at ::text]
                               :opt-un [::media-links]))
(s/def ::source-type #{:twitter :rss :tumblr})
(s/def ::resolve-urls? boolean?)
(s/def ::content-filter string?)
(s/def ::content-filters (s/* ::content-filter))
(s/def ::keyword-filter string?)
(s/def ::keyword-filters (s/* ::keyword-filter))
(s/def ::replacements any?)
(defmulti source-type :source-type)
(defmethod source-type :twitter [_]
  (s/merge (s/keys :req-un[::source-type]) twitter/twitter-source?))
(defmethod source-type :rss [_]
  (s/merge (s/keys :req-un [::source-type]) rss/rss-source?))
(defmethod source-type :tumblr [_]
  (s/merge (s/keys :req-un [::source-type]) tumblr/tumblr-source?))
(s/def ::source (s/multi-spec source-type ::source-type))

(s/def ::target-type #{:mastodon})
(defmulti target-type :target-type)
(defmethod target-type :mastodon [_]
  (s/merge (s/keys :req-un [::target-type]) masto/mastodon-target?))
(s/def ::target (s/multi-spec target-type ::target-type))

(s/def ::transformation (s/keys :req-un [::source ::target]
                                :opt-un [::resolve-urls? ::content-filters ::keyword-filters ::replacements]))
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

(defn-spec intermediate-resolve-urls string?
  [resolve-urls? ::resolve-urls?
   input input?]
  (when resolve-urls?
    (update input :text #(string/replace % shortened-url-pattern resolve-url))))

(defn-spec content-filter-regexes ::content-filters
  [transformation ::transformation]
  (mapv re-pattern (:content-filters transformation)))

(defn-spec keyword-filter-regexes ::keyword-filters
  [transformation ::transformation]
  (mapv re-pattern (:keyword-filters transformation)))

(defn-spec blocked-content? boolean?
  [transformation ::transformation
   text string?]
  (boolean
   (or (some #(re-find % text) (content-filter-regexes transformation))
       (when (not-empty (keyword-filter-regexes transformation))
         (empty? (some #(re-find % text) (keyword-filter-regexes transformation)))))))

(defn-spec perform-replacements string?
  [transformation ::transformation
   input input?]
  (update input :text #(reduce-kv string/replace % (:replacements transformation))))

;TODO: remove in final code
(defn debug[item]
  (println item)
  item)
  

; TODO: move this to mastodon-api - seems to belong strongly to mastodon
(defn-spec intermediate-to-mastodon mastodon-output?
  [mastodon-auth masto/mastodon-auth?
   target masto/mastodon-target?
   input input?]
  (let [{:keys [created-at text media-links screen_name untrimmed-text]} input
        {:keys [signature append-screen-name?]} target
        untrimmed (if (some? untrimmed-text)
                    (str " " untrimmed-text) "")
        sname (if (some? append-screen-name?)
                (str "\n#" screen_name) "")
        signature_text (if (some? signature)
                         (str "\n" signature)
                         "")
        trim-length (- (masto/max-post-length target)
                       (count untrimmed)
                       (count sname)
                       (count signature_text))]
    {:created-at created-at
     :text (str (trim-text text trim-length)
                untrimmed
                sname
                signature_text)
     :reblogged true
     :media-links media-links}))

(defn-spec post-tweets-to-mastodon any?
  [mastodon-auth masto/mastodon-auth?
   transformation ::transformation
   last-post-time any?]
  (let [{:keys [source target resolve-urls?]} transformation]
    (fn [error tweets response]
      (if error
        (infra/exit-with-error error)
        (->> (infra/js->edn tweets)
             (map twitter/parse-tweet)
             (filter #(> (:created-at %) last-post-time))
             (remove #(blocked-content? transformation (:text %)))
             (map #(intermediate-resolve-urls resolve-urls? %))
             (map #(twitter/nitter-url source %))
             (map #(perform-replacements transformation %))
             (map #(intermediate-to-mastodon mastodon-auth target %))
             (masto/post-items mastodon-auth target))))))

(defn-spec tweets-to-mastodon any?
  [mastodon-auth masto/mastodon-auth?
   twitter-auth twitter/twitter-auth?
   transformation ::transformation
   last-post-time any?]
  (let [{:keys [source target resolve-urls?]} transformation]
    (doseq [account (:accounts source)]
      (twitter/user-timeline
       twitter-auth
       source
       account
       (post-tweets-to-mastodon 
        mastodon-auth
        transformation
        last-post-time)))))

(defn-spec post-tumblr-to-mastodon any?
  [mastodon-auth masto/mastodon-auth?
   transformation ::transformation
   last-post-time any?]
  (let [{:keys [source target resolve-urls?]} transformation]
    (fn [error tweets response]
      (if error
        (infra/exit-with-error error)
        (->> (infra/js->edn tweets)
             :posts
             (mapv tumblr/parse-tumblr-post)
             (filter #(> (:created-at %) last-post-time))             
             (remove #(blocked-content? transformation (:text %)))
             (map #(perform-replacements transformation %))
             (map #(intermediate-to-mastodon mastodon-auth target %))
             (masto/post-items mastodon-auth target))))))

(defn-spec tumblr-to-mastodon any?
  [mastodon-auth masto/mastodon-auth?
   tumblr-auth tumblr/tumblr-auth?
   transformation ::transformation
   last-post-time any?]
  (let [{:keys [accounts limit]} transformation]
    (doseq [account accounts]
      (let [client (tumblr/tumblr-client tumblr-auth account)]
        (.posts client 
                #js {:limit (or limit 5)}
                (post-tumblr-to-mastodon
                 mastodon-auth
                 transformation
                 last-post-time)
                )))))


(defn-spec post-rss-to-mastodon any?
  [mastodon-auth masto/mastodon-auth?
   transformation ::transformation
   last-post-time any?]
  (let [{:keys [source target resolve-urls?]} transformation]
    (fn [payload]
      (->> (infra/js->edn payload)
           (:items)
           (map rss/parse-feed)
           (filter #(> (:created-at %) last-post-time))
           (remove #(blocked-content? transformation (:text %)))
           (map #(intermediate-resolve-urls resolve-urls? %))
           (map #(perform-replacements transformation %))
           (map #(intermediate-to-mastodon mastodon-auth target %))
           (masto/post-items mastodon-auth target)))))


(defn-spec rss-to-mastodon any?
  [mastodon-auth masto/mastodon-auth?
   transformation ::transformation
   last-post-time any?]
  (let [{:keys [source target]} transformation]
    (doseq [[name url] (:feeds source)]
      (rss/get-feed
       url
       (post-rss-to-mastodon
        mastodon-auth
        transformation
        last-post-time)))))