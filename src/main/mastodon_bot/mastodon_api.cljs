(ns mastodon-bot.mastodon-api
  (:require
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [orchestra.core :refer-macros [defn-spec]]
   [clojure.string :as string]
   [mastodon-bot.infra :as infra]
   ["request" :as request]
   ["mastodon-api" :as mastodon]))

(s/def ::access_token string?)
(s/def ::api_url string?)
(s/def ::account-id string?)
(s/def ::append-screen-name? boolean?)
(s/def ::signature string?)
(s/def ::sensitive? boolean?)
(s/def ::media-only? boolean?)
(s/def ::visibility #{"direct" "private" "unlisted" "public"})
(s/def ::replacements string?)
(s/def ::max-post-length (fn [n] (and
                                 (int? n)
                                 (<= n 500)
                                 (> n 0))))


(def mastodon-auth? (s/keys :req-un [::account-id ::access_token ::api_url]))
(def mastodon-target? (s/keys :opt-un [::max-post-length 
                                       ::signature 
                                       ::visibility
                                       ::append-screen-name? 
                                       ::sensitive?
                                       ::media-only?
                                       ;::replacements
                                       ]))
(def mastodon-config? (s/merge mastodon-auth? mastodon-target?))


(defn-spec max-post-length ::max-post-length
  [target mastodon-target?]
  (:max-post-length target))

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

(defn-spec delete-status any?
  [mastodon-config mastodon-config?
   status-id string?]
  (.delete (mastodon-client mastodon-config) (str "statuses/" status-id) #js {}))

(defn post-status
  ([mastodon-auth target status-text]
   (post-status mastodon-auth target status-text nil print))
  ([mastodon-auth target status-text media-ids]
   (post-status mastodon-auth target status-text media-ids print))
  ([mastodon-auth target status-text media-ids callback]
   (let [{:keys [visibility sensitive?]} target]
     (-> (.post (mastodon-client mastodon-auth) "statuses"
                (clj->js (merge {:status (->> status-text
                                             (perform-replacements mastodon-auth))}
                                (when media-ids {:media_ids media-ids})
                                (when sensitive? {:sensitive sensitive?})
                                (when visibility {:visibility visibility}))))
         (.then #(-> % callback))))))

(defn-spec post-image any?
  [mastodon-auth mastodon-auth?
   target mastodon-target?
   image-stream any?
   description string?
   callback fn?]
  (-> (.post (mastodon-client mastodon-auth) "media" 
             #js {:file image-stream :description description})
      (.then #(-> % .-data .-id callback))))

(defn post-status-with-images
  ([mastodon-auth target status-text urls]
   (post-status-with-images mastodon-auth target status-text urls [] print))
  ([mastodon-auth target status-text urls ids]
   (post-status-with-images mastodon-auth target status-text urls ids print))
  ([mastodon-auth target status-text [url & urls] ids callback]
   (if url
     (-> request
         (.get url)
         (.on "response"
           (fn [image-stream]
             (post-image mastodon-auth target image-stream status-text 
                         #(post-status-with-images mastodon-auth 
                                                   target
                                                   status-text 
                                                   urls 
                                                   (conj ids %) 
                                                   callback)))))
     (post-status mastodon-auth target status-text (not-empty ids) callback))))

(defn-spec post-items any?
  [mastodon-auth mastodon-auth?
   target mastodon-target?
   last-post-time any?
   items any?]
  (doseq [{:keys [text media-links]} 
          (->> items
               (filter #(> (:created-at %) last-post-time)))]
    (if media-links
      (post-status-with-images mastodon-auth target text media-links)
      (when-not (:media-only? target)
        (post-status mastodon-auth target text)))))

(defn-spec get-mastodon-timeline any?
  [mastodon-auth mastodon-auth?
   callback fn?]
  (.then (.get (mastodon-client mastodon-auth)
               (str "accounts/" (:account-id mastodon-auth) "/statuses") #js {})
         #(let [response (-> % .-data infra/js->edn)]
            (if-let [error (::error response)]
              (infra/exit-with-error error)
              (callback response)))))