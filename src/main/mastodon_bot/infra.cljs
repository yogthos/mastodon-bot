(ns mastodon-bot.infra
  (:require
   [cljs.reader :as edn]
   [clojure.pprint :refer [pprint]]
   ["fs" :as fs]))

(defn debug [item]
  (pprint item)
  item)

(defn debug-first [item]
  (pprint (first item))
  item)

(defn js->edn [data]
  (js->clj data :keywordize-keys true))

(defn exit-with-error [error]
  (js/console.error error)
  (js/process.exit 1))

(defn find-config [config-location]
  (or config-location
      (-> js/process .-env .-MASTODON_BOT_CONFIG)
      "config.edn"))

(defn read-edn [config]
  (if config
    (if (fs/existsSync config)
       ;(edn/read-string (fs/readFileSync #js {:encoding "UTF-8"} config))
       (edn/read-string (fs/readFileSync config "UTF-8"))
       (exit-with-error (str "config file does not exist: " config)))
    nil))

(defn load-credentials-config []
  (read-edn (-> js/process .-env .-MASTODON_BOT_CREDENTIALS)))

(defn load-main-config [config-location]
  (-> config-location
      (find-config)
      (read-edn)))

(defn load-config [config-location]
  (merge (load-main-config config-location) (load-credentials-config)))
