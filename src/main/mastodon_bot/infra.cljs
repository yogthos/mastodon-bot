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
  (let [config (or config-location
                   (-> js/process .-env .-MASTODON_BOT_CONFIG)
                   "config.edn")]
    (if (fs/existsSync config)
      config
      (exit-with-error (str "failed to read config: " config)))))

(defn load-config [config-location]
  (-> config-location
      (find-config)
      (fs/readFileSync #js {:encoding "UTF-8"})
      edn/read-string))