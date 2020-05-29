(ns mastodon-bot.mastodon-api
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   [clojure.string :as string]
   [mastodon-bot.infra :as infra]
   ["deasync" :as deasync]
   ["request" :as request]
   ["mastodon-api" :as mastodon]))

(s/def ::access_token string?)
(s/def ::api_url string?)
(s/def ::account-id string?)
(s/def ::content-filter string?)
(s/def ::keyword-filter string?)
(s/def ::append-screen-name? boolean?)
(s/def ::signature string?)
(s/def ::sensitive? boolean?)
(s/def ::resolve-urls? boolean?)
(s/def ::nitter-urls? boolean?)
(s/def ::visibility string?)
(s/def ::replacements string?)
(s/def ::max-post-length (fn [n] (and
                                 (int? n)
                                 (<= n 500)
                                 (> n 0))))

(s/def ::content-filters (s/* ::content-filter))
(s/def ::keyword-filters (s/* ::keyword-filter))
(def mastodon-auth? (s/keys :req-un [::account-id ::access_token ::api_url]))
(def mastodon-target? (s/keys :req-un [
                                       ;::content-filters ::keyword-filters
                                       ;::max-post-length 
                                       ::signature 
                                       ;::visibility
                                       ;::append-screen-name? ::sensitive? ::resolve-urls?
                                       ;::nitter-urls? ::replacements
                                       ]))
(def mastodon-config? (s/merge mastodon-auth? mastodon-target?))


(defn-spec content-filter-regexes ::content-filters
  [mastodon-config mastodon-config?]
  (mapv re-pattern (:content-filters mastodon-config)))

(defn-spec keyword-filter-regexes ::keyword-filters
  [mastodon-config mastodon-config?]
  (mapv re-pattern (:keyword-filters mastodon-config)))

(defn-spec append-screen-name? ::append-screen-name?
  [mastodon-config mastodon-config?]
  (boolean (:append-screen-name? mastodon-config)))

(defn-spec max-post-length ::max-post-length
  [mastodon-config mastodon-config?]
  (:max-post-length mastodon-config))

(defn-spec perform-replacements string?
  [mastodon-config mastodon-config?
   text string?]
  (reduce-kv string/replace text (:replacements mastodon-config)))

(defn-spec mastodon-client any?
  [mastodon-auth mastodon-auth?]
  (or (some-> mastodon-auth 
       clj->js 
       mastodon.)
      (infra/exit-with-error "missing Mastodon auth configuration!")))

(defn-spec blocked-content? boolean?
  [mastodon-config mastodon-config?
   text string?]
 (boolean
   (or (some #(re-find % text) (content-filter-regexes mastodon-config))
       (when (not-empty (keyword-filter-regexes mastodon-config))
             (empty? (some #(re-find % text) (keyword-filter-regexes mastodon-config)))))))

(defn-spec delete-status any?
  [mastodon-config mastodon-config?
   status-id string?]
  (.delete (mastodon-client mastodon-config) (str "statuses/" status-id) #js {}))

;; TODO: move to twitter
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

;; TODO: move to twitter
(def shortened-url-pattern #"(https?://)?(?:\S+(?::\S*)?@)?(?:(?!(?:10|127)(?:\.\d{1,3}){3})(?!(?:169\.254|192\.168)(?:\.\d{1,3}){2})(?!172\.(?:1[6-9]|2\d|3[0-1])(?:\.\d{1,3}){2})(?:[1-9]\d?|1\d\d|2[01]\d|22[0-3])(?:\.(?:1?\d{1,2}|2[0-4]\d|25[0-5])){2}(?:\.(?:[1-9]\d?|1\d\d|2[0-4]\d|25[0-4]))|(?:(?:[a-z\u00a1-\uffff0-9]-*)*[a-z\u00a1-\uffff0-9]+)(?:\.(?:[a-z\u00a1-\uffff0-9]-*)*[a-z\u00a1-\uffff0-9]+)*(?:\.(?:[a-z\u00a1-\uffff]{2,}))\.?)(?::\d{2,5})?(?:[/?#]\S*)?")

;; TODO: move to twitter
(defn-spec resolve-urls string?
  [mastodon-config mastodon-config?
   text string?]
  (cond-> text
    (:resolve-urls? mastodon-config)
    (string/replace shortened-url-pattern resolve-url)
    (:nitter-urls? mastodon-config)
    (string/replace #"https://twitter.com" "https://nitter.net")))

(defn post-status
  ([mastodon-config status-text]
   (post-status mastodon-config status-text nil print))
  ([mastodon-config status-text media-ids]
   (post-status mastodon-config status-text media-ids print))
  ([mastodon-config status-text media-ids callback]
   (let [{:keys [sensitive? signature visibility]} mastodon-config]
     (-> (.post (mastodon-client mastodon-config) "statuses"
                (clj->js (merge {:status (->> status-text
                                             (resolve-urls mastodon-config)
                                             (perform-replacements mastodon-config))}
                                (when media-ids {:media_ids media-ids})
                                (when sensitive? {:sensitive sensitive?})
                                (when visibility {:visibility visibility}))))
         (.then #(-> % callback))))))

(defn-spec post-image any?
  [mastodon-config mastodon-config?
   image-stream any?
   description string?
   callback fn?]
  (-> (.post (mastodon-client mastodon-config) "media" 
             #js {:file image-stream :description description})
      (.then #(-> % .-data .-id callback))))

(defn post-status-with-images
  ([mastodon-config status-text urls]
   (post-status-with-images mastodon-config status-text urls [] print))
  ([mastodon-config status-text urls ids]
   (post-status-with-images mastodon-config status-text urls ids print))
  ([mastodon-config status-text [url & urls] ids callback]
   (if url
     (-> request
         (.get url)
         (.on "response"
           (fn [image-stream]
             (post-image mastodon-config image-stream status-text 
                         #(post-status-with-images mastodon-config status-text urls (conj ids %) callback)))))
     (post-status mastodon-config status-text (not-empty ids) callback))))

(defn-spec get-mastodon-timeline any?
  [mastodon-auth mastodon-auth?
   callback fn?]
  (.then (.get (mastodon-client mastodon-auth) 
               (str "accounts/" (:account-id mastodon-auth)"/statuses") #js {})
         #(let [response (-> % .-data infra/js->edn)]
            (if-let [error (::error response)]
              (infra/exit-with-error error)
              (callback response)))))

(defn-spec post-items any?
  [mastodon-config mastodon-config?
   last-post-time any?
   items any?]
  (doseq [{:keys [text media-links]} 
          (->> items
               (remove #(blocked-content? mastodon-config (:text %)))
               (filter #(> (:created-at %) last-post-time)))]
    (if media-links
      (post-status-with-images mastodon-config text media-links)
      (when-not (::media-only? mastodon-config)
        (post-status mastodon-config text)))))
