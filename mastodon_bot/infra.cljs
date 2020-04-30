(ns mastodon-bot.infra
  (:require
   [cljs.reader :as edn]
   [clojure.set :refer [rename-keys]]
   [clojure.string :as string]))

(defn exit-with-error [error]
  (js/console.error error)
  (js/process.exit 1))

(defn find-config []
  (let [config (or (first *command-line-args*)
                   (-> js/process .-env .-MASTODON_BOT_CONFIG)
                   "config.edn")]
    (if (fs/existsSync config)
      config
      (exit-with-error (str "failed to read config: " config)))))

(def config (-> (find-config) (fs/readFileSync #js {:encoding "UTF-8"}) edn/read-string))

(def mastodon-config (:mastodon config))

(defn js->edn [data]
  (js->clj data :keywordize-keys true))

(defn trim-text [text]
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
