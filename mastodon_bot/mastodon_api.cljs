(ns mastodon-bot.mastodon-api
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   [cljs.reader :as edn]
   [clojure.set :refer [rename-keys]]
   [clojure.string :as string]
   [mastodon-bot.infra :as infra]
   ["mastodon-api" :as mastodon]))

; Todo: think about how namespaced keywords & clj->js can play nicely together
(s/def :access_token string?)
(s/def :account-id string?)
(s/def :api_url string?)

(s/def ::mastodon-config (s/keys :req [:access_token :account-id :access_token]))

(defn-spec mastodon-config ::mastodon-config
  [config any?] 
  (:mastodon config))

(defn-spec mastodon-client any?
  [mastodon-config ::mastodon-config]
  (or (some-> mastodon-config clj->js mastodon.)
      (infra/exit-with-error "missing Mastodon client configuration!")))

(def content-filter-regexes (mapv re-pattern (:content-filters mastodon-config)))

(def keyword-filter-regexes (mapv re-pattern (:keyword-filters mastodon-config)))

(def append-screen-name? (boolean (:append-screen-name? mastodon-config)))

(def max-post-length (:max-post-length mastodon-config))

(defn blocked-content? [text]
 (boolean
   (or (some #(re-find % text) content-filter-regexes)
       (when (not-empty keyword-filter-regexes)
             (empty? (some #(re-find % text) keyword-filter-regexes))))))

(defn delete-status [status]
  (.delete mastodon-client (str "statuses/" status) #js {}))

(defn set-signature [text]
  (if-let [signature (:signature mastodon-config )]
    (str text "\n" signature)
    text))

(defn post-status
  ([status-text]
   (post-status status-text nil))
  ([status-text media-ids]
   (let [{:keys [sensitive? signature visibility]} mastodon-config]
     (.post mastodon-client "statuses"
          (clj->js (merge {:status (-> status-text resolve-urls set-signature)}
                          (when media-ids {:media_ids media-ids})
                          (when sensitive? {:sensitive sensitive?})
                          (when visibility {:visibility visibility})))))))

(defn post-image [image-stream description callback]
  (-> (.post mastodon-client "media" #js {:file image-stream :description description})
      (.then #(-> % .-data .-id callback))))

(defn post-status-with-images
  ([status-text urls]
   (post-status-with-images status-text urls []))
  ([status-text [url & urls] ids]
   (if url
     (-> request
         (.get url)
         (.on "response"
           (fn [image-stream]
             (post-image image-stream status-text #(post-status-with-images status-text urls (conj ids %))))))
     (post-status status-text (not-empty ids)))))

(defn get-mastodon-timeline [callback]
  (.then (.get mastodon-client (str "accounts/" (:account-id mastodon-config)"/statuses") #js {})
         #(let [response (-> % .-data js->edn)]
            (if-let [error (:error response)]
              (exit-with-error error)
              (callback response)))))

(defn perform-replacements [post]
  (assoc post :text (reduce-kv string/replace (:text post) (:replacements mastodon-config)))
  )

(defn post-items [last-post-time items]
  (doseq [{:keys [text media-links]} (->> items
                                          (remove #(blocked-content? (:text %)))
                                          (filter #(> (:created-at %) last-post-time))
                                          (map perform-replacements))]
    (if media-links
      (post-status-with-images text media-links)
      (when-not (:media-only? mastodon-config)
        (post-status text)))))
