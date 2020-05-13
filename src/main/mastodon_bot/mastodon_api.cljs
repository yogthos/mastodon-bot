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

; Todo: think about how namespaced keywords & clj->js can play nicely together
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
(s/def ::max-post-length (fn [n] (and
                                 (int? n)
                                 (<= n 600)
                                 (> n 0))))

(s/def ::content-filters (s/* ::content-filter))
(s/def ::keyword-filters (s/* ::keyword-filter))
(s/def ::mastodon-js-config (s/keys :req [::access_token ::api_url]))
(s/def ::mastodon-clj-config (s/keys :req [::account-id ::content-filters ::keyword-filters 
                                           ::max-post-length ::signature ::visibility 
                                           ::append-screen-name? ::sensitive? ::resolve-urls? 
                                           ::nitter-urls?]))
(def mastodon-config? (s/merge ::mastodon-js-config ::mastodon-clj-config))

(defn-spec content-filter-regexes ::content-filters
  [mastodon-config mastodon-config?]
  (mapv re-pattern (::content-filters mastodon-config)))

(defn-spec keyword-filter-regexes ::keyword-filters
  [mastodon-config mastodon-config?]
  (mapv re-pattern (::keyword-filters mastodon-config)))

(defn-spec append-screen-name? ::append-screen-name?
  [mastodon-config mastodon-config?]
  (boolean (::append-screen-name? mastodon-config)))

(defn-spec max-post-length ::max-post-length
  [mastodon-config mastodon-config?]
  (::max-post-length mastodon-config))

(defn-spec mastodon-client any?
  [mastodon-config mastodon-config?]
  (or (some-> mastodon-config clj->js mastodon.)
      (infra/exit-with-error "missing Mastodon client configuration!")))

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

(defn-spec resolve-urls string?
  [mastodon-config mastodon-config?
   text string?]
  (cond-> text
    (::resolve-urls? mastodon-config)
    (string/replace shortened-url-pattern resolve-url)
    (::nitter-urls? mastodon-config)
    (string/replace #"https://twitter.com" "https://nitter.net")))

(defn-spec set-signature string?
  [mastodon-config mastodon-config?
   text string?]
  (if-let [signature (::signature mastodon-config )]
    (str text "\n" signature)
    text))

(defn post-status
  ([mastodon-config status-text]
   (post-status mastodon-config status-text nil))
  ([mastodon-config status-text media-ids]
   (let [{:keys [sensitive? signature visibility]} mastodon-config]
     (.post (mastodon-client mastodon-config) "statuses"
          (clj->js (merge {:status (resolve-urls mastodon-config
                                                 (set-signature mastodon-config status-text))}
                          (when media-ids {:media_ids media-ids})
                          (when sensitive? {:sensitive sensitive?})
                          (when visibility {:visibility visibility})))))))

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
   (post-status-with-images mastodon-config status-text urls []))
  ([mastodon-config status-text [url & urls] ids]
   (if url
     (-> request
         (.get url)
         (.on "response"
           (fn [image-stream]
             (post-image mastodon-config image-stream status-text 
                         #(post-status-with-images status-text urls (conj ids %))))))
     (post-status mastodon-config status-text (not-empty ids)))))

(defn-spec get-mastodon-timeline any?
  [mastodon-config mastodon-config?
   callback fn?]
  (.then (.get (mastodon-client mastodon-config) 
               (str "accounts/" (::account-id mastodon-config)"/statuses") #js {})
         #(let [response (-> % .-data infra/js->edn)]
            (if-let [error (::error response)]
              (infra/exit-with-error error)
              (callback response)))))

(defn-spec perform-replacements any?
  [mastodon-config mastodon-config?
   post any?]
  (assoc post :text (reduce-kv string/replace (:text post) (::replacements mastodon-config)))
  )

(defn-spec post-items any?
  [mastodon-config mastodon-config?
   last-post-time any?
   items any?]
  (doseq [{:keys [text media-links]} 
          (->> items
               (remove #((blocked-content? mastodon-config (:text %))))
               (filter #(> (:created-at %) last-post-time))
               (map #(perform-replacements mastodon-config %)))]
    (if media-links
      (post-status-with-images mastodon-config text media-links)
      (when-not (::media-only? mastodon-config)
        (post-status mastodon-config text)))))
